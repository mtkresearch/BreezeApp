package com.mtkresearch.breezeapp.router

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtkresearch.breezeapp.router.config.MTKConfig
import com.mtkresearch.breezeapp.router.data.runner.MTKLLMRunner
import com.mtkresearch.breezeapp.router.domain.model.ModelConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MTKLLMRunnerTest {
    @Test
    fun testStreamingInferenceWithRealFile() = runBlocking {
        // 1. 取得 context
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 2. 檢查硬體支援
        assumeTrue(com.mtkresearch.breezeapp.router.hardware.HardwareCompatibility.isMTKNPUSupported())

        // 3. 取得 entry_point 檔案路徑
        val modelName = "Breeze2-3B-8W16A-250630-npu"
        val entryPointFile = File(context.filesDir, "models/$modelName/config_breezetiny_3b_instruct.yaml")
        assertTrue("Entry point file does not exist: ${entryPointFile.absolutePath}", entryPointFile.exists())

        // 4. 建立 runner
        val runner = MTKLLMRunner.create(
            context = context,
            config = MTKConfig(modelPath = entryPointFile.absolutePath)
        )

        // 5. 初始化 runner
        val loaded = runner.load(
            ModelConfig(
                modelName = modelName,
                modelPath = entryPointFile.absolutePath
            )
        )
        assertTrue("Runner failed to load", loaded)
        assertTrue(runner.isLoaded())

        // 6. 執行推論（可根據你的需求設計）
        // val request = ...
        // val results = runner.runAsFlow(request).toList()
        // assertTrue(results.isNotEmpty())
    }
}
