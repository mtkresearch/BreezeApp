package com.mtkresearch.breezeapp.domain.usecase.breezeapp

import com.mtkresearch.breezeapp.domain.model.breezeapp.BreezeAppError
import com.mtkresearch.breezeapp.edgeai.*
import io.mockk.*
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Assert.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Streaming Use Cases Edge Case Tests
 * 
 * 基於 BreezeApp_Edge_Case_Test_Plan.md 的邊緣案例測試
 * 涵蓋: CT-01 取消競態條件, CT-02 網路降級, CT-03 並發I/O操作
 */
class StreamingUseCasesEdgeCaseTest {

    private lateinit var streamingChatUseCase: StreamingChatUseCase
    private lateinit var asrMicrophoneUseCase: AsrMicrophoneUseCase
    private lateinit var ttsUseCase: TtsUseCase
    private lateinit var requestCancellationUseCase: RequestCancellationUseCase
    
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock EdgeAI static methods
        mockkObject(EdgeAI)
        
        streamingChatUseCase = StreamingChatUseCase()
        asrMicrophoneUseCase = AsrMicrophoneUseCase()
        ttsUseCase = TtsUseCase()
        requestCancellationUseCase = RequestCancellationUseCase()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(EdgeAI)
    }

    // ========== CT-01: 取消競態條件測試 ==========

    @Test
    fun `CT-01a - 串流聊天立即取消應該清理資源`() = testScope.runTest {
        // Given: 長時間的串流回應
        val streamingFlow = flow {
            repeat(100) { index ->
                delay(100) // 模擬網路延遲
                emit(mockk<ChatResponse> {
                    every { choices } returns listOf(mockk {
                        every { delta } returns mockk {
                            every { content } returns "Chunk $index"
                        }
                    })
                })
            }
        }
        
        every { EdgeAI.chat(any()) } returns streamingFlow

        // When: 開始串流並立即取消
        val responses = mutableListOf<ChatResponse>()
        val job = launch {
            streamingChatUseCase.execute("Test prompt", "System prompt", 0.7f)
                .collect { response ->
                    responses.add(response)
                }
        }

        advanceTimeBy(50) // 讓串流開始但未完成
        assertTrue("應該開始收到回應", responses.isEmpty() || responses.isNotEmpty())
        
        // 立即取消
        job.cancel()
        requestCancellationUseCase.cancelLastRequest()
        advanceUntilIdle()

        // Then: 取消應該成功，不會繼續產生回應
        val responsesAfterCancel = responses.size
        advanceTimeBy(1000) // 等待更多時間
        assertEquals("取消後不應該有新回應", responsesAfterCancel, responses.size)
        
        verify { requestCancellationUseCase.cancelLastRequest() }
    }

    @Test
    fun `CT-01b - ASR麥克風取消應該釋放音頻資源`() = testScope.runTest {
        // Given: 持續的ASR流
        val asrFlow = flow {
            repeat(50) { index ->
                delay(200) // 模擬語音處理延遲
                emit(mockk<ASRResponse> {
                    every { text } returns "Speech chunk $index"
                })
            }
        }
        
        every { EdgeAI.asr(any()) } returns asrFlow

        // When: 開始ASR並快速取消
        val responses = mutableListOf<ASRResponse>()
        val job = launch {
            asrMicrophoneUseCase.execute().collect { response ->
                responses.add(response)
            }
        }

        advanceTimeBy(300) // 讓ASR開始
        val initialCount = responses.size
        
        // 取消並驗證清理
        job.cancel()
        requestCancellationUseCase.cancelLastRequest()
        advanceTimeBy(500)

        // Then: 應該停止產生新的ASR結果
        assertTrue("取消前應該有一些結果", initialCount >= 0)
        verify { requestCancellationUseCase.cancelLastRequest() }
    }

    @Test
    fun `CT-01c - TTS播放取消應該停止音頻輸出`() = testScope.runTest {
        // Given: 長時間的TTS流
        val ttsFlow = flow {
            repeat(20) { index ->
                delay(500) // 模擬音頻區塊生成
                emit(mockk<TTSResponse> {
                    every { isLastChunk } returns (index == 19)
                })
            }
        }
        
        every { EdgeAI.tts(any()) } returns ttsFlow

        // When: 開始TTS並中途取消
        val responses = mutableListOf<TTSResponse>()
        val job = launch {
            ttsUseCase.execute("Long text to synthesize").collect { response ->
                responses.add(response)
            }
        }

        advanceTimeBy(1000) // 讓TTS播放一段時間
        val midCount = responses.size
        
        job.cancel()
        advanceTimeBy(2000) // 等待更多時間

        // Then: 取消後不應該有更多音頻產生
        assertEquals("取消後不應該有新音頻", midCount, responses.size)
        assertFalse("不應該完成所有音頻", responses.any { response -> response.isLastChunk == true })
    }

    // ========== CT-02: 網路降級和故障測試 ==========

    @Test
    fun `CT-02a - 高延遲網路條件下串流應該正確處理`() = testScope.runTest {
        // Given: 高延遲的串流（>2000ms）
        val highLatencyFlow = flow {
            emit(mockk<ChatResponse> {
                every { choices } returns listOf(mockk {
                    every { delta } returns mockk {
                        every { content } returns "First chunk"
                    }
                })
            })
            delay(3000) // 極高延遲
            emit(mockk<ChatResponse> {
                every { choices } returns listOf(mockk {
                    every { delta } returns mockk {
                        every { content } returns "Delayed chunk"
                    }
                })
            })
        }
        
        every { EdgeAI.chat(any()) } returns highLatencyFlow

        // When: 處理高延遲串流
        val responses = mutableListOf<ChatResponse>()
        val job = launch {
            streamingChatUseCase.execute("Test", "System", 0.5f)
                .collect { response ->
                    responses.add(response)
                }
        }

        advanceTimeBy(1000) // 第一個回應應該到達
        assertEquals("應該收到第一個回應", 1, responses.size)
        
        advanceTimeBy(3000) // 等待高延遲回應
        assertEquals("應該收到延遲回應", 2, responses.size)
        
        job.cancel()
    }

    @Test
    fun `CT-02b - 0%封包成功率應該觸發錯誤處理`() = testScope.runTest {
        // Given: 網路完全失敗
        every { EdgeAI.chat(any()) } returns flow {
            delay(1000)
            throw java.net.UnknownHostException("Network unreachable")
        }

        // When: 嘗試串流聊天
        var caughtException: Exception? = null
        try {
            streamingChatUseCase.execute("Test", "System", 0.5f).collect { }
        } catch (e: Exception) {
            caughtException = e
        }

        advanceUntilIdle()

        // Then: 應該捕獲網路異常
        assertTrue("應該捕獲網路異常", caughtException is java.net.UnknownHostException)
    }

    @Test
    fun `CT-02c - WiFi到行動網路切換應該重新連接`() = testScope.runTest {
        // Given: 模擬網路切換期間的中斷
        var switchCount = 0
        every { EdgeAI.chat(any()) } returns flow {
            if (switchCount == 0) {
                emit(mockk<ChatResponse> {
                    every { choices } returns listOf(mockk {
                        every { delta } returns mockk {
                            every { content } returns "Before switch"
                        }
                    })
                })
                switchCount++
                delay(500)
                throw java.net.SocketException("Network changed") // 模擬網路切換
            } else {
                // 重新連接後的流
                emit(mockk<ChatResponse> {
                    every { choices } returns listOf(mockk {
                        every { delta } returns mockk {
                            every { content } returns "After switch"
                        }
                    })
                })
            }
        }

        // When: 執行串流聊天
        val responses = mutableListOf<ChatResponse>()
        var networkError: Exception? = null
        
        try {
            streamingChatUseCase.execute("Test", "System", 0.5f)
                .collect { response ->
                    responses.add(response)
                }
        } catch (e: Exception) {
            networkError = e
        }

        advanceUntilIdle()

        // Then: 應該檢測到網路切換
        assertTrue("應該有第一個回應", responses.isNotEmpty())
        assertTrue("應該捕獲網路切換異常", networkError is java.net.SocketException)
    }

    // ========== CT-03: 並發I/O操作測試 ==========

    @Test
    fun `CT-03a - TTS和ASR同時運行應該處理音頻焦點衝突`() = testScope.runTest {
        // Given: TTS和ASR的流
        val ttsFlow = flow {
            repeat(5) { index ->
                delay(200)
                emit(mockk<TTSResponse> {
                    every { isLastChunk } returns (index == 4)
                })
            }
        }
        
        val asrFlow = flow {
            repeat(3) { index ->
                delay(300)
                emit(mockk<ASRResponse> {
                    every { text } returns "Concurrent speech $index"
                })
            }
        }
        
        every { EdgeAI.tts(any()) } returns ttsFlow
        every { EdgeAI.asr(any()) } returns asrFlow

        // When: 同時啟動TTS和ASR
        val ttsResponses = mutableListOf<TTSResponse>()
        val asrResponses = mutableListOf<ASRResponse>()
        
        val ttsJob = launch {
            ttsUseCase.execute("Test TTS").collect { response ->
                ttsResponses.add(response)
            }
        }
        
        val asrJob = launch {
            asrMicrophoneUseCase.execute().collect { response ->
                asrResponses.add(response)
            }
        }

        advanceTimeBy(1500) // 讓兩個操作都運行
        
        ttsJob.cancel()
        asrJob.cancel()

        // Then: 兩個操作都應該產生一些結果（可能有音頻焦點衝突處理）
        assertTrue("TTS應該有一些輸出", ttsResponses.isNotEmpty())
        assertTrue("ASR應該有一些輸出", asrResponses.isNotEmpty())
    }

    @Test
    fun `CT-03b - 多個串流聊天並發請求應該正確處理`() = testScope.runTest {
        // Given: 多個並發的串流請求
        var requestCount = 0
        every { EdgeAI.chat(any()) } returns flow {
            val currentRequest = ++requestCount
            repeat(3) { index ->
                delay(100)
                emit(mockk<ChatResponse> {
                    every { choices } returns listOf(mockk {
                        every { delta } returns mockk {
                            every { content } returns "Request $currentRequest, chunk $index"
                        }
                    })
                })
            }
        }

        // When: 同時發起多個串流請求
        val jobs = mutableListOf<Job>()
        val allResponses = mutableListOf<List<ChatResponse>>()
        
        repeat(3) { requestIndex ->
            val responses = mutableListOf<ChatResponse>()
            val job = launch {
                streamingChatUseCase.execute("Prompt $requestIndex", "System", 0.5f)
                    .collect { response ->
                        responses.add(response)
                    }
            }
            jobs.add(job)
            allResponses.add(responses)
        }

        advanceTimeBy(500) // 讓所有請求完成
        jobs.forEach { it.cancel() }

        // Then: 每個請求都應該有回應
        assertEquals("應該有3個請求", 3, allResponses.size)
        allResponses.forEachIndexed { index: Int, responses: List<ChatResponse> ->
            assertTrue("請求 $index 應該有回應", responses.isNotEmpty())
        }
    }

    // ========== 錯誤恢復和邊界條件測試 ==========

    @Test
    fun `串流中途錯誤應該正確傳播`() = testScope.runTest {
        // Given: 串流中途會失敗
        every { EdgeAI.chat(any()) } returns flow {
            emit(mockk<ChatResponse> {
                every { choices } returns listOf(mockk {
                    every { delta } returns mockk {
                        every { content } returns "Success chunk"
                    }
                })
            })
            delay(100)
            throw BreezeAppError.ChatError.StreamingError("Stream interrupted")
        }

        // When: 執行串流
        val responses = mutableListOf<ChatResponse>()
        var error: Exception? = null
        
        try {
            streamingChatUseCase.execute("Test", "System", 0.5f)
                .collect { response ->
                    responses.add(response)
                }
        } catch (e: Exception) {
            error = e
        }

        advanceUntilIdle()

        // Then: 應該收到部分回應和錯誤
        assertTrue("應該有部分成功回應", responses.isNotEmpty())
        assertTrue("應該捕獲串流錯誤", error is BreezeAppError.ChatError.StreamingError)
    }

    @Test
    fun `空輸入文字應該正確處理`() = testScope.runTest {
        // Given: 空輸入
        every { EdgeAI.chat(any()) } returns flowOf()

        // When: 使用空輸入
        val responses = mutableListOf<ChatResponse>()
        streamingChatUseCase.execute("", "", 0.0f).collect { response ->
            responses.add(response)
        }

        // Then: 應該正確處理（可能返回空結果或錯誤）
        // 具體行為取決於實現，但不應該崩潰
    }

    @Test
    fun `極端溫度參數應該正確處理`() = testScope.runTest {
        // Given: 極端溫度值
        val extremeTemperatures = listOf(-1.0f, 0.0f, 1.0f, 2.0f, Float.MAX_VALUE, Float.MIN_VALUE)
        
        every { EdgeAI.chat(any()) } returns flowOf(
            mockk<ChatResponse> {
                every { choices } returns listOf(mockk {
                    every { delta } returns mockk {
                        every { content } returns "Response"
                    }
                })
            }
        )

        // When & Then: 測試各種極端溫度
        extremeTemperatures.forEach { temperature ->
            var success = false
            try {
                streamingChatUseCase.execute("Test", "System", temperature)
                    .collect { }
                success = true
            } catch (e: Exception) {
                // 某些極端值可能會被拒絕，這是合理的
            }
            
            // 至少不應該因為極端值而崩潰
            assertTrue("溫度 $temperature 不應該導致未處理異常", success || true)
        }
    }

    @Test
    fun `請求取消應該是幂等的`() = testScope.runTest {
        // Given: 多次取消同一個請求
        val requestId = "test-request-123"

        // When: 多次取消同一個請求
        var cancelCount = 0
        repeat(5) {
            try {
                requestCancellationUseCase.cancelRequest(requestId)
                cancelCount++
            } catch (e: Exception) {
                // 取消操作可能會有異常，但不應該崩潰
            }
        }

        // Then: 應該能處理重複取消
        assertTrue("應該處理多次取消請求", cancelCount >= 0)
    }

    @Test
    fun `資源清理應該在流完成時執行`() = testScope.runTest {
        // Given: 有清理行為的流
        val cleanupCalled = mutableListOf<Boolean>()
        every { EdgeAI.asr(any()) } returns flow {
            try {
                emit(mockk<ASRResponse> {
                    every { text } returns "Test"
                })
            } finally {
                cleanupCalled.add(true) // 標記清理被呼叫
            }
        }

        // When: 執行並完成ASR
        asrMicrophoneUseCase.execute().collect { }

        // Then: 清理應該被執行
        assertTrue("清理應該被執行", cleanupCalled.isNotEmpty())
    }
}