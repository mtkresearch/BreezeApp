package com.mtkresearch.breezeapp_UI.presentation.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.mtkresearch.breezeapp_UI.databinding.ItemChatMessageBinding
import com.mtkresearch.breezeapp_UI.presentation.common.base.BaseViewHolder
import com.mtkresearch.breezeapp_UI.presentation.common.widget.MessageBubbleView
import com.mtkresearch.breezeapp_UI.domain.model.ChatMessage
import com.mtkresearch.breezeapp_UI.domain.model.MessageAuthor

/**
 * 聊天訊息列表適配器 (v2.1 - 使用 ListAdapter + Domain Model)
 * 
 * 功能特色:
 * - 基於 ListAdapter，自動處理 DiffUtil，效能更佳。
 * - 直接使用 Domain 層的 ChatMessage 模型。
 * - 根據 MessageAuthor 和 isLoading 屬性決定訊息樣式。
 * - 簡化了監聽器和更新邏輯。
 */
class MessageAdapter : ListAdapter<ChatMessage, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    interface MessageInteractionListener {
        fun onMessageInteraction(message: ChatMessage)
    }

    private var interactionListener: MessageInteractionListener? = null

    fun setMessageInteractionListener(listener: MessageInteractionListener?) {
        this.interactionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getLastMessage(): ChatMessage? {
        return if (itemCount > 0) getItem(itemCount - 1) else null
    }

    inner class MessageViewHolder(
        private val binding: ItemChatMessageBinding
    ) : BaseViewHolder<ChatMessage>(binding.root) {

        override fun bind(item: ChatMessage) {
            setupMessageBubble(item)
            setupInteractionListeners(item)
        }

        private fun setupMessageBubble(message: ChatMessage) {
            val messageState = when {
                message.isLoading -> MessageBubbleView.MessageState.TYPING
                message.author == MessageAuthor.SYSTEM_ERROR -> MessageBubbleView.MessageState.ERROR
                else -> MessageBubbleView.MessageState.NORMAL
            }

            val showButtons = message.author == MessageAuthor.AI && !message.isLoading

            val layoutParams = binding.messageBubble.layoutParams as android.widget.FrameLayout.LayoutParams
            layoutParams.gravity = if (message.author == MessageAuthor.USER) {
                android.view.Gravity.END
            } else {
                android.view.Gravity.START
            }
            binding.messageBubble.layoutParams = layoutParams

            binding.messageBubble.setMessage(
                text = message.content,
                author = message.author,
                state = messageState,
                showButtons = showButtons
            )
        }

        private fun setupInteractionListeners(message: ChatMessage) {
            interactionListener?.let { listener ->
                // A single listener for all interactions on the bubble
                binding.messageBubble.setOnClickListener {
                    listener.onMessageInteraction(message)
                }
                binding.messageBubble.setOnLongClickListener {
                    listener.onMessageInteraction(message)
                    true
                }
                binding.messageBubble.setOnRetryClickListener {
                    listener.onMessageInteraction(message)
            }
        }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem == newItem
        }
    }
} 