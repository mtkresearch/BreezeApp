package com.mtkresearch.breezeapp.utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.Serializable;

public class ChatHistory implements Serializable {
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

    public void updateMessages(List<ChatMessage> newMessages) {
        this.messages = new ArrayList<>(newMessages);
    }
} 