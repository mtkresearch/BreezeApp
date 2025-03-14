package com.mtkresearch.breezeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.mtkresearch.breezeapp.ui.chat.ChatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Find the Start Chat button
        val startChatButton = findViewById<Button>(R.id.startChatButton)
        
        // Set click listener to navigate to the chat screen
        startChatButton.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }
}