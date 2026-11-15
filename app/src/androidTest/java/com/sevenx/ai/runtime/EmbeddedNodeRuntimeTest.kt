package com.sevenx.ai.runtime

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sevenx.ai.MainActivity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class EmbeddedNodeRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startsAuthenticatesStopsAndRestartsInFreshGatewayProcess() {
        val context = composeRule.activity.applicationContext
        val token = GatewayProcessService.POC_TOKEN

        startGateway(context)
        val firstHealth = waitForHealth(token)
        assertEquals(200, firstHealth.status)
        val firstJson = JSONObject(firstHealth.body)
        assertTrue(firstJson.getString("nodeVersion").startsWith("v24.22.1"))
        assertFalse(firstJson.getString("mobileRuntime").isBlank())

        // Loopback does not bypass authentication, even for requests originating on-device.
        assertEquals(401, request("/healthz", token = null).status)

        assertEquals(202, request("/shutdown", method = "POST", token = token).status)
        waitUntilGatewayIsDown()

        // node::Start() is a one-shot entrypoint. A restart must come from a new
        // Android service process, not a second invocation in the old process.
        startGateway(context)
        val secondHealth = waitForHealth(token)
        assertEquals(200, secondHealth.status)
        assertTrue(JSONObject(secondHealth.body).getString("nodeVersion").startsWith("v24.22.1"))

        request("/shutdown", method = "POST", token = token)
        waitUntilGatewayIsDown()
    }

    private fun startGateway(context: Context) {
        context.startService(Intent(context, GatewayProcessService::class.java))
    }

    private fun waitForHealth(token: String): HttpResponse {
        val deadline = SystemClock.elapsedRealtime() + START_TIMEOUT_MS
        var lastFailure: Throwable? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                val response = request("/healthz", token = token)
                if (response.status == 200) return response
                lastFailure = AssertionError("Gateway returned HTTP ${response.status}: ${response.body}")
            } catch (error: Throwable) {
                lastFailure = error
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Embedded Node gateway did not become healthy", lastFailure)
    }

    private fun waitUntilGatewayIsDown() {
        val deadline = SystemClock.elapsedRealtime() + STOP_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                val response = request("/healthz", token = null)
                if (response.status == 401) {
                    SystemClock.sleep(POLL_INTERVAL_MS)
                    continue
                }
            } catch (_: Throwable) {
                return
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Gateway listener did not close after shutdown")
    }

    private fun request(
        path: String,
        method: String = "GET",
        token: String?,
    ): HttpResponse {
        val connection = (URL("http://127.0.0.1:$PORT$path").openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        if (method == "POST") connection.doOutput = true

        return try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(val status: Int, val body: String)

    private companion object {
        const val PORT = 20128
        const val HTTP_TIMEOUT_MS = 1_500
        const val START_TIMEOUT_MS = 20_000L
        const val STOP_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 200L
    }
}
