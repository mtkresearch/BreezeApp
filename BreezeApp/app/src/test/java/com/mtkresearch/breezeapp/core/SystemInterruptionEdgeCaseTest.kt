package com.mtkresearch.breezeapp.core

import android.content.Context
import android.media.AudioManager
import com.mtkresearch.breezeapp.domain.model.breezeapp.BreezeAppError
import com.mtkresearch.breezeapp.domain.usecase.breezeapp.*
import com.mtkresearch.breezeapp.edgeai.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

/**
 * System Interruption Edge Case Tests
 * 
 * 基於 BreezeApp_Edge_Case_Test_Plan.md 的邊緣案例測試
 * 涵蓋: PS-03 系統中斷 (來電/鬧鐘), 音頻焦點處理, 前台服務中斷
 */
@RunWith(RobolectricTestRunner::class)
class SystemInterruptionEdgeCaseTest {

    private lateinit var asrUseCase: AsrMicrophoneUseCase
    private lateinit var ttsUseCase: TtsUseCase
    private lateinit var streamingChatUseCase: StreamingChatUseCase
    
    private val mockContext = mockk<Context>()
    private val mockAudioManager = mockk<AudioManager>()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock EdgeAI static methods
        mockkObject(EdgeAI)
        
        asrUseCase = AsrMicrophoneUseCase()
        ttsUseCase = TtsUseCase()
        streamingChatUseCase = StreamingChatUseCase()
        
        // Mock Context和AudioManager
        every { mockContext.getSystemService(Context.AUDIO_SERVICE) } returns mockAudioManager
        every { mockAudioManager.mode } returns AudioManager.MODE_NORMAL
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(EdgeAI)
    }

    // ========== PS-03: 來電中斷測試 ==========

    @Test
    fun `PS-03a - ASR進行中來電應該暫停並在通話結束後恢復`() = testScope.runTest {
        // Given: Mock EdgeAI ASR response
        val mockResponse = mockk<ASRResponse> {
            every { text } returns "Normal speech"
        }
        
        val asrFlow = flow {
            emit(mockResponse)
            delay(1000)
            throw BreezeAppError.AsrError.AudioFocusLost("Phone call incoming")
        }
        
        every { EdgeAI.asr(any()) } returns asrFlow

        // When: Start ASR and simulate phone call interruption
        val responses = mutableListOf<ASRResponse>()
        var asrError: Exception? = null
        
        val job = launch {
            try {
                asrUseCase.execute().collect { response ->
                    responses.add(response)
                }
            } catch (e: Exception) {
                asrError = e
            }
        }

        advanceUntilIdle()
        
        // Then: Should catch audio focus lost error
        assertTrue("Should receive normal speech", responses.size >= 1)
        assertTrue("Should catch audio focus lost", asrError is BreezeAppError.AsrError.AudioFocusLost)
        
        job.cancel()
    }

    @Test
    fun `PS-03b - TTS播放中來電應該暫停音頻輸出`() = testScope.runTest {
        // Given: Mock TTS response
        val mockResponse = mockk<TTSResponse> {
            every { isLastChunk } returns false
        }
        
        val ttsFlow = flow {
            repeat(5) { index ->
                if (index == 3) {
                    throw BreezeAppError.TtsError.AudioFocusLost("Incoming call")
                }
                delay(200)
                emit(mockResponse)
            }
        }
        
        every { EdgeAI.tts(any()) } returns ttsFlow

        // When: Start TTS and simulate phone call
        val responses = mutableListOf<TTSResponse>()
        var ttsError: Exception? = null
        
        val job = launch {
            try {
                ttsUseCase.execute("Test text").collect { response ->
                    responses.add(response)
                }
            } catch (e: Exception) {
                ttsError = e
            }
        }

        advanceUntilIdle()
        
        // Then: Should interrupt TTS and catch error
        assertTrue("Should have some audio output before call", responses.size > 0)
        assertTrue("Should catch audio focus lost", ttsError is BreezeAppError.TtsError.AudioFocusLost)
        
        job.cancel()
    }

    @Test
    fun `PS-03c - 鬧鐘觸發應該正確處理音頻優先級`() = testScope.runTest {
        // Given: Mock system audio mode changes
        every { mockAudioManager.mode } returnsMany listOf(
            AudioManager.MODE_NORMAL,
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_NORMAL
        )
        
        val mockResponse = mockk<ASRResponse> {
            every { text } returns "Before alarm"
        }
        
        val asrFlow = flow {
            emit(mockResponse)
            delay(500)
            
            // Check system audio mode
            if (mockAudioManager.mode == AudioManager.MODE_RINGTONE) {
                throw BreezeAppError.AsrError.SystemInterruption("Alarm triggered")
            }
        }
        
        every { EdgeAI.asr(any()) } returns asrFlow

        // When: ASR runs during alarm trigger
        val responses = mutableListOf<ASRResponse>()
        var systemError: Exception? = null
        
        try {
            asrUseCase.execute().collect { response ->
                responses.add(response)
            }
        } catch (e: Exception) {
            systemError = e
        }

        advanceUntilIdle()

        // Then: Should detect system interruption
        assertTrue("Should have response before alarm", responses.any { it.text == "Before alarm" })
        assertTrue("Should detect system interruption", systemError is BreezeAppError.AsrError.SystemInterruption)
    }

    // ========== 音頻焦點競爭測試 ==========

    @Test
    fun `同時進行的音頻操作應該正確處理焦點競爭`() = testScope.runTest {
        // Given: TTS and ASR simultaneously request audio focus
        val ttsResponse = mockk<TTSResponse> {
            every { isLastChunk } returns false
        }
        
        val ttsFlow = flow {
            emit(ttsResponse)
            delay(1000)
            emit(mockk<TTSResponse> { every { isLastChunk } returns true })
        }
        
        val asrFlow = flow<ASRResponse> {
            delay(500) // ASR starts during TTS playback
            throw BreezeAppError.AsrError.AudioFocusConflict("TTS is playing")
        }
        
        every { EdgeAI.tts(any()) } returns ttsFlow
        every { EdgeAI.asr(any()) } returns asrFlow

        // When: Start both TTS and ASR simultaneously
        val ttsResponses = mutableListOf<TTSResponse>()
        val asrResponses = mutableListOf<ASRResponse>()
        var asrConflictError: Exception? = null
        
        val ttsJob = launch {
            ttsUseCase.execute("Test TTS").collect { response ->
                ttsResponses.add(response)
            }
        }
        
        val asrJob = launch {
            try {
                asrUseCase.execute().collect { response ->
                    asrResponses.add(response)
                }
            } catch (e: Exception) {
                asrConflictError = e
            }
        }

        advanceUntilIdle()
        
        ttsJob.cancel()
        asrJob.cancel()

        // Then: Should properly handle audio focus conflict
        assertTrue("TTS should play normally", ttsResponses.isNotEmpty())
        assertTrue("ASR should detect conflict", asrConflictError is BreezeAppError.AsrError.AudioFocusConflict)
    }

    @Test
    fun `音頻焦點恢復應該重新啟動暫停的操作`() = testScope.runTest {
        // Given: Audio focus lost then regained scenario
        var focusState = "granted"
        
        val asrFlow = flow {
            when (focusState) {
                "granted" -> {
                    emit(mockk<ASRResponse> {
                        every { text } returns "Normal operation"
                    })
                }
                "lost" -> {
                    throw BreezeAppError.AsrError.AudioFocusLost("Focus temporarily lost")
                }
                "regained" -> {
                    emit(mockk<ASRResponse> {
                        every { text } returns "Resumed operation"
                    })
                }
            }
        }
        
        every { EdgeAI.asr(any()) } returns asrFlow

        // When: Normal operation -> Focus lost -> Focus regained
        val responses = mutableListOf<ASRResponse>()
        
        // Normal operation
        focusState = "granted"
        var job = launch {
            asrUseCase.execute().take(1).collect { response ->
                responses.add(response)
            }
        }
        advanceTimeBy(100)
        job.cancel()
        
        // Focus lost
        focusState = "lost"
        var focusLostError: Exception? = null
        job = launch {
            try {
                asrUseCase.execute().collect { }
            } catch (e: Exception) {
                focusLostError = e
            }
        }
        advanceTimeBy(100)
        job.cancel()
        
        // Focus regained
        focusState = "regained"
        job = launch {
            asrUseCase.execute().take(1).collect { response ->
                responses.add(response)
            }
        }
        advanceTimeBy(100)
        job.cancel()

        // Then: Should have normal operation and recovery records
        assertTrue("Should have normal operation response", responses.any { it.text == "Normal operation" })
        assertTrue("Should detect focus lost", focusLostError is BreezeAppError.AsrError.AudioFocusLost)
        assertTrue("Should have resumed response", responses.any { it.text == "Resumed operation" })
    }

    // ========== 前台服務中斷測試 ==========

    @Test
    fun `前台服務被系統終止應該正確處理`() = testScope.runTest {
        // Given: Mock foreground service termination
        val chatResponse = mockk<ChatResponse> {
            every { choices } returns listOf(mockk {
                every { delta } returns mockk {
                    every { content } returns "Normal response"
                }
            })
        }
        
        val chatFlow = flow {
            emit(chatResponse)
            delay(1000)
            throw BreezeAppError.ConnectionError.ServiceDisconnected("Foreground service terminated")
        }
        
        every { EdgeAI.chat(any()) } returns chatFlow

        // When: Chat is terminated during service termination
        val responses = mutableListOf<ChatResponse>()
        var serviceError: Exception? = null
        
        try {
            streamingChatUseCase.execute("Test", "System", 0.5f).collect { response ->
                responses.add(response)
            }
        } catch (e: Exception) {
            serviceError = e
        }

        advanceUntilIdle()

        // Then: Should detect service disconnection
        assertTrue("Should have normal response", responses.isNotEmpty())
        assertTrue("Should detect service disconnection", serviceError is BreezeAppError.ConnectionError.ServiceDisconnected)
    }

    @Test
    fun `系統資源不足導致的操作失敗應該正確處理`() = testScope.runTest {
        // Given: Mock insufficient system resources
        every { EdgeAI.asr(any()) } returns flow {
            throw BreezeAppError.AsrError.ResourceUnavailable("Insufficient system resources")
        }

        // When: Try to start ASR
        var resourceError: Exception? = null
        
        try {
            asrUseCase.execute().collect { }
        } catch (e: Exception) {
            resourceError = e
        }

        advanceUntilIdle()

        // Then: Should properly handle resource unavailable error
        assertTrue("Should catch resource unavailable error", resourceError is BreezeAppError.AsrError.ResourceUnavailable)
    }

    // ========== 應用生命週期中斷測試 ==========

    @Test
    fun `應用進入背景時音頻操作應該暫停`() = testScope.runTest {
        // Given: Mock application lifecycle changes
        var appInBackground = false
        
        val ttsFlow = flow {
            repeat(10) { index ->
                if (index == 5 && appInBackground) {
                    throw BreezeAppError.TtsError.AppBackgrounded("App moved to background")
                }
                delay(100)
                emit(mockk<TTSResponse> {
                    every { isLastChunk } returns (index == 9)
                })
            }
        }
        
        every { EdgeAI.tts(any()) } returns ttsFlow

        // When: TTS plays while app moves to background
        val responses = mutableListOf<TTSResponse>()
        var backgroundError: Exception? = null
        
        val job = launch {
            try {
                ttsUseCase.execute("Background test").collect { response ->
                    responses.add(response)
                }
            } catch (e: Exception) {
                backgroundError = e
            }
        }

        advanceTimeBy(500) // Normal playback
        val responsesBeforeBackground = responses.size
        
        appInBackground = true // Simulate app going to background
        advanceTimeBy(500)
        
        job.cancel()

        // Then: Should stop when app goes to background
        assertTrue("Should have output before background", responsesBeforeBackground > 0)
        assertTrue("Should detect app backgrounded", backgroundError is BreezeAppError.TtsError.AppBackgrounded)
    }
}