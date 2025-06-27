package com.mtkresearch.breezeapp_UI.domain.usecase.chat

import com.mtkresearch.breezeapp_UI.domain.model.ChatMessage
import com.mtkresearch.breezeapp_UI.domain.model.MessageAuthor
import com.mtkresearch.breezeapp_UI.domain.repository.ChatRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.*
import org.mockito.MockitoAnnotations
import java.time.ZonedDateTime
import java.util.UUID

class SaveMessageUseCaseTest {

    @Mock
    private lateinit var mockRepository: ChatRepository

    private lateinit var useCase: SaveMessageUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        useCase = SaveMessageUseCase(mockRepository)
    }

    @Test
    fun `invoke with valid message should call repository's saveMessage`() = runTest {
        // Given
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = "session-123",
            author = MessageAuthor.USER,
            content = "This is a test message.",
            timestamp = ZonedDateTime.now()
        )
        // `saveMessage` is a suspend fun that returns Unit, so no `whenever` is needed for the happy path.

        // When
        useCase(message)

        // Then
        // Verify that the repository's saveMessage was called exactly once with the correct message.
        verify(mockRepository).saveMessage(eq(message))
        verifyNoMoreInteractions(mockRepository)
    }

    @Test
    fun `invoke with blank content should not call repository`() = runTest {
        // Given
        val messageWithBlankContent = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = "session-123",
            author = MessageAuthor.USER,
            content = "   ", // Blank content
            timestamp = ZonedDateTime.now()
        )

        // When
        useCase(messageWithBlankContent)

        // Then
        // Verify that the repository's saveMessage was never called.
        verifyNoInteractions(mockRepository)
    }
} 