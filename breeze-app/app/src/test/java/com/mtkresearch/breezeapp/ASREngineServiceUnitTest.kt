package com.mtkresearch.breezeapp

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.mtkresearch.breezeapp.service.ASREngineService
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ASREngineServiceUnitTest {

    @Test
    fun testASREngineServiceCodeCoverage() {
        val tag = "testASREngineServiceCodeCoverage"
        val testDurationSeconds = 10L

        // init TTSEngineService by Robolectric
        val controller = Robolectric.buildService(ASREngineService::class.java)
        val service = controller.create().get()
        val spyService = spy(service)

        // code coverage
        val testIntent = Intent().apply {
            action = "ACTION_GENERATE"
        }
        spyService.onStartCommand(testIntent, 0, 1)

        // code coverage
        val dummyIntent = Intent()
        val binder: IBinder? = spyService.onBind(dummyIntent)
        if (binder is ASREngineService.LocalBinder) {
            Assert.assertNotNull("Service retrieved from binder should not be null", binder.service)
        }

        // code coverage
        val isReady = spyService.isReady
        Log.d(tag, "isReady:$isReady")
        verify(spyService).isReady

        // mock future for ASREngineService.initialize()
        `when`(spyService.initialize())
            .thenReturn(CompletableFuture.completedFuture(true))

        // mock future for ASREngineService.speak()
        doNothing().`when`(spyService).startListening(any())

        // mock future for ASREngineService.stopListening()
        doNothing().`when`(spyService).stopListening()

        val latch = CountDownLatch(1)

        // ASREngineService Test Flow
        spyService.initialize().thenAccept { initResult ->
            spyService.startListening { result ->
                Log.d("ASREngineServiceTest", "Listening: $result")
            }
            // Schedule stopListening() after 10 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d("ASREngineServiceTest", "10 seconds passed. Stopping listening...")
                spyService.stopListening()
                latch.countDown()
            },  TimeUnit.SECONDS.toMillis(testDurationSeconds))

        }.exceptionally { throwable ->
            Log.e("ASREngineServiceTest", "Initialization failed", throwable)
            latch.countDown()
            null
        }

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(testDurationSeconds, TimeUnit.SECONDS)

        // Wait for the latch to be counted down, with a timeout slightly longer than the delay
        // to prevent the test from hanging indefinitely if something goes wrong.
        assertTrue(
            "Test timed out waiting for stopListening.",
            latch.await(testDurationSeconds + 5, TimeUnit.SECONDS)
        )

        controller.destroy()
    }

}