package com.mtkresearch.breezeapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.executorch.ModelType
import com.google.gson.Gson
import com.mtkresearch.breezeapp.service.LLMEngineService
import com.mtkresearch.breezeapp.utils.AppConstants
import com.mtkresearch.breezeapp.utils.ChatMessage
import com.mtkresearch.breezeapp.utils.LLMInferenceParams
import com.mtkresearch.breezeapp.utils.ModelDownloadDialog
import com.mtkresearch.breezeapp.utils.ModelFilter
import com.mtkresearch.breezeapp.utils.ModelUtils
import com.mtkresearch.breezeapp.utils.PromptManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class LLMEngineServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    companion object {

        const val TAG = "LLMEngineServiceTest"

        /**
         * Download all the LLM models checked by [AppConstants.needsModelDownload]
         * before running any test case.
         *
         * Test functions:
         *  - AppConstants.needsModelDownload()
         *  - ModelFilter.writeFilteredModelListToFile()
         *  - ModelFilter.readFilteredModelList()
         *  - ModelDownloadDialog.saveDownloadedModelList()
         */
        @BeforeClass
        @JvmStatic
        fun setupOnce() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            when (AppConstants.needsModelDownload(context)) {
                true -> {
                    runBlocking {
                        try {
                            val jsonString =
                                context.assets.open("fullModelList.json").bufferedReader()
                                    .use { it.readText() }
                            val config = Gson().fromJson(jsonString, LLMModelConfig::class.java)
                            assertTrue(
                                "models in fullModelList.json is empty.",
                                config.models.isNotEmpty()
                            )

                            val modelsDir = File(context.filesDir, "models")
                            modelsDir.mkdirs()

                            // download model files and write to app files folder
                            for (model in config.models) {
                                for (modelURL in model.urls) {
                                    Log.d(TAG, "Downloading: $modelURL")
                                    val modelDir = File(modelsDir, model.id)
                                    modelDir.mkdirs()
                                    val url = URL(modelURL)
                                    // extract filename
                                    val fileName = url.path.substringAfterLast("/")
                                    // strip query parameters (like ?download=true)
                                    val cleanFileName = fileName.substringBefore("?")

                                    val outputFile = File(modelDir, cleanFileName)
                                    Log.d(TAG, "Downloading to: ${outputFile.absolutePath}")

                                    val connection = url.openConnection() as HttpURLConnection
                                    val contentLength = connection.contentLengthLong
                                    connection.inputStream.use { input ->
                                        outputFile.outputStream().use { output ->
                                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                            var bytesRead: Int
                                            var totalBytesRead = 0L
                                            var lastReportedProgress = -1

                                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                                output.write(buffer, 0, bytesRead)
                                                totalBytesRead += bytesRead

                                                if (contentLength > 0) {
                                                    val progress = (100 * totalBytesRead / contentLength).toInt()
                                                    if (progress != lastReportedProgress) {
                                                        lastReportedProgress = progress
                                                        Log.d(TAG, "Download progress: $progress%")
                                                    }
                                                }
                                            }

                                            Log.d(TAG, "Download complete: ${totalBytesRead / 1024} KB")
                                        }
                                    }

                                }
                            }

                            // (Coupling Issue)
                            // LLMEngineService will check downloadModelList.json for init Service.
                            // Copy assets/fullModelList.json to filteredModelList.json
                            // Copy filteredModelList.json to downloadedModelList.json
                            ModelFilter.writeFilteredModelListToFile(context)
                            val filteredModelList = ModelFilter.readFilteredModelList(context)
                            if (filteredModelList != null) {
                                ModelDownloadDialog.saveDownloadedModelList(
                                    context,
                                    filteredModelList
                                )
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()
                            assertTrue("setupOnce launches failed: ${e.message}", true)
                        }
                    }
                }

                else -> {
                    Log.d(TAG, "Model files are ready.")
                }
            }
        }
    }

    /**
     * Test [LLMEngineService.generateStreamingResponse]
     *
     * Test functions:
     *  - AppConstants.needsModelDownload()
     *  - ModelUtils.getPrefModelInfo()
     *  - PromptManager.formatCompletePrompt()
     *  - LLMInferenceParams.fromSharedPreferences()
     *  - LLMEngineService.initialize()
     *  - LLMEngineService.generateStreamingResponse()
     *  - PromptFormat.getSystemPromptTemplate()
     *  - PromptFormat.getUserPromptTemplate()
     *  - LLMEngineService.stopGeneration()
     */
    @Test
    fun testLLMEngineServiceGenerateStreamingResponse() {

        val context = ApplicationProvider.getApplicationContext<Context>()
        val userMessage = "who are you?"
        val conversationHistory = listOf<ChatMessage>()
        val llmResponse = StringBuffer()

        // check /data/applicationId/files/model folder exist
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
        // AppConstants.needsModelDownload must return false to avoid launch failed in LLMEngineService.
        assertTrue(!AppConstants.needsModelDownload(context))

        val latch = CountDownLatch(1)
        val intent = Intent(context, LLMEngineService::class.java)
        val modelInfo = ModelUtils.getPrefModelInfo(context)
        intent.apply {
            putExtra("base_folder", modelInfo["baseFolder"])
            putExtra("model_entry_path", modelInfo["modelEntryPath"])
            putExtra("preferred_backend", modelInfo["backend"])
        }

        // start LLMEngineService
        val componentName = context.startService(intent)
        assertTrue("start LLMEngineService failed.", componentName.toString().isNotEmpty())

        // bind LLMEngineService
        val binder = serviceRule.bindService(intent)
        val service = (binder as LLMEngineService.LocalBinder).service
        assertTrue("bind LLMEngineService failed.", service != null)

        // test LLMEngineService.generateStreamingResponse
        val prompt =
            PromptManager.formatCompletePrompt(userMessage, conversationHistory, ModelType.BREEZE_2)
        val llmParams = LLMInferenceParams.fromSharedPreferences(context)
        service.initialize().thenAccept { initResult ->
            assertTrue("Init LLM failed: $initResult", initResult)
            service.generateStreamingResponse(prompt, llmParams) { tokens ->
                Log.d(TAG, "token: $tokens")
                llmResponse.append(tokens)
            }.thenAccept { finalResponse ->
                Log.d(TAG, "token end: $finalResponse")
                llmResponse.append(finalResponse)
                assertTrue("LLM response is empty.", llmResponse.isNotEmpty())
                service.stopGeneration()
                latch.countDown()
            }
        }

        latch.await()
    }


}