package com.mtkresearch.breezeapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.google.gson.Gson
import com.mtkresearch.breezeapp.service.LLMEngineService
import com.mtkresearch.breezeapp.utils.AppConstants
import com.mtkresearch.breezeapp.utils.LLMInferenceParams
import com.mtkresearch.breezeapp.utils.ModelDownloadDialog
import com.mtkresearch.breezeapp.utils.ModelFilter
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

        /**
         * Download LLM models according to fullModelList.json description before launching
         * all the class tests.
         */
        @BeforeClass
        @JvmStatic
        fun setupOnce() {
            runBlocking {
                try {
                    val context = ApplicationProvider.getApplicationContext<Context>()
                    val jsonString = context.assets.open("fullModelList.json").bufferedReader()
                        .use { it.readText() }
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

    /**
     * Test [LLMEngineService.generateStreamingResponse] functionality.
     *
     * Note. Please make sure [AppConstants.BACKEND_DEFAULT] is [AppConstants.BACKEND_CPU]
     * before running the test.
     */
    @Test
    fun testLLMEngineServiceGenerateStreamingResponse() {

        val context = ApplicationProvider.getApplicationContext<Context>()

        // check /data/app/files/model folder exist
        val modelsDir = File(context.filesDir, "models")
        assertTrue("Expected 'models' directory does not exist!", modelsDir.exists())

        // according the content of assets/fullModelList.json, check all the LLM model files exist
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
                assertTrue("Model file '$cleanFileName' is empty!", modelFile.length() > 0)
            }
        }

        // (Coupling Issue)
        // Copy assets/fullModelList.json to app folder filteredModelList.json.
        // Copy app/filteredModelList.json to downloadedModelList.json
        ModelFilter.writeFilteredModelListToFile(context)
        val filteredModelList = ModelFilter.readFilteredModelList(context)
        if (filteredModelList != null) {
            ModelDownloadDialog.saveDownloadedModelList(context, filteredModelList)
        }

        // (Coupling Issue)
        // AppConstants.needsModelDownload must return false to avoid launch failed in LLMEngineService.
        assertTrue(!AppConstants.needsModelDownload(context))


        // Test LLMEngineService.generateStreamingResponse()
        val latch = CountDownLatch(1)
        val intent = Intent(context, LLMEngineService::class.java)
        val modelInfo = ModelUtils.getPrefModelInfo(context)
        intent.apply {
            putExtra("base_folder", modelInfo["baseFolder"])
            putExtra("model_entry_path", modelInfo["modelEntryPath"])
            putExtra("preferred_backend", modelInfo["backend"])
        }
        val componentName = context.startService(intent)
        assertNotNull("LLMEngineService should be started", componentName)
        val binder = serviceRule.bindService(intent)
        val service = (binder as LLMEngineService.LocalBinder).service
        service.initialize().thenAccept { initResult ->
            assertTrue("Init LLM failed: $initResult", initResult)
            val llmParams = LLMInferenceParams.fromSharedPreferences(context)
            // TODO. 調整prompt格式
            service.generateStreamingResponse("who are you?", llmParams) { tokens ->
                Log.d("tokens:", tokens)
            }.thenAccept { finalResponse ->
                Log.d("tokens end:", finalResponse)
                latch.countDown()
            }
        }

        latch.await()


    }


}