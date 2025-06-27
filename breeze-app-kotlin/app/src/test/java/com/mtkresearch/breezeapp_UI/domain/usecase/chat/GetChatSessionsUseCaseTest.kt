package com.mtkresearch.breezeapp_UI.domain.usecase.chat

import com.mtkresearch.breezeapp_UI.domain.model.ChatSession
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

class GetChatSessionsUseCaseTest {

    @Mock
    private lateinit var mockRepository: ChatRepository

    private lateinit var useCase: GetChatSessionsUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = GetChatSessionsUseCase(mockRepository)
    }

    @Test
    fun `invoke should return flow of sessions from repository`() = runTest {
        // Given
        val now = ZonedDateTime.now()
        val expectedSessions = listOf(
            ChatSession("session-1", "Hello World", now.minusDays(1), now.minusHours(5), 5),
            ChatSession("session-2", "Quick Question", now, now, 2)
        )
        whenever(mockRepository.getChatSessions()).thenReturn(flowOf(expectedSessions))

        // When
        val resultFlow = useCase()
        val result = resultFlow.first()

        // Then
        assertThat(result).isEqualTo(expectedSessions)
        verify(mockRepository).getChatSessions()
        verifyNoMoreInteractions(mockRepository)
    }

    @Test
    fun `invoke when repository returns empty list should return empty flow`() = runTest {
        // Given
        whenever(mockRepository.getChatSessions()).thenReturn(flowOf(emptyList()))

        // When
        val resultFlow = useCase()
        val result = resultFlow.first()

        // Then
        assertThat(result).isEmpty()
        verify(mockRepository).getChatSessions()
    }
} 