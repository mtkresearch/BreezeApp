package com.mtkresearch.gai_android.utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.Serializable;

public class ChatHistory implements Serializable {
    private static final long serialVersionUID = 3731319695635184847L;
    private String id;
    private String title;
    private Date date;
    private List<ChatMessage> messages;
    private int promptId;
    private boolean isActive;

    public ChatHistory(String id, String title, Date date, List<ChatMessage> messages) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.messages = new ArrayList<>(messages);
        this.promptId = messages.isEmpty() ? 0 : messages.get(0).getPromptId();
        this.isActive = false;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Date getDate() {
        return date;
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void addMessage(ChatMessage message) {
        message.setPromptId(promptId);
        messages.add(message);
    }

    public int getPromptId() {
        return promptId;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public boolean isActive() {
        return isActive;
    }

    public String getFirstUserMessage() {
        for (ChatMessage message : messages) {
            if (message.isUser() && message.hasText()) {
                return message.getText();
            }
        }
        return "";
    }

    public void updateLastMessage(String text) {
        if (!messages.isEmpty()) {
            messages.get(messages.size() - 1).updateText(text);
        }
    }

    public void removeLastMessage() {
        if (!messages.isEmpty()) {
            messages.remove(messages.size() - 1);
        }
    }

    public void updateMessages(List<ChatMessage> newMessages) {
        this.messages = new ArrayList<>(newMessages);
    }
} 