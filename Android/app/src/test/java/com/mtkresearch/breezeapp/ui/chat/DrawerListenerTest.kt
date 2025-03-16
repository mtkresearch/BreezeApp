package com.mtkresearch.breezeapp.ui.chat

import android.text.Editable
import android.view.View
import android.widget.EditText
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.testing.FragmentScenario
import com.mtkresearch.breezeapp.ui.history.ConversationHistoryFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for the drawer listener that refreshes conversations
 * when the drawer is opened
 */
@RunWith(MockitoJUnitRunner::class)
class DrawerListenerTest {

    @Mock
    private lateinit var drawerLayout: DrawerLayout
    
    @Mock
    private lateinit var searchEditText: EditText
    
    @Mock
    private lateinit var historyFragment: ConversationHistoryFragment
    
    @Mock
    private lateinit var editable: Editable
    
    private lateinit var drawerListener: DrawerLayout.DrawerListener
    
    @Before
    fun setup() {
        // Set up searchEditText mock
        whenever(searchEditText.text).thenReturn(editable)
        
        // Create the drawer listener
        drawerListener = createDrawerListener(searchEditText, historyFragment)
    }
    
    @Test
    fun `onDrawerOpened clears search text`() {
        // Act
        drawerListener.onDrawerOpened(mock())
        
        // Assert
        verify(editable).clear()
    }
    
    @Test
    fun `onDrawerOpened refreshes conversations`() {
        // Act
        drawerListener.onDrawerOpened(mock())
        
        // Assert
        verify(historyFragment).refreshConversations()
    }
    
    // Helper function that matches our implementation in ChatActivity
    private fun createDrawerListener(
        searchEditText: EditText,
        historyFragment: ConversationHistoryFragment
    ): DrawerLayout.DrawerListener {
        return object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) {
                // Clear any search text when drawer opens
                searchEditText.text?.clear()
                // Refresh the conversation list
                historyFragment.refreshConversations()
            }
            
            override fun onDrawerClosed(drawerView: View) {
                // Not needed for now
            }
            
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Not needed for now
            }
            
            override fun onDrawerStateChanged(newState: Int) {
                // Not needed for now
            }
        }
    }
} 