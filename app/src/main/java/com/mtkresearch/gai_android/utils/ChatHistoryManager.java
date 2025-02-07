package com.mtkresearch.gai_android.utils;

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
        String id = UUID.randomUUID().toString();
        ChatHistory history = new ChatHistory(id, title, new Date(), messages);
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
} 