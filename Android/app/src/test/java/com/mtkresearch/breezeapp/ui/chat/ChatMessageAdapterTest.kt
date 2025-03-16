package com.mtkresearch.breezeapp.ui.chat

import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bumptech.glide.RequestManager
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.message.ChatMessageType
import com.mtkresearch.breezeapp.message.MessageFactory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class ChatMessageAdapterTest {

    @Mock
    private lateinit var context: Context
    
    @Mock
    private lateinit var layoutInflater: LayoutInflater
    
    @Mock
    private lateinit var viewGroup: ViewGroup
    
    @Mock
    private lateinit var userMessageView: View
    
    @Mock
    private lateinit var assistantMessageView: View
    
    @Mock
    private lateinit var mediaMessageView: View
    
    @Mock
    private lateinit var textView: TextView
    
    @Mock
    private lateinit var mediaImageView: ImageView
    
    @Mock
    private lateinit var copyButton: ImageButton
    
    @Mock
    private lateinit var speakerButton: ImageButton
    
    @Mock
    private lateinit var expandButton: ImageButton
    
    @Mock
    private lateinit var clipboardManager: ClipboardManager
    
    @Mock
    private lateinit var textToSpeech: TextToSpeech
    
    @Mock
    private lateinit var glideRequestManager: RequestManager
    
    @Spy
    private lateinit var adapter: ChatMessageAdapter
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        whenever(context.getSystemService(Context.CLIPBOARD_SERVICE)).thenReturn(clipboardManager)
        whenever(context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).thenReturn(layoutInflater)
        
        // Set up view inflation
        whenever(layoutInflater.inflate(R.layout.item_user_message, viewGroup, false)).thenReturn(userMessageView)
        whenever(layoutInflater.inflate(R.layout.item_assistant_message, viewGroup, false)).thenReturn(assistantMessageView)
        whenever(layoutInflater.inflate(R.layout.item_media_message, viewGroup, false)).thenReturn(mediaMessageView)
        
        // Set up view finding
        whenever(userMessageView.findViewById<TextView>(R.id.userMessageText)).thenReturn(textView)
        whenever(userMessageView.findViewById<ImageButton>(R.id.copyButton)).thenReturn(copyButton)
        whenever(userMessageView.findViewById<ImageButton>(R.id.speakerButton)).thenReturn(speakerButton)
        
        whenever(assistantMessageView.findViewById<TextView>(R.id.assistantMessageText)).thenReturn(textView)
        whenever(assistantMessageView.findViewById<ImageButton>(R.id.copyButton)).thenReturn(copyButton)
        whenever(assistantMessageView.findViewById<ImageButton>(R.id.speakerButton)).thenReturn(speakerButton)
        
        whenever(mediaMessageView.findViewById<ImageView>(R.id.mediaImage)).thenReturn(mediaImageView)
        whenever(mediaMessageView.findViewById<ImageButton>(R.id.expandButton)).thenReturn(expandButton)
        
        // Initialize adapter
        adapter = ChatMessageAdapter(context, glideRequestManager)
    }
    
    @Test
    fun `getItemViewType returns correct view type for message types`() {
        // Arrange
        val userMessage = MessageFactory.createUserMessage("User message")
        val assistantMessage = MessageFactory.createAssistantMessage("Assistant message")
        val mediaMessage = MessageFactory.createMediaMessage("/path/to/image.jpg")
        
        adapter.updateMessages(listOf(userMessage, assistantMessage, mediaMessage))
        
        // Act & Assert
        assert(adapter.getItemViewType(0) == ChatMessageAdapter.VIEW_TYPE_USER)
        assert(adapter.getItemViewType(1) == ChatMessageAdapter.VIEW_TYPE_ASSISTANT)
        assert(adapter.getItemViewType(2) == ChatMessageAdapter.VIEW_TYPE_MEDIA)
    }
    
    @Test
    fun `onCreateViewHolder creates correct view holder for each type`() {
        // Act
        val userViewHolder = adapter.onCreateViewHolder(viewGroup, ChatMessageAdapter.VIEW_TYPE_USER)
        val assistantViewHolder = adapter.onCreateViewHolder(viewGroup, ChatMessageAdapter.VIEW_TYPE_ASSISTANT)
        val mediaViewHolder = adapter.onCreateViewHolder(viewGroup, ChatMessageAdapter.VIEW_TYPE_MEDIA)
        
        // Assert
        assert(userViewHolder is ChatMessageAdapter.UserMessageViewHolder)
        assert(assistantViewHolder is ChatMessageAdapter.AssistantMessageViewHolder)
        assert(mediaViewHolder is ChatMessageAdapter.MediaMessageViewHolder)
    }
    
    @Test
    fun `copy button captures text when clicked`() {
        // Arrange
        val userMessage = MessageFactory.createUserMessage("Test message")
        adapter.updateMessages(listOf(userMessage))
        
        val viewHolder = ChatMessageAdapter.UserMessageViewHolder(userMessageView)
        
        // Mock button click listener capture
        val clickListenerCaptor = argumentCaptor<View.OnClickListener>()
        verify(copyButton).setOnClickListener(clickListenerCaptor.capture())
        
        // Act
        clickListenerCaptor.firstValue.onClick(copyButton)
        
        // Assert
        verify(clipboardManager).setPrimaryClip(any())
    }
    
    @Test
    fun `speaker button triggers text to speech when clicked`() {
        // Arrange
        adapter.setTextToSpeech(textToSpeech)
        val assistantMessage = MessageFactory.createAssistantMessage("Test message")
        adapter.updateMessages(listOf(assistantMessage))
        
        val viewHolder = ChatMessageAdapter.AssistantMessageViewHolder(assistantMessageView)
        
        // Mock button click listener capture
        val clickListenerCaptor = argumentCaptor<View.OnClickListener>()
        verify(speakerButton).setOnClickListener(clickListenerCaptor.capture())
        
        // Act
        clickListenerCaptor.firstValue.onClick(speakerButton)
        
        // Assert
        verify(textToSpeech).speak(eq("Test message"), any(), any(), any())
    }
    
    @Test
    fun `expand button triggers fullscreen image when clicked`() {
        // Arrange
        val mediaMessage = MessageFactory.createMediaMessage("/path/to/image.jpg")
        adapter.updateMessages(listOf(mediaMessage))
        
        val viewHolder = ChatMessageAdapter.MediaMessageViewHolder(mediaMessageView)
        
        // Mock button click listener capture
        val clickListenerCaptor = argumentCaptor<View.OnClickListener>()
        verify(expandButton).setOnClickListener(clickListenerCaptor.capture())
        
        // Act
        clickListenerCaptor.firstValue.onClick(expandButton)
        
        // Assert - verify Glide request is made
        verify(glideRequestManager).load(eq("/path/to/image.jpg"))
    }
    
    // Helper functions
    private inline fun <reified T : Any> argumentCaptor() = org.mockito.kotlin.argumentCaptor<T>()
} 