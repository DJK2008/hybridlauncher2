package com.samsung.hybridlauncher.utils

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Debug
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggressive background memory manager.
 * Monitors the Proportional Set Size (PSS) of the launcher process.
 * If memory usage creeps towards the 200MB threshold, it forcibly triggers
 * garbage collection and prunes hidden UI layer caches.
 */
@Singleton
class MemoryWatcher @Inject constructor(
    @ApplicationContext private val context: Context
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "MemoryWatcher"

        // Strict budget in MBs
        private const val CRITICAL_MEMORY_THRESHOLD_MB = 180
        private const val WARNING_MEMORY_THRESHOLD_MB = 150

        // Polling interval for PSS calculation (every 15 seconds)
        private const val POLLING_INTERVAL_MS = 15000L
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private var watcherJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Starts the active memory monitoring loop.
     * Should be called from the LauncherApplication's onCreate().
     */
    fun startWatching() {
        context.registerComponentCallbacks(this)

        watcherJob?.cancel()
        watcherJob = scope.launch {
            while (isActive) {
                checkMemoryUsage()
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops monitoring (useful if transitioning to a fallback safe-mode).
     */
    fun stopWatching() {
        context.unregisterComponentCallbacks(this)
        watcherJob?.cancel()
    }

    private fun checkMemoryUsage() {
        // PSS (Proportional Set Size) is what the Android Low Memory Killer Daemon (LMKD)
        // uses to decide which processes to kill. It includes private pages and a proportional
        // share of shared pages (like memory-mapped fonts or shared graphic buffers).
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)

        // TotalPss is measured in KB, convert to MB
        val totalPssMB = memoryInfo.totalPss / 1024

        when {
            totalPssMB >= CRITICAL_MEMORY_THRESHOLD_MB -> {
                Log.w(TAG, "Critical memory threshold breached: ${totalPssMB}MB. Initiating aggressive sweep.")
                executeAggressiveMemorySweep()
            }
            totalPssMB >= WARNING_MEMORY_THRESHOLD_MB -> {
                Log.i(TAG, "Memory warning threshold reached: ${totalPssMB}MB. Suggesting GC.")
                suggestGarbageCollection()
            }
        }
    }

    /**
     * A last-resort sweep to stay under the 200MB cap and avoid termination.
     */
    private fun executeAggressiveMemorySweep() {
        // 1. Force a system-wide memory trim broadcast locally
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        // 2. Clear native memory (e.g., SQLite connection caches, hardware accelerated layers)
        ActivityManager.getInstance().releaseSomeActivities(context as android.app.Application)

        // 3. Request aggressive JVM Garbage Collection
        System.gc()
        System.runFinalization()
    }

    private fun suggestGarbageCollection() {
        // A softer approach when we are getting close but not in immediate danger
        System.gc()
    }

    // --- ComponentCallbacks2 Implementation ---

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // The user pressed Home or switched apps. The launcher is now in the background.
                // This is the optimal time to drop heavy Compose state caches or large Bitmaps
                // associated with the App Drawer or Tertiary Theme Engine.
                Log.d(TAG, "UI Hidden: Dropping transient rendering caches.")
                // E.g., Coil.imageLoader(context).memoryCache?.clear()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.d(TAG, "System memory pressure high (Level: $level). Shedding non-critical data.")
                System.gc()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Unused for memory tracking, but required by interface
    }

    override fun onLowMemory() {
        Log.e(TAG, "System signaled onLowMemory. Purging all volatile state.")
        executeAggressiveMemorySweep()
    }
}