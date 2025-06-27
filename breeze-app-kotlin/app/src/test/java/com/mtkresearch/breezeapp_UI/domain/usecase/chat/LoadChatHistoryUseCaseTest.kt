package com.mtkresearch.breezeapp_UI.domain.usecase.chat

import com.mtkresearch.breezeapp_UI.domain.model.ChatMessage
import com.mtkresearch.breezeapp_UI.domain.model.MessageAuthor
import com.mtkresearch.breezeapp_UI.domain.repository.ChatRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.*
import org.mockito.MockitoAnnotations
import java.time.ZonedDateTime
import java.util.UUID

class LoadChatHistoryUseCaseTest {

    @Mock
    private lateinit var mockRepository: ChatRepository

    private lateinit var useCase: LoadChatHistoryUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = LoadChatHistoryUseCase(mockRepository)
    }

    @Test
    fun `invoke with valid sessionId should return message history from repository`() = runTest {
        // Given
        val sessionId = "session-123"
        val expectedHistory = listOf(
            ChatMessage(UUID.randomUUID().toString(), sessionId, MessageAuthor.USER, "Hello", ZonedDateTime.now()),
            ChatMessage(UUID.randomUUID().toString(), sessionId, MessageAuthor.AI, "Hi there", ZonedDateTime.now())
        )
        whenever(mockRepository.getChatMessages(sessionId)).thenReturn(flowOf(expectedHistory))

        // When
        val resultFlow = useCase(sessionId)
        val result = resultFlow.first()

        // Then
        assertThat(result).isEqualTo(expectedHistory)
        verify(mockRepository).getChatMessages(sessionId)
        verifyNoMoreInteractions(mockRepository)
    }

    @Test
    fun `invoke with null sessionId should return empty flow`() = runTest {
        // Given
        val sessionId: String? = null

        // When
        val resultFlow = useCase(sessionId)
        val result = resultFlow.first()

        // Then
        assertThat(result).isEmpty()
        verifyNoInteractions(mockRepository)
    }
    
    @Test
    fun `invoke with empty history should return empty list`() = runTest {
        // Given
        val sessionId = "session-empty"
        whenever(mockRepository.getChatMessages(sessionId)).thenReturn(flowOf(emptyList()))

        // When
        val resultFlow = useCase(sessionId)
        val result = resultFlow.first()

        // Then
        assertThat(result).isEmpty()
        verify(mockRepository).getChatMessages(sessionId)
    }
} 