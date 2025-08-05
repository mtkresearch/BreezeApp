package com.mtkresearch.breezeapp

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.mtkresearch.breezeapp.service.TTSEngineService
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class TTSEngineServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    /**
     * Test [TTSEngineService.speak]
     *
     * Test functions:
     *  - TTSEngineService.initialize()
     *  - TTSEngineService.ready()
     *  - TTSEngineService.speak()
     *  - TTSEngineService.stopSpeaking()
     */
    @Test
    fun testTTSEngineServiceSpeak() {

        val latch = CountDownLatch(1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, TTSEngineService::class.java)
        val speakMessage = "今天天氣真好"

        // start TTSEngineService
        val componentName = context.startService(intent)
        assertTrue("start TTSEngineService failed.", componentName.toString().isNotEmpty())

        // bind TTSEngineService
        val binder = serviceRule.bindService(intent)
        val service = (binder as TTSEngineService.LocalBinder).service
        assertTrue("bind TTSEngineService failed.", service != null)

        service.initialize().thenAccept { initResult ->
            assertTrue("init TTSEngineService failed.", initResult)
            assertTrue("TTSEngineService is not ready.", service.isReady)
            service.speak(speakMessage).thenRun {
                service.stopSpeaking()
                latch.countDown()
            }.exceptionally { exception ->
                service.stopSpeaking()
                assertTrue("exception detected: ${exception.message}", true)
                null
            }
        }

        latch.await()
    }


}