package com.myapp.expensetracker.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.myapp.expensetracker.BuildConfig
import com.myapp.expensetracker.GitHubApi
import com.myapp.expensetracker.R
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("UpdateCheckWorker", "Starting update check...")

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().build())
            .build()

        val githubApi = retrofit.create(GitHubApi::class.java)
        val sharedPrefs = applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        try {
            val latestRelease = githubApi.getLatestRelease()
            val latestTagName = latestRelease.tag_name

            // Check for new tag or different commit hash for same tag
            val currentVersion = BuildConfig.VERSION_NAME
            val currentCommitHash = BuildConfig.GIT_COMMIT_HASH

            var updateAvailable = false
            var latestSha = ""

            if (latestTagName != currentVersion) {
                updateAvailable = true
            } else {
                // Same tag, check commit hash
                try {
                    val tagRef = githubApi.getTagRef(latestTagName)
                    latestSha = tagRef.`object`.sha
                    if (latestSha != currentCommitHash && currentCommitHash != "unknown") {
                        updateAvailable = true
                    }
                } catch (e: Exception) {
                    Log.e("UpdateCheckWorker", "Error fetching tag ref: ${e.message}")
                }
            }

            if (updateAvailable) {
                Log.d("UpdateCheckWorker", "Update available: $latestTagName")
                sharedPrefs.edit().apply {
                    putBoolean("update_available", true)
                    putString("latest_version", latestTagName)
                    putString("latest_release_url", latestRelease.html_url)
                    apply()
                }
                showUpdateNotification(latestTagName, latestRelease.html_url)
            } else {
                Log.d("UpdateCheckWorker", "No update available.")
                sharedPrefs.edit().putBoolean("update_available", false).apply()
            }

            // Schedule for next day at 6 PM IST
            scheduleNextCheck(applicationContext)

            return Result.success()
        } catch (e: Exception) {
            Log.e("UpdateCheckWorker", "Error checking for updates", e)
            return Result.retry()
        }
    }

    private fun showUpdateNotification(version: String, url: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "app_updates"

        val channel = NotificationChannel(
            channelId,
            "App Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Update Available")
            .setContentText("A new version ($version) of Expense Tracker is available.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, notification)
    }

    companion object {
        fun scheduleNextCheck(context: Context) {
            val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
            val target = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (now.after(target)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = target.timeInMillis - now.timeInMillis

            val workRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "daily_update_check",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("UpdateCheckWorker", "Scheduled next check in ${delay / 1000 / 60} minutes")
        }

        fun checkNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
