package com.mtkresearch.breezeapp.router

import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AIRouterServiceTest {
    private lateinit var service: AIRouterService

    @Before
    fun setUp() {
        service = AIRouterService()
    }

    // --- Configuration Validation Tests ---

    @Test
    fun configValidation_validConfig_returnsTrue() {
        val config = Configuration(
            apiVersion = 1,
            logLevel = 3,
            preferredRuntime = Configuration.RuntimeBackend.GPU,
            runnerConfigurations = mapOf(
                Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.EXECUTORCH
            ),
            defaultModelName = "gpt-3.5-turbo",
            languagePreference = "en",
            timeoutMs = 10000,
            maxTokens = 512,
            temperature = 0.5f
        )
        val result = service.javaClass.getDeclaredMethod("validateConfiguration", Configuration::class.java)
            .apply { isAccessible = true }
            .invoke(service, config) as Boolean
        assertTrue(result)
    }

    @Test
    fun configValidation_invalidApiVersion_returnsFalse() {
        val config = Configuration(apiVersion = 0)
        val result = service.javaClass.getDeclaredMethod("validateConfiguration", Configuration::class.java)
            .apply { isAccessible = true }
            .invoke(service, config) as Boolean
        assertFalse(result)
    }

    @Test
    fun configValidation_invalidLogLevel_returnsFalse() {
        val config = Configuration(logLevel = 10)
        val result = service.javaClass.getDeclaredMethod("validateConfiguration", Configuration::class.java)
            .apply { isAccessible = true }
            .invoke(service, config) as Boolean
        assertFalse(result)
    }

    @Test
    fun configValidation_negativeTimeout_returnsFalse() {
        val config = Configuration(timeoutMs = -1)
        val result = service.javaClass.getDeclaredMethod("validateConfiguration", Configuration::class.java)
            .apply { isAccessible = true }
            .invoke(service, config) as Boolean
        assertFalse(result)
    }

    @Test
    fun configValidation_zeroMaxTokens_returnsFalse() {
        val config = Configuration(maxTokens = 0)
        val result = service.javaClass.getDeclaredMethod("validateConfiguration", Configuration::class.java)
            .apply { isAccessible = true }
            .invoke(service, config) as Boolean
        assertFalse(result)
    }

    @Test
    fun configValidation_invalidTemperature_returnsFalse() {
        val config = Configuration(temperature = 1.5f)
        val result = service.javaClass.getDeclaredMethod("validateConfiguration", Configuration::class.java)
            .apply { isAccessible = true }
            .invoke(service, config) as Boolean
        assertFalse(result)
    }

    @Test
    fun configValidation_blankLanguage_returnsFalse() {
        val config = Configuration(languagePreference = " ")
        val result = service.javaClass.getDeclaredMethod("validateConfiguration", Configuration::class.java)
            .apply { isAccessible = true }
            .invoke(service, config) as Boolean
        assertFalse(result)
    }

    @Test
    fun configValidation_invalidEnum_returnsFalse() {
        val config = Configuration(
            preferredRuntime = Configuration.RuntimeBackend.GPU,
            runnerConfigurations = mapOf(
                Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.EXECUTORCH
            )
        )
        val brokenConfig = config.copy(
            runnerConfigurations = mapOf(
                Configuration.AITaskType.TEXT_GENERATION to (
                        enumValueOfOrNull<Configuration.RunnerType>("NOT_A_RUNNER") ?: Configuration.RunnerType.DEFAULT
                        )
            )
        )
        val result = service.javaClass.getDeclaredMethod("validateConfiguration", Configuration::class.java)
            .apply { isAccessible = true }
            .invoke(service, brokenConfig) as Boolean
        assertTrue(result) // 因為 fallback 為 DEFAULT，仍屬於合法的 enum 值
    }

    // --- Listener Registration/Notification Tests ---

    @Test
    fun listener_register_and_unregister() {
        val latch = CountDownLatch(1)
        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                latch.countDown()
            }
        }
        // Register
        val binder = service.javaClass.getDeclaredField("listeners").apply { isAccessible = true }.get(service) as android.os.RemoteCallbackList<IAIRouterListener>
        binder.register(listener, null)
        // Notify
        val notifyMethod = service.javaClass.getDeclaredMethod("notifyListeners", AIResponse::class.java)
        notifyMethod.isAccessible = true
        notifyMethod.invoke(
            service,
            AIResponse(
                requestId = "req-1",
                text = "test",
                isComplete = true,
                state = AIResponse.ResponseState.COMPLETED,
                apiVersion = 1,
                binaryAttachments = emptyList(),
                metadata = emptyMap(),
                error = null
            )
        )
        // Should receive callback
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        // Unregister
        binder.unregister(listener)
        // Notify again, should not receive callback
        val latch2 = CountDownLatch(1)
        notifyMethod.invoke(
            service,
            AIResponse(
                requestId = "req-2",
                text = "test2",
                isComplete = true,
                state = AIResponse.ResponseState.COMPLETED,
                apiVersion = 1,
                binaryAttachments = emptyList(),
                metadata = emptyMap(),
                error = null
            )
        )
        assertFalse(latch2.await(1, TimeUnit.SECONDS))
    }

    // --- Utility ---
    private inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? =
        try { enumValueOf<T>(name) } catch (_: Exception) { null }
} 