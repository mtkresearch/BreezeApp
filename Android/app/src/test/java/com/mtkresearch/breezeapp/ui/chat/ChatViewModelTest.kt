package com.mtkresearch.breezeapp.ui.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.cash.turbine.test
import com.mtkresearch.breezeapp.core.utils.AppConstants
import com.mtkresearch.breezeapp.core.utils.ModelManager
import com.mtkresearch.breezeapp.core.utils.ModelType
import com.mtkresearch.breezeapp.core.utils.ServiceState
import com.mtkresearch.breezeapp.data.models.ChatMessage
import com.mtkresearch.breezeapp.data.models.MediaType
import com.mtkresearch.breezeapp.data.models.MessageFactory
import com.mtkresearch.breezeapp.data.models.MessageSender
import com.mtkresearch.breezeapp.data.repository.ConversationRepository
import com.mtkresearch.breezeapp.features.llm.LLMService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.*

@ExperimentalCoroutinesApi
class ChatViewModelTest {
    
    // Mocks
    private lateinit var mockApplication: Application
    private lateinit var mockContext: Context
    private lateinit var mockConversationRepository: ConversationRepository
    private lateinit var mockModelManager: ModelManager
    private lateinit var mockLlmService: LLMService
    
    // Class under test
    private lateinit var viewModel: TestChatViewModel
    
    // Test dispatcher for coroutines
    private val testDispatcher = StandardTestDispatcher()
    
    // StateFlow for messages
    private val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    
    // Custom ChatViewModel for testing that allows injection of dependencies
    class TestChatViewModel(
        application: Application,
        repository: ConversationRepository,
        modelManager: ModelManager
    ) : ChatViewModel(application) {
        init {
            // Replace dependencies with mocked ones
            this.conversationRepo = repository
            this.modelManager = modelManager
        }
        
        // Setter for injecting mocked LLM service
        fun injectLlmService(llmService: LLMService) {
            this.llmService = llmService
        }
    }
    
    @Before
    fun setup() {
        // Set main dispatcher for coroutines
        Dispatchers.setMain(testDispatcher)
        
        // Create mocks
        mockApplication = mock()
        mockContext = mock()
        mockConversationRepository = mock()
        mockModelManager = mock()
        mockLlmService = mock()
        
        // Mock repository's message flow
        whenever(mockConversationRepository.messages).thenReturn(messagesFlow)
        
        // Mock context for application
        whenever(mockApplication.applicationContext).thenReturn(mockContext)
        
        // Create ViewModel with mocked dependencies
        viewModel = TestChatViewModel(mockApplication, mockConversationRepository, mockModelManager)
        viewModel.injectLlmService(mockLlmService)
    }
    
    @After
    fun tearDown() {
        // Reset main dispatcher
        Dispatchers.resetMain()
    }
    
    @Test
    fun `sendMessage adds user message and processing message`() = runTest {
        // Given
        val userMessage = "Hello, how are you?"
        
        // When
        viewModel.sendMessage(userMessage)
        
        // Then
        verify(mockConversationRepository).addMessage(argThat {
            this.sender == MessageSender.USER && this.content == userMessage
        })
        
        verify(mockConversationRepository).addMessage(argThat {
            this.sender == MessageSender.ASSISTANT && this.isProcessing
        })
        
        verify(mockLlmService)?.generateText(
            anyOrNull(),
            eq(AppConstants.DEFAULT_TEMPERATURE),
            any()
        )
    }
    
    @Test
    fun `sendMessage does nothing for blank messages`() = runTest {
        // Given
        val blankMessage = "   "
        
        // When
        viewModel.sendMessage(blankMessage)
        
        // Then
        verifyNoInteractions(mockLlmService)
        verify(mockConversationRepository, never()).addMessage(any())
    }
    
    @Test
    fun `sendMessageWithMedia adds user and processing message`() = runTest {
        // Given
        val message = "Check this image"
        val mediaUri = mock<Uri>()
        val mediaType = MediaType.IMAGE
        
        // When
        viewModel.sendMessageWithMedia(message, mediaUri, mediaType)
        
        // Then
        verify(mockConversationRepository).addMessage(argThat {
            this.sender == MessageSender.USER && 
            this.content == message && 
            this.mediaUri == mediaUri &&
            this.mediaType == mediaType
        })
        
        verify(mockConversationRepository).addMessage(argThat {
            this.sender == MessageSender.ASSISTANT && this.isProcessing
        })
        
        verify(mockLlmService)?.generateText(
            argThat { this.contains("image") },
            eq(AppConstants.DEFAULT_TEMPERATURE),
            any()
        )
    }
    
    @Test
    fun `clearConversation calls repository`() = runTest {
        // When
        viewModel.clearConversation()
        
        // Then
        verify(mockConversationRepository).clearConversation()
    }
    
    @Test
    fun `updateSystemPrompt calls repository`() = runTest {
        // Given
        val newPrompt = "New system prompt"
        
        // When
        viewModel.updateSystemPrompt(newPrompt)
        
        // Then
        verify(mockConversationRepository).setSystemPrompt(newPrompt)
    }
} 