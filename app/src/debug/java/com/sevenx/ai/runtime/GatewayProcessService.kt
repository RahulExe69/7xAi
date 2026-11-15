package com.sevenx.ai.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Debug-only Node embedding proof of concept.
 *
 * This is intentionally a visible-session service, not an always-on product
 * service. It exists only to prove the Android native runtime lifecycle before
 * adding a production foreground-service policy.
 */
class GatewayProcessService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!nodeStartRequested.compareAndSet(false, true)) {
            return START_NOT_STICKY
        }

        Thread(::runNode, "7xai-node-poc").start()
        return START_NOT_STICKY
    }

    private fun runNode() {
        try {
            val script = File(filesDir, "node-poc/server.mjs")
            script.parentFile?.mkdirs()
            assets.open("node-poc/server.mjs").use { input ->
                script.outputStream().use { output -> input.copyTo(output) }
            }

            val cache = File(cacheDir, "node-poc").apply { mkdirs() }
            val environment = arrayOf(
                "NODE_ENV=production",
                "HOST=127.0.0.1",
                "PORT=$PORT",
                "POC_TOKEN=$POC_TOKEN",
                "HOME=${filesDir.absolutePath}",
                "TMPDIR=${cache.absolutePath}",
                "NODE_COMPILE_CACHE=${File(cache, "compile-cache").absolutePath}",
            )
            val arguments = arrayOf("node", script.absolutePath)

            Log.i(TAG, "Starting embedded Node POC in process ${Process.myPid()}")
            val exitCode = NativeNode.startNode(arguments, environment, filesDir.absolutePath)
            Log.i(TAG, "Embedded Node returned with exit code $exitCode")
        } catch (error: Throwable) {
            Log.e(TAG, "Embedded Node failed to start", error)
        } finally {
            // A fresh service process is required before node::Start() can be
            // invoked again. Terminating this isolated process is the restart.
            Process.killProcess(Process.myPid())
        }
    }

    companion object {
        const val PORT = 20128
        const val POC_TOKEN = "7xai-node-poc-only-2026-do-not-ship"
        private const val TAG = "7xAI-Node-POC"
        private val nodeStartRequested = AtomicBoolean(false)
    }
}
