package com.mtkresearch.breezeapp

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.service.LLMEngineService
import com.mtkresearch.breezeapp.utils.LLMInferenceParams
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CompletableFuture

@RunWith(RobolectricTestRunner::class)
class LLMEngineServiceUnitTest {

    @Test
    fun testLLMEngineServiceCodeCoverage() {
        val tag = "testLLMEngineServiceCodeCoverage"
        val prompt = "Tell me a joke."

        // init LLMEngineService by Robolectric
        val controller = Robolectric.buildService(LLMEngineService::class.java)
        val service = controller.create().get()
        val spyService = spy(service)

        // mock LLMInferenceParams
        val llmParams = mock(LLMInferenceParams::class.java)
        `when`(llmParams.maxToken).thenReturn(128)
        `when`(llmParams.temperature).thenReturn(0.7f)

        // code coverage
        val testIntent = Intent().apply {
            action = "ACTION_GENERATE"
            putExtra("prompt", prompt)
            putExtra("modelName", "gpt-3.5")
        }
        spyService.onStartCommand(testIntent, 0, 1)

        // code coverage
        val currentBackend = spyService.currentBackend
        Log.d(tag, "currentBackend:$currentBackend")
        verify(spyService).currentBackend

        // code coverage
        val dummyIntent = Intent()
        val binder: IBinder? = spyService.onBind(dummyIntent)
        if (binder is LLMEngineService.LocalBinder) {
            Assert.assertNotNull("Service retrieved from binder should not be null", binder.service)
        }

        // code coverage
        val modelName = spyService.modelName
        Log.d(tag, "modelName:$modelName")
        verify(spyService).modelName

        // mock LLMEngineService.isReady
        `when`(spyService.isReady).thenReturn(true)

        // mock future for LLMEngineService.initialize()
        `when`(spyService.initialize())
            .thenReturn(CompletableFuture.completedFuture(true))

        // mock StreamingResponseCallback
        val dummyCallback: (String) -> Unit =
            { token -> Log.d(tag, "token: $token") }

        // mock future for LLMEngineService.generateStreamingResponse()
        doReturn(CompletableFuture.completedFuture("final tokens"))
            .`when`(spyService).generateStreamingResponse(
                eq(prompt),
                eq(llmParams),
                any() // Still crucial for the callback
            )

        // mock LLMEngineService.stopGeneration()
        doNothing().`when`(spyService).stopGeneration()

        // LLMEngineService Test Flow
        spyService.initialize().thenAccept { initResult ->
            spyService.generateStreamingResponse(prompt, llmParams) { tokens ->
            }.thenAccept { finalResponse ->
                spyService.stopGeneration()
                verify(spyService).initialize()
                verify(spyService).generateStreamingResponse(prompt, llmParams, dummyCallback)
                verify(spyService).stopGeneration()
            }
        }

        controller.destroy()
    }
}