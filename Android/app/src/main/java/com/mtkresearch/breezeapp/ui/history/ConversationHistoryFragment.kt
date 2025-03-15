package com.mtkresearch.breezeapp.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.data.models.SavedConversation
import com.mtkresearch.breezeapp.data.repository.ConversationRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConversationHistoryFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: ConversationHistoryAdapter
    private lateinit var repository: ConversationRepository
    
    // Callbacks for history item interactions
    var onConversationSelected: ((SavedConversation) -> Unit)? = null
    var onConversationDeleted: ((SavedConversation) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_conversation_history, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        recyclerView = view.findViewById(R.id.rvConversationHistory)
        emptyView = view.findViewById(R.id.tvEmptyHistory)
        
        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ConversationHistoryAdapter(
            onConversationClicked = { conversation ->
                onConversationSelected?.invoke(conversation)
            },
            onDeleteClicked = { conversation ->
                repository.deleteSavedConversation(conversation.id)
                onConversationDeleted?.invoke(conversation)
            }
        )
        recyclerView.adapter = adapter
        
        // Get repository instance
        repository = ConversationRepository()
        
        // Observe saved conversations
        viewLifecycleOwner.lifecycleScope.launch {
            repository.savedConversations.collectLatest { conversations ->
                adapter.submitList(conversations)
                updateEmptyView(conversations.isEmpty())
            }
        }
    }
    
    private fun updateEmptyView(isEmpty: Boolean) {
        if (isEmpty) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    // Method to set repository from outside
    fun setRepository(conversationRepository: ConversationRepository) {
        this.repository = conversationRepository
    }
} 