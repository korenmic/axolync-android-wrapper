package com.axolync.android.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.axolync.android.R
import com.axolync.android.server.ServerManager

/**
 * Dedicated fullscreen splash activity.
 * Shows branded splash art while embedded localhost server reaches READY state.
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false
    private var startedAtMs = 0L

    companion object {
        private const val TAG = "SplashActivity"
        private const val MIN_SPLASH_MS = 2000L
        private const val MAX_WAIT_MS = 12000L
        private const val POLL_INTERVAL_MS = 100L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        startedAtMs = System.currentTimeMillis()
        waitForServerAndLaunch()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun waitForServerAndLaunch() {
        handler.post(object : Runnable {
            override fun run() {
                if (hasNavigated) return

                val now = System.currentTimeMillis()
                val elapsed = now - startedAtMs
                val minReached = elapsed >= MIN_SPLASH_MS
                val timeoutReached = elapsed >= MAX_WAIT_MS
                val state = ServerManager.getInstance(applicationContext).getServerState()

                val shouldProceed = when (state) {
                    ServerManager.ServerState.READY -> minReached
                    ServerManager.ServerState.FAILED -> minReached
                    ServerManager.ServerState.STARTING -> timeoutReached
                }

                if (shouldProceed) {
                    if (timeoutReached && state == ServerManager.ServerState.STARTING) {
                        Log.e(TAG, "Server startup timed out after ${MAX_WAIT_MS}ms, continuing to MainActivity")
                    } else {
                        Log.i(TAG, "Proceeding to MainActivity with server state=$state after ${elapsed}ms")
                    }
                    launchMain()
                } else {
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        })
    }

    private fun launchMain() {
        if (hasNavigated) return
        hasNavigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

