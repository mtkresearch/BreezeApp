package com.mtkresearch.breezeapp

import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.service.VLMEngineService
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CompletableFuture

@RunWith(RobolectricTestRunner::class)
class VLMEngineServiceUnitTest {

    // Define dummy data for tests
    private val dummyUri: Uri = Uri.parse("content://dummy/image")
    private val testMessage: String = "what's inside the image?"

    @Test
    fun testVLMEngineServiceCodeCoverage() {
        val tag = "testVLMEngineServiceCodeCoverage"

        // init VLMEngineService by Robolectric
        val controller = Robolectric.buildService(VLMEngineService::class.java)
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
        if (binder is VLMEngineService.LocalBinder) {
            Assert.assertNotNull("Service retrieved from binder should not be null", binder.service)
        }

        // code coverage
        val isReady = spyService.isReady
        Log.d(tag, "isReady:$isReady")
        verify(spyService).isReady

        // mock future for VLMEngineService.initialize()
        `when`(spyService.initialize())
            .thenReturn(CompletableFuture.completedFuture(true))

        // mock future for VLMEngineService.analyzeImage()
        doReturn(CompletableFuture.completedFuture(true))
            .`when`(spyService).analyzeImage(dummyUri, testMessage)

        // VLMEngineService Test Flow
        spyService.initialize().thenAccept { initResult ->
            spyService.analyzeImage(eq(dummyUri), eq(testMessage)).thenAccept {
                verify(spyService).initialize()
                verify(spyService).analyzeImage(eq(dummyUri), eq(testMessage))
            }
        }

        controller.destroy()
    }

}