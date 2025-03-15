package com.mtkresearch.breezeapp.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.data.models.SavedConversation
import java.text.SimpleDateFormat
import java.util.Locale

class ConversationHistoryAdapter(
    private val onConversationClicked: (SavedConversation) -> Unit,
    private val onDeleteClicked: (SavedConversation) -> Unit
) : ListAdapter<SavedConversation, ConversationHistoryAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.tvConversationTitle)
        val dateTextView: TextView = view.findViewById(R.id.tvConversationDate)
        val deleteButton: ImageButton = view.findViewById(R.id.btnDeleteConversation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.titleTextView.text = conversation.title
        holder.dateTextView.text = formatDate(conversation.date)
        
        // Set click listeners
        holder.itemView.setOnClickListener { onConversationClicked(conversation) }
        holder.deleteButton.setOnClickListener { onDeleteClicked(conversation) }
    }
    
    private fun formatDate(date: java.util.Date): String {
        val formatter = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return formatter.format(date)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SavedConversation>() {
            override fun areItemsTheSame(oldItem: SavedConversation, newItem: SavedConversation): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: SavedConversation, newItem: SavedConversation): Boolean {
                return oldItem == newItem
            }
        }
    }
} 