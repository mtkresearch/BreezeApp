package com.mtkresearch.breezeapp

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.service.TTSEngineService
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CompletableFuture

@RunWith(RobolectricTestRunner::class)
class TTSEngineServiceUnitTest {

    @Test
    fun testTTSEngineServiceCodeCoverage() {
        val tag = "testLLMEngineServiceCodeCoverage"
        val speakMessage = "今天天氣真好"

        // init TTSEngineService by Robolectric
        val controller = Robolectric.buildService(TTSEngineService::class.java)
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
        if (binder is TTSEngineService.LocalBinder) {
            Assert.assertNotNull("Service retrieved from binder should not be null", binder.service)
        }

        // code coverage
        val isReady = spyService.isReady
        Log.d(tag, "isReady:$isReady")
        verify(spyService).isReady

        // mock future for TTSEngineService.initialize()
        `when`(spyService.initialize())
            .thenReturn(CompletableFuture.completedFuture(true))

        // mock future for TTSEngineService.speak()
        doReturn(CompletableFuture.completedFuture(true))
            .`when`(spyService).speak(eq(speakMessage))

        // mock future for TTSEngineService.initialize()
        doNothing().`when`(spyService).stopSpeaking()

        // TTSEngineService Test Flow
        spyService.initialize().thenAccept { initResult ->
            spyService.speak(speakMessage).thenAccept {
                spyService.stopSpeaking()
                verify(spyService).initialize()
                verify(spyService).speak(speakMessage)
                verify(spyService).stopSpeaking()
            }
        }


        controller.destroy()
    }

}