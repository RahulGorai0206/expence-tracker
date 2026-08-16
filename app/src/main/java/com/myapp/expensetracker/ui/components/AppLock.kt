package com.myapp.expensetracker.ui.components

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Preference key shared by the settings toggle and the gate in MainActivity. */
const val PREF_APP_LOCK = "app_lock_enabled"

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True when the device has a biometric or a PIN/pattern/password enrolled. */
fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

fun isAppLockEnabled(context: Context): Boolean =
    context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        .getBoolean(PREF_APP_LOCK, false) && canAuthenticate(context)

fun setAppLockEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREF_APP_LOCK, enabled)
        .apply()
}

/**
 * Shows the system biometric / device-credential prompt.
 *
 * Device credential is included as a fallback so users without enrolled
 * biometrics can still lock the app behind their PIN — and so a failed
 * fingerprint doesn't strand them outside their own data.
 */
fun promptForUnlock(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onFailure: () -> Unit = {}
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }
        }
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Expense Tracker")
            .setSubtitle("Your transactions and locations are protected")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
    )
}

/** Placeholder shown instead of app content while locked. */
@Composable
fun LockedScreen(onUnlockClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(110.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                "Expense Tracker is locked",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onUnlockClick, shape = RoundedCornerShape(16.dp)) {
                Text("Unlock", fontWeight = FontWeight.Bold)
            }
        }
    }
}
