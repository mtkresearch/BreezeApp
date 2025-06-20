package com.mtkresearch.breezeapp_kotlin.presentation.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.mtkresearch.breezeapp_kotlin.databinding.ItemChatMessageBinding
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseAdapter
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseViewHolder
import com.mtkresearch.breezeapp_kotlin.presentation.common.widget.MessageBubbleView
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessage

/**
 * 聊天訊息列表適配器
 * 
 * 功能特色:
 * - 使用MessageBubbleView顯示訊息氣泡
 * - 自動處理用戶和AI訊息的不同樣式
 * - 支援訊息狀態變化動畫
 * - 提供訊息互動回調 (語音播放、點讚、重試等)
 * - DiffUtil自動計算列表差異，提供流暢動畫
 */
class MessageAdapter : BaseAdapter<ChatMessage, MessageAdapter.MessageViewHolder>(
    MessageDiffCallback()
) {

    /**
     * 訊息互動回調介面
     */
    interface MessageInteractionListener {
        fun onSpeakerClick(message: ChatMessage)
        fun onLikeClick(message: ChatMessage, isPositive: Boolean)
        fun onRetryClick(message: ChatMessage)
        fun onMessageLongClick(message: ChatMessage): Boolean
        fun onImageClick(message: ChatMessage, imageUrl: String)
    }

    private var interactionListener: MessageInteractionListener? = null

    /**
     * 設置訊息互動監聽器
     */
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

    /**
     * 訊息ViewHolder
     */
    inner class MessageViewHolder(
        private val binding: ItemChatMessageBinding
    ) : BaseViewHolder<ChatMessage>(binding.root) {

        override fun bind(item: ChatMessage, position: Int) {
            // 設置訊息氣泡
            setupMessageBubble(item)
            
            // 設置互動監聽器
            setupInteractionListeners(item)
            
            // 設置長按監聽器
            setupLongClickListener(item)
        }

        override fun bind(item: ChatMessage, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                // 處理部分更新，例如只更新訊息狀態
                val payload = payloads.first()
                when (payload) {
                    "STATE_UPDATE" -> updateMessageState(item)
                    "TEXT_UPDATE" -> updateMessageText(item)
                    else -> bind(item, position)
                }
            } else {
                bind(item, position)
            }
        }

        override fun onViewRecycled() {
            super.onViewRecycled()
            // 清理資源，例如停止動畫
            // 移除監聽器來避免記憶體洩漏
            binding.messageBubble.setOnSpeakerClickListener(null)
            binding.messageBubble.setOnLikeClickListener(null)
            binding.messageBubble.setOnRetryClickListener(null)
            binding.messageBubble.setOnLongClickListener(null)
        }

        /**
         * 設置訊息氣泡
         */
        private fun setupMessageBubble(message: ChatMessage) {
            val messageType = if (message.isFromUser) {
                MessageBubbleView.MessageType.USER
            } else {
                MessageBubbleView.MessageType.AI
            }

            val messageState = when (message.state) {
                ChatMessage.MessageState.NORMAL -> MessageBubbleView.MessageState.NORMAL
                ChatMessage.MessageState.SENDING -> MessageBubbleView.MessageState.LOADING
                ChatMessage.MessageState.LOADING -> MessageBubbleView.MessageState.LOADING
                ChatMessage.MessageState.ERROR -> MessageBubbleView.MessageState.ERROR
                ChatMessage.MessageState.TYPING -> MessageBubbleView.MessageState.TYPING
            }

            val showButtons = !message.isFromUser && message.state == ChatMessage.MessageState.NORMAL

            binding.messageBubble.setMessage(
                text = message.text,
                type = messageType,
                state = messageState,
                showButtons = showButtons,
                imageUrl = message.imageUrl
            )
        }

        /**
         * 設置互動監聽器
         */
        private fun setupInteractionListeners(message: ChatMessage) {
            interactionListener?.let { listener ->
                // 語音播放
                binding.messageBubble.setOnSpeakerClickListener {
                    listener.onSpeakerClick(message)
                }

                // 點讚/點踩
                binding.messageBubble.setOnLikeClickListener { isPositive ->
                    listener.onLikeClick(message, isPositive)
                }

                // 重試
                binding.messageBubble.setOnRetryClickListener {
                    listener.onRetryClick(message)
                }

                // 圖片點擊 (如果有圖片)
                message.imageUrl?.let { imageUrl ->
                    // TODO: 當MessageBubbleView支援圖片時，添加圖片點擊監聽
                    // binding.messageBubble.setOnImageClickListener {
                    //     listener.onImageClick(message, imageUrl)
                    // }
                }
            }
        }

        /**
         * 設置長按監聽器
         */
        private fun setupLongClickListener(message: ChatMessage) {
            binding.messageBubble.setOnLongClickListener {
                interactionListener?.onMessageLongClick(message) ?: false
            }
        }

        /**
         * 更新訊息狀態 (用於部分更新)
         */
        private fun updateMessageState(message: ChatMessage) {
            val messageState = when (message.state) {
                ChatMessage.MessageState.NORMAL -> MessageBubbleView.MessageState.NORMAL
                ChatMessage.MessageState.SENDING -> MessageBubbleView.MessageState.LOADING
                ChatMessage.MessageState.LOADING -> MessageBubbleView.MessageState.LOADING
                ChatMessage.MessageState.ERROR -> MessageBubbleView.MessageState.ERROR
                ChatMessage.MessageState.TYPING -> MessageBubbleView.MessageState.TYPING
            }
            
            // 更新訊息狀態 - 重新設置完整的訊息內容
            setupMessageBubble(message)
        }

        /**
         * 更新訊息文字 (用於部分更新)
         */
        private fun updateMessageText(message: ChatMessage) {
            binding.messageBubble.setMessage(
                text = message.text,
                type = if (message.isFromUser) MessageBubbleView.MessageType.USER else MessageBubbleView.MessageType.AI,
                state = when (message.state) {
                    ChatMessage.MessageState.NORMAL -> MessageBubbleView.MessageState.NORMAL
                    ChatMessage.MessageState.SENDING -> MessageBubbleView.MessageState.LOADING
                    ChatMessage.MessageState.LOADING -> MessageBubbleView.MessageState.LOADING
                    ChatMessage.MessageState.ERROR -> MessageBubbleView.MessageState.ERROR
                    ChatMessage.MessageState.TYPING -> MessageBubbleView.MessageState.TYPING
                },
                showButtons = !message.isFromUser && message.state == ChatMessage.MessageState.NORMAL,
                imageUrl = message.imageUrl
            )
        }
    }

    /**
     * 滾動到最新訊息
     */
    fun scrollToLatest(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        if (itemCount > 0) {
            recyclerView.smoothScrollToPosition(itemCount - 1)
        }
    }

    /**
     * 更新特定訊息的狀態
     */
    fun updateMessageState(messageId: String, newState: ChatMessage.MessageState) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(state = newState)
            submitList(currentList) {
                notifyItemChanged(index, "STATE_UPDATE")
            }
        }
    }

    /**
     * 更新特定訊息的文字內容
     */
    fun updateMessageText(messageId: String, newText: String) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(text = newText)
            submitList(currentList) {
                notifyItemChanged(index, "TEXT_UPDATE")
            }
        }
    }

    /**
     * 添加新訊息並滾動到最新位置
     */
    fun addMessage(
        message: ChatMessage, 
        recyclerView: androidx.recyclerview.widget.RecyclerView? = null
    ) {
        val newList = currentList.toMutableList()
        newList.add(message)
        submitList(newList) {
            recyclerView?.let { rv ->
                rv.smoothScrollToPosition(itemCount - 1)
            }
        }
    }

    /**
     * 批量添加訊息
     */
    fun addMessages(
        messages: List<ChatMessage>,
        scrollToLatest: Boolean = true,
        recyclerView: androidx.recyclerview.widget.RecyclerView? = null
    ) {
        val newList = currentList.toMutableList()
        newList.addAll(messages)
        submitList(newList) {
            if (scrollToLatest && recyclerView != null && itemCount > 0) {
                recyclerView.smoothScrollToPosition(itemCount - 1)
            }
        }
    }

    /**
     * 清空所有訊息
     */
    fun clearMessages() {
        submitList(emptyList())
    }

    /**
     * 獲取最後一條訊息
     */
    fun getLastMessage(): ChatMessage? {
        return if (itemCount > 0) getItem(itemCount - 1) else null
    }

    /**
     * 獲取最後一條用戶訊息
     */
    fun getLastUserMessage(): ChatMessage? {
        return currentList.lastOrNull { it.isFromUser }
    }

    /**
     * 獲取最後一條AI訊息
     */
    fun getLastAIMessage(): ChatMessage? {
        return currentList.lastOrNull { !it.isFromUser }
    }
}

/**
 * ChatMessage的DiffCallback
 */
private class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: ChatMessage, newItem: ChatMessage): Any? {
        return when {
            oldItem.state != newItem.state -> "STATE_UPDATE"
            oldItem.text != newItem.text -> "TEXT_UPDATE"
            else -> null
        }
    }
} 