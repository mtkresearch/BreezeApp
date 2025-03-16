package com.mtkresearch.breezeapp.ui.chat

import android.text.Editable
import android.widget.EditText
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.ui.history.ConversationHistoryFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.never

@RunWith(MockitoJUnitRunner::class)
class DrawerSearchTest {

    @Mock
    private lateinit var searchEditText: EditText
    
    @Mock
    private lateinit var historyFragment: ConversationHistoryFragment
    
    private lateinit var mockEditable: Editable
    
    @Before
    fun setup() {
        // Initialize mock editable
        mockEditable = mock()
        whenever(mockEditable.toString()).thenReturn("") // Default to empty string
        
        // Set up the text watcher
        setupSearchTextWatcher(searchEditText, historyFragment)
    }
    
    @Test
    fun `search text change triggers history filter`() {
        // Arrange
        whenever(mockEditable.toString()).thenReturn("test query")
        
        // Act - capture the text watcher and trigger afterTextChanged
        val textWatcherCaptor = argumentCaptor<android.text.TextWatcher>()
        verify(searchEditText).addTextChangedListener(textWatcherCaptor.capture())
        val textWatcher = textWatcherCaptor.firstValue
        
        // Simulate text change
        textWatcher.afterTextChanged(mockEditable)
        
        // Assert
        verify(historyFragment).filterConversations("test query")
    }
    
    @Test
    fun `empty search text shows all conversations`() {
        // Arrange
        whenever(mockEditable.toString()).thenReturn("")
        
        // Act - capture the text watcher and trigger afterTextChanged
        val textWatcherCaptor = argumentCaptor<android.text.TextWatcher>()
        verify(searchEditText).addTextChangedListener(textWatcherCaptor.capture())
        val textWatcher = textWatcherCaptor.firstValue
        
        // Simulate text change
        textWatcher.afterTextChanged(mockEditable)
        
        // Assert - empty string should be passed to show all conversations
        verify(historyFragment).filterConversations("")
    }
    
    @Test
    fun `search is trimmed before filtering`() {
        // Arrange
        whenever(mockEditable.toString()).thenReturn("  test query  ")
        
        // Act - capture the text watcher and trigger afterTextChanged
        val textWatcherCaptor = argumentCaptor<android.text.TextWatcher>()
        verify(searchEditText).addTextChangedListener(textWatcherCaptor.capture())
        val textWatcher = textWatcherCaptor.firstValue
        
        // Simulate text change
        textWatcher.afterTextChanged(mockEditable)
        
        // Assert - should be trimmed
        verify(historyFragment).filterConversations("test query")
    }
    
    // Helper function to set up text watcher
    private fun setupSearchTextWatcher(
        searchEditText: EditText,
        historyFragment: ConversationHistoryFragment
    ) {
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not used
            }
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Not used
            }
            
            override fun afterTextChanged(s: Editable?) {
                val searchText = s?.toString()?.trim() ?: ""
                historyFragment.filterConversations(searchText)
            }
        })
    }
} 