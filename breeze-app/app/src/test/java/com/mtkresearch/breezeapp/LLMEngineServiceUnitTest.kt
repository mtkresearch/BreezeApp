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
        // Prepare the prompt and parameters
        val prompt = "Tell me a joke."
        val llmParams = mock(LLMInferenceParams::class.java)
        `when`(llmParams.maxToken).thenReturn(128)
        `when`(llmParams.temperature).thenReturn(0.7f)

        val controller = Robolectric.buildService(LLMEngineService::class.java)
        val service = controller.create().get()
        val spyService = spy(service)
        `when`(spyService.isReady).thenReturn(true)

        // Prepare a mocked future
        `when`(spyService.initialize())
            .thenReturn(CompletableFuture.completedFuture(true))

        val specificTestLambda: (String) -> Unit = { token -> Log.d("tag", "dummy lambda: $token") }
        // mock generateStreamingResponse
        doAnswer { invocation ->
            CompletableFuture.completedFuture("final output")
        }.`when`(spyService).generateStreamingResponse(
            prompt,
            llmParams,
            specificTestLambda
        )

        // Stub stopGeneration (optional unless it has side effects)
        doNothing().`when`(spyService).stopGeneration()

        spyService.initialize().thenAccept { initResult ->
            spyService.generateStreamingResponse(prompt, llmParams) { tokens ->
            }.thenAccept { finalResponse ->
                spyService.stopGeneration()

                verify(spyService).generateStreamingResponse(eq(prompt), eq(llmParams), any())
                verify(spyService).stopGeneration()
            }
        }

        controller.destroy()
    }
}