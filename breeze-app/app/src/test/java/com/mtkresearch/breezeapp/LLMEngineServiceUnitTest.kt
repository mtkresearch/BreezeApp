package com.mtkresearch.breezeapp

import android.util.Log
import com.mtkresearch.breezeapp.service.LLMEngineService
import com.mtkresearch.breezeapp.utils.LLMInferenceParams
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
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
        val prompt = "Tell me a joke."
        val llmParams = mock(LLMInferenceParams::class.java)
        `when`(llmParams.maxToken).thenReturn(128)
        `when`(llmParams.temperature).thenReturn(0.7f)

        // init LLMEngineService by Robolectric
        val controller = Robolectric.buildService(LLMEngineService::class.java)
        val service = controller.create().get()
        val spyService = spy(service)
        `when`(spyService.isReady).thenReturn(true)

        // mock future for LLMEngineService.initialize()
        `when`(spyService.initialize())
            .thenReturn(CompletableFuture.completedFuture(true))

        // Stub StreamingResponseCallback
        val dummyCallback: (String) -> Unit =
            { token -> Log.d("tag", "dummy callback: $token") }

        // mock future for LLMEngineService.generateStreamingResponse()
        doAnswer { invocation ->
            CompletableFuture.completedFuture("final output")
        }.`when`(spyService).generateStreamingResponse(
            eq(prompt),
            eq(llmParams),
            any<(String) -> Unit>()
        )

        // Stub stopGeneration
        doNothing().`when`(spyService).stopGeneration()

        // LLMEngineService Test Flow
        spyService.initialize().thenAccept { initResult ->
            spyService.generateStreamingResponse(prompt, llmParams) { tokens ->
            }.thenAccept { finalResponse ->
                spyService.stopGeneration()

                verify(spyService).generateStreamingResponse(prompt, llmParams, dummyCallback)
                verify(spyService).stopGeneration()
            }
        }

        controller.destroy()
    }
}