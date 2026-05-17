package com.workcount.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.workcount.app.service.ShiftForegroundService
import com.workcount.app.ui.WorkCountScreen
import com.workcount.app.ui.WorkCountTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val viewModel: WorkCountViewModel by viewModels { appContainer.viewModelFactory }

    private var privacyEnabled: Boolean = false
    private var isUnlocked by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState
                        .map { it.settings.privacyLockEnabled }
                        .distinctUntilChanged()
                        .collect { enabled ->
                            privacyEnabled = enabled
                            if (!enabled) {
                                isUnlocked = true
                            }
                        }
                }
                launch {
                    viewModel.uiState
                        .map { it.activeShift?.id }
                        .distinctUntilChanged()
                        .collect { activeId ->
                            if (activeId == null) {
                                ShiftForegroundService.stop(this@MainActivity)
                            } else {
                                ShiftForegroundService.sync(this@MainActivity)
                            }
                        }
                }
            }
        }

        setContent {
            WorkCountTheme {
                WorkCountScreen(
                    viewModel = viewModel,
                    isUnlocked = isUnlocked,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (privacyEnabled && !isUnlocked) {
            requestUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        if (privacyEnabled) {
            isUnlocked = false
        }
    }

    private fun requestUnlock() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val capability = biometricManager.canAuthenticate(authenticators)
        if (capability != BiometricManager.BIOMETRIC_SUCCESS) {
            isUnlocked = true
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isUnlocked = true
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock WorkCount")
                .setSubtitle("Your work time data is protected")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }
}
