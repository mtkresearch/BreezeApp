package com.mtkresearch.breezeapp.utils;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ChatHistoryManager {
    private static final String TAG = "ChatHistoryManager";
    private static final String HISTORY_DIR = "chat_histories";
    private final Context context;
    private ChatHistory currentActiveHistory;

    public ChatHistoryManager(Context context) {
        this.context = context;
        createHistoryDirectory();
    }

    private void createHistoryDirectory() {
        File directory = new File(context.getFilesDir(), HISTORY_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public ChatHistory createNewHistory(String title, List<ChatMessage> messages) {
        if (currentActiveHistory != null) {
            // Update existing history with new messages
            currentActiveHistory.setTitle(title);
            currentActiveHistory.updateMessages(messages);
            saveHistory(currentActiveHistory);
            return currentActiveHistory;
        }

        String id = UUID.randomUUID().toString();
        ChatHistory history = new ChatHistory(id, title, new Date(), messages);
        currentActiveHistory = history;
        saveHistory(history);
        return history;
    }

    public void saveHistory(ChatHistory history) {
        File file = new File(new File(context.getFilesDir(), HISTORY_DIR), history.getId() + ".dat");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(history);
        } catch (IOException e) {
            Log.e(TAG, "Error saving chat history", e);
        }
    }

    public List<ChatHistory> loadAllHistories() {
        List<ChatHistory> histories = new ArrayList<>();
        File directory = new File(context.getFilesDir(), HISTORY_DIR);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".dat"));
        
        if (files != null) {
            for (File file : files) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    ChatHistory history = (ChatHistory) ois.readObject();
                    histories.add(history);
                } catch (InvalidClassException e) {
                    // Handle version mismatch by deleting the corrupted file
                    Log.w(TAG, "Deleting incompatible chat history file: " + file.getName());
                    file.delete();
                } catch (IOException | ClassNotFoundException e) {
                    Log.e(TAG, "Error loading chat history from " + file.getName(), e);
                    // Delete corrupted files
                    file.delete();
                }
            }
        }
        
        return histories;
    }

    public void deleteHistory(String historyId) {
        File file = new File(new File(context.getFilesDir(), HISTORY_DIR), historyId + ".dat");
        if (file.exists()) {
            file.delete();
        }
    }

    public void setCurrentActiveHistory(ChatHistory history) {
        currentActiveHistory = history;
    }

    public ChatHistory getCurrentActiveHistory() {
        return currentActiveHistory;
    }

    public void clearCurrentActiveHistory() {
        currentActiveHistory = null;
    }

} 