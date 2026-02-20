package com.samsung.hybridlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.samsung.hybridlauncher.nowbar.NowBarContainer
import com.samsung.hybridlauncher.nowbar.NowBarNotificationListener
import com.samsung.hybridlauncher.utils.MemoryWatcher
import com.samsung.hybridlauncher.utils.SystemUiOverrides
import com.samsung.hybridlauncher.wellbeing.BiometricVaultManager
import com.samsung.hybridlauncher.wellbeing.LaunchFrictionOverlay
import com.samsung.hybridlauncher.workspace.FreeformWorkspaceLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The core Launcher Activity.
 * Orchestrates the View/Compose hybrid architecture, enforces memory limits,
 * and patches One UI 4 system bugs upon initialization.
 */
@AndroidEntryPoint
class HybridLauncher : FragmentActivity() {

    @Inject
    lateinit var memoryWatcher: MemoryWatcher

    @Inject
    lateinit var systemUiOverrides: SystemUiOverrides

    @Inject
    lateinit var biometricVaultManager: BiometricVaultManager

    // The high-performance, legacy View micro-grid for 60fps drag-and-drop
    private lateinit var workspaceLayout: FreeformWorkspaceLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Immediately apply the Android 12 gesture navigation freeze workaround
        systemUiOverrides.applyGestureFreezeWorkaround(this)

        // 2. Start aggressive PSS memory monitoring
        memoryWatcher.startWatching()

        // 3. Setup the Hybrid View Structure
        setupHybridArchitecture()

        // 4. Request Notification Access for the Now Bar (if not already granted)
        checkNotificationListenerPermissions()
    }

    private fun setupHybridArchitecture() {
        // Base container holding both View and Compose layers
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Layer 1: The Legacy View Workspace (Bottom)
        workspaceLayout = FreeformWorkspaceLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootContainer.addView(workspaceLayout)

        // Layer 2: Jetpack Compose Overlays (Top)
        // We inject a ComposeView on top of the workspace to handle the Now Bar and Friction overlays
        val composeOverlay = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                Box(modifier = Modifier.fillMaxSize()) {

                    // The bottom-anchored morphing Live Activity pill
                    NowBarContainer(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    // The full-screen distraction delay overlay (hidden by default)
                    LaunchFrictionOverlay(
                        isVisible = false, // Tied to a StateFlow in a real ViewModel
                        appName = "Instagram",
                        onProceed = { /* Execute Splash Screen Handoff Intent */ },
                        onCancel = { /* Abort launch */ }
                    )
                }
            }
        }
        rootContainer.addView(composeOverlay)

        setContentView(rootContainer)
    }

    private fun checkNotificationListenerPermissions() {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (listeners == null || !listeners.contains(packageName)) {
            // Prompt the user to grant access so the M3 Now Bar can track Media/Timers
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure the Hidden Vault instantly locks when returning to the launcher
        biometricVaultManager.lockVault()
    }

    override fun onDestroy() {
        super.onDestroy()
        memoryWatcher.stopWatching()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent the back button from closing the launcher
        // M3 Expressive / One UI 7 relies strictly on spatial swiping
    }
}