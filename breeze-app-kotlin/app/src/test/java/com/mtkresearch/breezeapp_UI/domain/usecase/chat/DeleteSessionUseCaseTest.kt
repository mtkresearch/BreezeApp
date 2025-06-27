package com.mtkresearch.breezeapp_UI.domain.usecase.chat

import com.mtkresearch.breezeapp_UI.domain.repository.ChatRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations

class DeleteSessionUseCaseTest {

    @Mock
    private lateinit var mockRepository: ChatRepository

    private lateinit var useCase: DeleteSessionUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = DeleteSessionUseCase(mockRepository)
    }

    @Test
    fun `invoke with valid sessionId should call repository's deleteSession`() = runTest {
        // Given
        val sessionId = "session-to-delete"

        // When
        useCase(sessionId)

        // Then
        verify(mockRepository).deleteSession(eq(sessionId))
        verifyNoMoreInteractions(mockRepository)
    }

    @Test
    fun `invoke with blank sessionId should not call repository`() = runTest {
        // Given
        val blankSessionId = "   "

        // When
        useCase(blankSessionId)

        // Then
        verifyNoInteractions(mockRepository)
    }
} 