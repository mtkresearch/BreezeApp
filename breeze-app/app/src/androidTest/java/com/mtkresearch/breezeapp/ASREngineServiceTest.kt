package com.mtkresearch.breezeapp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.mtkresearch.breezeapp.service.ASREngineService
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ASREngineServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun testASREngineService() {
        val testDurationSeconds = 10L
        val latch = CountDownLatch(1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, ASREngineService::class.java)

        // start ASREngineService
        val componentName = context.startService(intent)
        assertTrue("start ASREngineService failed.", componentName.toString().isNotEmpty())

        // bind ASREngineService
        val binder = serviceRule.bindService(intent)
        val service = (binder as ASREngineService.LocalBinder).service
        assertTrue("bind ASREngineService failed.", service != null)

        service.initialize().thenAccept { initResult ->
            assertTrue("init ASREngineService failed.", initResult)
            assertTrue("ASREngineService is not ready.", service.isReady)

            Log.d("ASREngineServiceTest", "ASR Service Initialized and Ready. Starting listening...")

            service.startListening { result ->
                Log.d("ASREngineServiceTest", "Listening: $result")
            }

            // Schedule stopListening() after 10 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d("ASREngineServiceTest", "10 seconds passed. Stopping listening...")
                service.stopListening()
                latch.countDown()
            },  TimeUnit.SECONDS.toMillis(testDurationSeconds))

        }.exceptionally { throwable ->
            Log.e("ASREngineServiceTest", "Initialization failed", throwable)
            latch.countDown()
            null
        }
        // Wait for the latch to be counted down, with a timeout slightly longer than the delay
        // to prevent the test from hanging indefinitely if something goes wrong.
        assertTrue(
            "Test timed out waiting for stopListening.",
            latch.await(testDurationSeconds + 5, TimeUnit.SECONDS)
        )
        Log.d("ASREngineServiceTest", "Test completed.")
    }
}