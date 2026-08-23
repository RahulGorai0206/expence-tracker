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
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ButtonDefaults
import com.myapp.expensetracker.ui.screens.BrandedSplashScreen
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

    try {
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Expense Tracker")
                .setSubtitle("Your transactions and locations are protected")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    } catch (e: Exception) {
        // Thrown when the activity isn't in a state that can host the prompt
        // (it uses a fragment internally). Report it so the caller can clear
        // its in-flight flag and let the user retry.
        onFailure()
    }
}

/**
 * Shown while the app is locked. This is the branded splash with an unlock
 * action rather than a separate panel — previously a locked launch skipped the
 * splash entirely and dropped straight onto a bare lock screen.
 */
@Composable
fun LockedScreen(onUnlockClick: () -> Unit) {
    BrandedSplashScreen { palette ->
        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onUnlockClick,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                // Same lavender/violet pair as the splash icon chip.
                containerColor = palette.OnAccent,
                contentColor = palette.Accent
            ),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Icon(
                Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Unlock",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * How long the app may sit in the background before it re-locks.
 *
 * Without a grace period, anything that briefly leaves the activity — the
 * contact picker, a permission dialog, the system PIN screen behind the
 * biometric prompt — re-locked the app and demanded a fingerprint on return.
 */
const val APP_LOCK_GRACE_MS = 5_000L
