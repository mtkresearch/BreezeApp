package com.mtkresearch.breezeapp.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.data.models.ChatMessage
import com.mtkresearch.breezeapp.data.models.MediaType
import com.mtkresearch.breezeapp.data.models.MessageSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying chat messages in a RecyclerView
 */
class ChatMessageAdapter(
    private val onMediaClicked: ((ChatMessage) -> Unit)? = null,
    private val onTtsRequested: ((ChatMessage) -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_ASSISTANT = 1
        private const val VIEW_TYPE_SYSTEM = 2
        private const val VIEW_TYPE_MEDIA = 3
        private val TIME_FORMATTER = SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when {
            // If it's a user message with media but no text content, show it as a media message
            message.sender == MessageSender.USER && 
                message.mediaUri != null && 
                message.content.isBlank() -> VIEW_TYPE_MEDIA
            message.sender == MessageSender.USER -> VIEW_TYPE_USER
            message.sender == MessageSender.ASSISTANT -> VIEW_TYPE_ASSISTANT
            else -> VIEW_TYPE_SYSTEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                UserMessageViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_user_message, parent, false),
                    onTtsRequested,
                    onMediaClicked
                )
            }
            VIEW_TYPE_ASSISTANT -> {
                AssistantMessageViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_assistant_message, parent, false),
                    onTtsRequested
                )
            }
            VIEW_TYPE_MEDIA -> {
                MediaMessageViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_media_message, parent, false),
                    onMediaClicked
                )
            }
            else -> {
                SystemMessageViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_system_message, parent, false)
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(message)
            is MediaMessageViewHolder -> holder.bind(message)
            is SystemMessageViewHolder -> holder.bind(message)
        }
    }

    /**
     * ViewHolder for media-only messages
     */
    class MediaMessageViewHolder(
        itemView: View,
        private val onMediaClicked: ((ChatMessage) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val mediaImageView: ImageView = itemView.findViewById(R.id.mediaImage)
        private val expandButton: ImageButton = itemView.findViewById(R.id.expandButton)
        private var currentMessage: ChatMessage? = null
        
        init {
            mediaImageView.setOnClickListener {
                currentMessage?.let { message ->
                    onMediaClicked?.invoke(message)
                }
            }
            
            expandButton.setOnClickListener {
                currentMessage?.let { message ->
                    onMediaClicked?.invoke(message)
                }
            }
        }
        
        fun bind(message: ChatMessage) {
            currentMessage = message
            
            // Set the media image
            if (message.mediaUri != null && message.mediaType == MediaType.IMAGE) {
                mediaImageView.setImageURI(message.mediaUri)
            }
        }
    }

    /**
     * ViewHolder for user messages
     */
    class UserMessageViewHolder(
        itemView: View,
        private val onTtsRequested: ((ChatMessage) -> Unit)?,
        private val onMediaClicked: ((ChatMessage) -> Unit)? = null
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val messageText: TextView = itemView.findViewById(R.id.userMessageText)
        private val mediaImageView: ImageView? = itemView.findViewById(R.id.userMediaImage)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val speakerButton: ImageButton = itemView.findViewById(R.id.speakerButton)
        private var currentMessage: ChatMessage? = null
        
        init {
            mediaImageView?.setOnClickListener {
                currentMessage?.let { message ->
                    onMediaClicked?.invoke(message)
                }
            }
            
            copyButton.setOnClickListener {
                currentMessage?.let { message ->
                    copyToClipboard(message.content)
                }
            }
            
            speakerButton.setOnClickListener {
                currentMessage?.let { message ->
                    onTtsRequested?.invoke(message)
                }
            }
        }
        
        private fun copyToClipboard(text: String) {
            val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(itemView.context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        
        fun bind(message: ChatMessage) {
            currentMessage = message
            messageText.text = message.content
            
            // Handle media attachment if present
            if (message.mediaUri != null && message.mediaType == MediaType.IMAGE) {
                mediaImageView?.visibility = View.VISIBLE
                mediaImageView?.setImageURI(message.mediaUri)
            } else {
                mediaImageView?.visibility = View.GONE
            }
        }
    }

    /**
     * ViewHolder for assistant messages
     */
    class AssistantMessageViewHolder(
        itemView: View,
        private val onTtsRequested: ((ChatMessage) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val messageText: TextView = itemView.findViewById(R.id.assistantMessageText)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val speakerButton: ImageButton = itemView.findViewById(R.id.speakerButton)
        private var currentMessage: ChatMessage? = null
        
        init {
            copyButton.setOnClickListener {
                currentMessage?.let { message ->
                    copyToClipboard(message.content)
                }
            }
            
            speakerButton.setOnClickListener {
                currentMessage?.let { message ->
                    onTtsRequested?.invoke(message)
                }
            }
        }
        
        private fun copyToClipboard(text: String) {
            val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(itemView.context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        
        fun bind(message: ChatMessage) {
            currentMessage = message
            messageText.text = message.content
            
            // Handle processing state
            if (message.isProcessing) {
                // TODO: Add a typing indicator for processing messages
            }
            
            // Handle error state
            if (message.error) {
                messageText.setTextColor(itemView.context.getColor(android.R.color.holo_red_light))
            } else {
                messageText.setTextColor(itemView.context.getColor(android.R.color.black))
            }
        }
    }

    /**
     * ViewHolder for system messages
     */
    class SystemMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.systemMessageText)
        
        fun bind(message: ChatMessage) {
            messageText.text = message.content
        }
    }

    /**
     * DiffUtil callback for efficient RecyclerView updates
     */
    class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
} 