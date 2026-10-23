package dev.thorhub.app

import android.app.Application
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import dev.thorhub.app.platform.diagnostics.AndroidLifecycleMonitor
import dev.thorhub.app.platform.safety.CrashLoopGuard
import dev.thorhub.app.platform.session.ProcessSessionRecovery
import javax.inject.Inject

@HiltAndroidApp
class ThorHubApplication : Application() {
    private lateinit var lifecycleMonitor: AndroidLifecycleMonitor

    @Inject
    lateinit var crashLoopGuard: CrashLoopGuard

    override fun onCreate() {
        super.onCreate()
        crashLoopGuard.onProcessStart()
        lifecycleMonitor = AndroidLifecycleMonitor(this).also { it.start() }
        if (!crashLoopGuard.isSafeMode) {
            val recovered = EntryPoints.get(this, StartupDependencies::class.java).sessionRecovery().start()
            if (!recovered) crashLoopGuard.enterSafeMode()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StartupDependencies {
        fun sessionRecovery(): ProcessSessionRecovery
    }
}
