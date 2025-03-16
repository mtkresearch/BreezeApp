package com.mtkresearch.breezeapp.ui.history

import android.os.Bundle
import android.util.Log
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
        
        // Only initialize a default repository if it hasn't been provided from outside
        if (!::repository.isInitialized) {
            repository = ConversationRepository()
        }
        
        // Observe saved conversations
        observeConversations()
    }
    
    private fun updateEmptyView(isEmpty: Boolean) {
        if (isEmpty) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyView.text = "No saved conversations yet\nUse the menu option to save the current conversation"
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    // Method to set repository from outside
    fun setRepository(conversationRepository: ConversationRepository) {
        this.repository = conversationRepository
        
        // If the view is already created, update the observations
        if (view != null && isAdded) {
            observeConversations()
        }
    }
    
    // Helper method to observe conversations from the repository
    private fun observeConversations() {
        viewLifecycleOwner.lifecycleScope.launch {
            // First, update the UI with current data immediately
            val currentConversations = repository.getSavedConversations()
            adapter.submitList(currentConversations)
            updateEmptyView(currentConversations.isEmpty())
            
            // Then set up the ongoing observation for future updates
            repository.savedConversations.collectLatest { conversations ->
                adapter.submitList(conversations)
                updateEmptyView(conversations.isEmpty())
            }
        }
    }
    
    // Method to filter conversations based on search query
    fun filterConversations(query: String) {
        Log.d("ConversationHistory", "Filtering conversations with query: '$query'")
        
        // If query is empty, just refresh to show all conversations
        if (query.isEmpty()) {
            Log.d("ConversationHistory", "Empty query, showing all conversations")
            refreshConversations()
            return
        }
        
        // Get current conversations to filter
        val allConversations = repository.getSavedConversations()
        
        // Filter conversations based on title or content
        val filteredList = allConversations.filter { conversation ->
            conversation.title.contains(query, ignoreCase = true) ||
                    conversation.previewText.contains(query, ignoreCase = true)
        }
        
        Log.d("ConversationHistory", "Found ${filteredList.size} conversations matching query")
        
        // Update UI with filtered results
        adapter.submitList(filteredList)
        updateEmptyView(filteredList.isEmpty())
    }
    
    // Method to refresh the conversation list
    fun refreshConversations() {
        // Get current list of all conversations directly
        val allConversations = repository.getSavedConversations()
        Log.d("ConversationHistory", "Refreshing conversations: found ${allConversations.size} conversations")
        
        // Update the UI
        adapter.submitList(allConversations)
        updateEmptyView(allConversations.isEmpty())
    }
} 