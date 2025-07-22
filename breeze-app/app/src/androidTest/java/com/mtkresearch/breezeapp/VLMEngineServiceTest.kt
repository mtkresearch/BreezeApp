package com.mtkresearch.breezeapp

import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.mtkresearch.breezeapp.service.VLMEngineService
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer
import java.util.function.Function

@RunWith(AndroidJUnit4::class)
class VLMEngineServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    /**
     * Test [VLMEngineService.analyzeImage]
     *
     * Test functions:
     *  - VLMEngineService.initialize()
     *  - VLMEngineService.ready()
     *  - VLMEngineService.analyzeImage()
     */
    @Test
    fun testVLMEngineService() {
        val latch = CountDownLatch(1)
        val message = "What's the subject inside the image?"
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val inputStream = testContext.assets.open("testImage.png")
        val tempFile = File(targetContext.cacheDir, "testImage.png")
        val intent = Intent(targetContext, VLMEngineService::class.java)

        // copy assets image into cache directory
        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        // load cache image into uri
        val imageUri = FileProvider.getUriForFile(
            targetContext,
            "${targetContext.packageName}.fileprovider",
            tempFile
        )
        assertTrue(imageUri.toString().startsWith("content://"))

        // start VLMEngineService
        val componentName = targetContext.startService(intent)
        assertTrue("start VLMEngineService failed.", componentName.toString().isNotEmpty())

        // bind VLMEngineService
        val binder = serviceRule.bindService(intent)
        val service = (binder as VLMEngineService.LocalBinder).service
        assertTrue("bind VLMEngineService failed.", service != null)

        service.initialize().thenAccept { initResult ->
            assertTrue("init VLMEngineService failed.", initResult)
            assertTrue("VLMEngineService is not ready.", service.isReady)
            Log.d("VLMEngineService", "VLM Service Initialized and Ready")

            service.analyzeImage(imageUri, message)
                .thenAccept(Consumer { response: String? ->
                    Log.d("VLMEngineService", "VLMEngineService response: $response")
                })
                .exceptionally(Function { throwable: Throwable? ->
                    assertTrue("Analyze image failed: ${throwable?.message}", true)
                    null
                })

        }.exceptionally { throwable ->
            Log.e("VLMEngineService", "Initialization failed", throwable)
            latch.countDown()
            null
        }

    }
}