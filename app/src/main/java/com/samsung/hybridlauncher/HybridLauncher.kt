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
import com.samsung.hybridlauncher.utils.MemoryWatcher
import com.samsung.hybridlauncher.utils.SystemUiOverrides
import com.samsung.hybridlauncher.wellbeing.BiometricVaultManager
import com.samsung.hybridlauncher.wellbeing.LaunchFrictionOverlay
import com.samsung.hybridlauncher.workspace.FreeformWorkspaceLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HybridLauncher : FragmentActivity() {

    @Inject lateinit var memoryWatcher: MemoryWatcher
    @Inject lateinit var systemUiOverrides: SystemUiOverrides
    @Inject lateinit var biometricVaultManager: BiometricVaultManager

    private lateinit var workspaceLayout: FreeformWorkspaceLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fixes One UI 4 gesture navigation freeze
        systemUiOverrides.applyGestureFreezeWorkaround(this)
        memoryWatcher.startWatching()

        setupHybridArchitecture()
        checkNotificationListenerPermissions()
    }

    private fun setupHybridArchitecture() {
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        workspaceLayout = FreeformWorkspaceLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootContainer.addView(workspaceLayout)

        val composeOverlay = androidx.compose.ui.platform.ComposeView(this).apply {
            setContent {
                Box(modifier = Modifier.fillMaxSize()) {
                    NowBarContainer(modifier = Modifier.align(Alignment.BottomCenter))
                    LaunchFrictionOverlay(
                        isVisible = false,
                        appName = "Social Media",
                        onProceed = { },
                        onCancel = { }
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
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        biometricVaultManager.lockVault()
    }

    override fun onDestroy() {
        super.onDestroy()
        memoryWatcher.stopWatching()
    }
}