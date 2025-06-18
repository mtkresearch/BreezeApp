package com.mtkresearch.breezeapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.google.gson.Gson
import com.mtkresearch.breezeapp.service.LLMEngineService
import com.mtkresearch.breezeapp.utils.LLMInferenceParams
import com.mtkresearch.breezeapp.utils.ModelUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class LLMEngineServiceTest {

    companion object {

        @BeforeClass
        @JvmStatic
        fun setupOnce() {
            runBlocking {
                try {
                    val context = ApplicationProvider.getApplicationContext<Context>()
                    val jsonString = context.assets.open("fullModelList.json").bufferedReader().use { it.readText() }
                    val config = Gson().fromJson(jsonString, LLMModelConfig::class.java)
                    assertTrue(config.models.isNotEmpty())

                    val modelsDir = File(context.filesDir, "models")
                    modelsDir.mkdirs()

                    // download model files and write to app files folder
                    for (model in config.models) {
                        for (modelURL in model.urls) {
                            Log.d("setupOnce", "Downloading: $modelURL")
                            val modelDir = File(modelsDir, model.id)
                            modelDir.mkdirs()
                            val url = URL(modelURL)
                            // extract filename
                            val fileName = url.path.substringAfterLast("/")
                            // strip query parameters (like ?download=true)
                            val cleanFileName = fileName.substringBefore("?")

                            val outputFile = File(modelDir, cleanFileName)
                            Log.d("setupOnce", "Downloading to: ${outputFile.absolutePath}")

                            url.openStream().use { input ->
                                outputFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun checkModelFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelsDir = File(context.filesDir, "models")
        assertTrue("Expected 'models' directory does not exist!", modelsDir.exists())

        val jsonString =
            context.assets.open("fullModelList.json").bufferedReader().use { it.readText() }
        val config = Gson().fromJson(jsonString, LLMModelConfig::class.java)

        for (model in config.models) {
            val modelDir = File(modelsDir, model.id)
            assertTrue(
                "Model directory for '${model.id}' is shockingly missing!",
                modelDir.exists()
            )

            for (modelURL in model.urls) {
                val cleanFileName = modelURL.substringAfterLast("/").substringBefore("?")
                val modelFile = File(modelDir, cleanFileName)

                assertTrue("Model file '$cleanFileName' is tragically absent!", modelFile.exists())
                assertTrue(
                    "Model file '$cleanFileName' is heartbreakingly empty!",
                    modelFile.length() > 0
                )
            }
        }

        // TODO. 檢視ModelDownloadDialog.saveDownloadedModelList()內容
        // TODO. 需寫入檔案downloadedModelList.json

    }

    @Test
    fun launchLLMEngineTest() {
        val latch = CountDownLatch(1)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, LLMEngineService::class.java)
        val modelInfo = ModelUtils.getPrefModelInfo(context)
        intent.apply {
            putExtra("base_folder", modelInfo["baseFolder"])
            putExtra("model_entry_path", modelInfo["baseFolder"])
            putExtra("preferred_backend", modelInfo["backend"])
        }
        // startService
        val componentName = context.startService(intent)
        assertNotNull("LLMEngineService should be started", componentName)
        // bindService
        val binder = serviceRule.bindService(intent)
        val service = (binder as LLMEngineService.LocalBinder).service

        // Validate service effects or internal state
        assertTrue(service.preferredBackend.isNotEmpty())
        service.initialize().thenAccept { initResult ->
            assertTrue(initResult)
            latch.countDown()
            val llmParams = LLMInferenceParams.fromSharedPreferences(context)
            service.generateStreamingResponse("who are you?", llmParams) { tokens ->
                Log.d("tag", tokens)
            }.thenAccept { finalResponse ->
                Log.d("tag", finalResponse)
                latch.countDown()
            }
        }

        latch.await()
    }
}