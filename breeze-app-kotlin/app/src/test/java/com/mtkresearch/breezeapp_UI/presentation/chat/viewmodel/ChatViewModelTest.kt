package com.mtkresearch.breezeapp_UI.presentation.chat.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.mtkresearch.breezeapp_UI.domain.model.ChatMessage
import com.mtkresearch.breezeapp_UI.domain.model.MessageAuthor
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.ConnectAIRouterUseCase
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.LoadChatHistoryUseCase
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.SaveMessageUseCase
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.SendMessageUseCase
import com.mtkresearch.breezeapp_router.domain.ConnectionState
import com.mtkresearch.breezeapp_router.domain.model.AIResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.ZonedDateTime
import java.util.UUID

@ExperimentalCoroutinesApi
@ExtendWith(MockitoExtension::class)
class ChatViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock private lateinit var connectAIRouterUseCase: ConnectAIRouterUseCase
    @Mock private lateinit var sendMessageUseCase: SendMessageUseCase
    @Mock private lateinit var loadChatHistoryUseCase: LoadChatHistoryUseCase
    @Mock private lateinit var saveMessageUseCase: SaveMessageUseCase

    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Stub the default behavior for use cases that return flows
        whenever(connectAIRouterUseCase.getConnectionState()).thenReturn(flowOf(ConnectionState.DISCONNECTED))
        whenever(loadChatHistoryUseCase.invoke(anyOrNull())).thenReturn(flowOf(emptyList()))
        
        viewModel = ChatViewModel(
            sendMessageUseCase,
            connectAIRouterUseCase,
            loadChatHistoryUseCase,
            saveMessageUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSession with valid id should trigger history loading`() = runTest {
        // Given
        val sessionId = "session-1"
        val history = listOf(ChatMessage("id1", sessionId, MessageAuthor.USER, "Hello", ZonedDateTime.now()))
        whenever(loadChatHistoryUseCase.invoke(eq(sessionId))).thenReturn(flowOf(history))

        // When
        viewModel.loadSession(sessionId)
        advanceUntilIdle()

        // Then
        verify(loadChatHistoryUseCase).invoke(eq(sessionId))
        assertThat(viewModel.messages.value).isEqualTo(history)
    }

    @Test
    fun `sendMessage in new session should save user message and call send use case`() = runTest {
        // Given
        val messageText = "Hello AI"
        viewModel.loadSession(null) // Start a new session
        viewModel.updateInputText(messageText)
        whenever(sendMessageUseCase.invoke(any(), any())).thenReturn(flowOf()) // Assume no response for this test

        // When
        viewModel.sendMessage()
        advanceUntilIdle()

        // Then
        val messageCaptor = argumentCaptor<ChatMessage>()
        // Verify saveMessageUseCase was called for the user's message
        verify(saveMessageUseCase).invoke(messageCaptor.capture())
        assertThat(messageCaptor.firstValue.content).isEqualTo(messageText)
        assertThat(messageCaptor.firstValue.author).isEqualTo(MessageAuthor.USER)
        
        // Verify sendMessageUseCase was called with the user message and empty history
        verify(sendMessageUseCase).invoke(eq(messageCaptor.firstValue), eq(emptyList()))
        assertThat(viewModel.isAIResponding.value).isTrue()
    }

    @Test
    fun `successful AI response should be saved via use case`() = runTest {
        // Given
        val userMessageText = "Query"
        val sessionId = UUID.randomUUID().toString()
        val aiResponseText = "This is the AI response."
        
        val userMessage = ChatMessage(UUID.randomUUID().toString(), sessionId, MessageAuthor.USER, userMessageText, ZonedDateTime.now())
        val aiResponse = AIResponse(isSuccess = true, text = aiResponseText, id = UUID.randomUUID().toString())

        whenever(sendMessageUseCase.invoke(any(), any())).thenReturn(flowOf(aiResponse))
        
        viewModel.loadSession(sessionId)
        viewModel.updateInputText(userMessageText)
        
        // When
        viewModel.sendMessage()
        advanceUntilIdle()

        // Then
        val messageCaptor = argumentCaptor<ChatMessage>()
        // Verify saveMessageUseCase was called twice (user and AI)
        verify(saveMessageUseCase, times(2)).invoke(messageCaptor.capture())
        
        val savedUserMessage = messageCaptor.allValues.first { it.author == MessageAuthor.USER }
        val savedAiMessage = messageCaptor.allValues.first { it.author == MessageAuthor.AI }

        assertThat(savedUserMessage.content).isEqualTo(userMessageText)
        assertThat(savedAiMessage.content).isEqualTo(aiResponseText)
        
        assertThat(viewModel.isAIResponding.value).isFalse()
    }

    @Test
    fun `isAIResponding state should be true during call and false after completion`() = runTest {
        // Given
        val userMessageText = "Query"
        // Let the flow hang to check the intermediate state
        whenever(sendMessageUseCase.invoke(any(), any())).thenReturn(flowOf()) 
        
        viewModel.loadSession("sid-1")
        viewModel.updateInputText(userMessageText)

        // When
        viewModel.sendMessage()
        
        // Then: Before completion
        assertThat(viewModel.isAIResponding.value).isTrue()

        // When: After completion
        advanceUntilIdle() // Let the empty flow complete
        assertThat(viewModel.isAIResponding.value).isFalse()
    }
} 