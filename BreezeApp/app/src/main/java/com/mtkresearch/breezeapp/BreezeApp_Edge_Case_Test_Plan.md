### **BreezeApp - Edge Case & Stability Test Plan**

#### **Objective:**
To identify critical bugs, race conditions, and resource handling failures that could cause the BreezeApp application to crash or enter an unrecoverable state. This plan specifically avoids "happy path" scenarios.

#### **Target Areas (inferred from code structure):**
1.  **Core Engine & Foreground Service:** The app runs a persistent service (`EngineServiceDebugExample`, `ForegroundServiceDebugger`). This is the most critical component to stress-test.
2.  **Real-time Chat & ASR/TTS:** The use cases `StreamingChatUseCase`, `AsrMicrophoneUseCase`, and `TtsUseCase` suggest real-time I/O and network activity, which are prone to failure.
3.  **System Permissions & Interruptions:** `OverlayPermissionManager` and microphone usage are sensitive and can be revoked by the user or interrupted by the OS at any time.
4.  **Configuration & State Management:** `AppSettings` and `RuntimeSettings` can be changed at any time. We must ensure these changes don't corrupt the app's state.

---

### **Test Cases:**

#### **1. Core Engine & Foreground Service Integrity**

| Test Case ID | Description | Expected Behavior | Potential Failure |
| :--- | :--- | :--- | :--- |
| **ES-01** | **Service Starvation & Recovery:** With the app in the background, enable "Don't keep activities" and limit background processes to "No background processes" in Android Developer Options. Then, open several memory-intensive apps (like a game or map) to force the system to kill the BreezeApp service. Re-open BreezeApp. | The app should restart its service gracefully, either restoring its last state or resetting to a clean initial state without crashing. | **Crash on launch (NullPointerException)** if the UI tries to bind to a dead service. **ANR** if the service restart logic blocks the main thread. |
| **ES-02** | **Rapid Service Restart:** From the app's debug screen or via ADB, rapidly start and stop the foreground service 10-20 times in quick succession. | The app should handle the rapid requests without deadlocking or crashing. The final state should be consistent (either on or off). | **Crash (IllegalStateException)** if trying to start an already-started service or stop a non-existent one. **Resource leaks** if listeners/bindings are not cleaned up properly on each stop. |
| **ES-03** | **Induce Application Not Responding (ANR):** During an active connection (`ConnectionUseCase`), use a debug build to intentionally block the main thread for 10 seconds (e.g., `Thread.sleep(10000)` in a button click listener). | The OS should show the "Application Not Responding" dialog. After closing the dialog, the app should either crash or recover, but not remain frozen. | The app becomes permanently unresponsive. The background service may be left in an inconsistent state. |

#### **2. Chat, ASR/TTS, and Network Failure**

| Test Case ID | Description | Expected Behavior | Potential Failure |
| :--- | :--- | :--- | :--- |
| **CT-01** | **Cancellation Race Condition:** Start a microphone ASR (`AsrMicrophoneUseCase`) or a streaming chat request (`StreamingChatUseCase`). Immediately trigger `RequestCancellationUseCase` while the connection is still being established or the first packets are arriving. | The operation must be cancelled cleanly with no resource leaks. The UI should return to an idle state. | **Crash (NullPointerException, IllegalStateException)** if the cancellation logic tries to access resources that haven't been initialized yet. **Orphaned threads/coroutines** that continue running in the background. |
| **CT-02** | **Network Degradation:** While a streaming chat is active, use a network proxy tool to simulate extreme network conditions: 1) High latency (>2000ms), 2) 0% packet success, 3) Sudden switch from Wi-Fi to cellular and back. | The UI must not freeze. A loading indicator or error message (`ErrorView`) should be displayed. The `ConnectionState` should update accurately. When the network returns, it should attempt to reconnect gracefully. | **ANR** if network operations are blocking the main thread. **Crash** due to unhandled network exceptions (`TimeoutException`, `UnknownHostException`). **Stuck loading state** (`LoadingView`) that never resolves. |
| **CT-03** | **Concurrent I/O Operations:** Simultaneously trigger a TTS playback (`TtsUseCase`) of a long sentence and start microphone ASR (`AsrMicrophoneUseCase`). | The app should handle audio focus contention gracefully. One operation should pause or fail, but the app must not crash. The UI should reflect the state of each operation. | **Crash** due to conflicts in accessing audio hardware or media players. Unpredictable behavior where both operations try to run at once, producing garbled audio. |

#### **3. Permissions & System Interruptions**

| Test Case ID | Description | Expected Behavior | Potential Failure |
| :--- | :--- | :--- | :--- |
| **PS-01** | **Permission Revocation (Mid-flight):** 1. Start microphone ASR. 2. While it's actively listening, go to App Settings and revoke the Microphone permission. 3. Return to the app. | The app must not crash. The ASR operation should fail immediately with a clear error message, and the app should prompt the user to re-enable the permission. | **Crash (SecurityException)** because the app continues trying to access the microphone after permission is lost. |
| **PS-02** | **Overlay Permission Revocation:** 1. Grant the overlay permission and trigger the UI that uses it. 2. While the overlay is visible, go to App Settings and revoke the "Appear on top" permission. 3. Return to the app. | The app must not crash. The overlay should be dismissed, and the feature should be disabled with a message explaining why. | **Crash (WindowManager$BadTokenException)** if it tries to update an overlay window whose permission has been revoked. |
| **PS-03** | **System Interruption (Call/Alarm):** While ASR is active or TTS is playing, simulate an incoming phone call or trigger an alarm using ADB. After the call/alarm ends, observe the app's state. | The app should correctly pause its audio operation (losing audio focus) and resume it (or reset the state) after the interruption is over. | **Crash** if state restoration is not handled correctly. **Permanent loss of function** (e.g., ASR never listens again until app restart). |

---

#### **4. Lifecycle and Configuration Change Robustness**

**Objective:** To ensure the app maintains its state and avoids crashes or resource leaks during framework-initiated Activity/Fragment recreation. These tests are critical because they simulate common user actions.

| Test Case ID | Description | Expected Behavior | Potential Failure |
| :--- | :--- | :--- | :--- |
| **LC-01** | **Configuration Change During Active Operation (Screen Rotation):** 1. Start an active, long-running operation (e.g., Microphone ASR or Streaming Chat). 2. While the operation is in progress, **rotate the device** from portrait to landscape and back. | The operation should continue seamlessly. The UI must be restored correctly in the new orientation without losing data (e.g., chat history, connection status). No memory leaks from old Activity instances. | **Crash (IllegalStateException, NullPointerException)** if the ViewModel or service connection is not handled correctly across recreation. **Operation silently terminates.** **Duplicate operations** are created, causing resource contention (e.g., two mic listeners). |
| **LC-02** | **Configuration Change During State Transition (Runtime Settings):** 1. Navigate to the `RuntimeSettingsFragment`. 2. Change a parameter (e.g., `UpdateFontSizeUseCase` or another runtime parameter). 3. **Immediately rotate the screen** before leaving the settings page. 4. Navigate back to the main chat screen. | The setting change must be correctly saved and applied. The app should reflect the new setting without crashing or requiring a manual restart. | **State loss:** The setting change is not persisted. **Crash** on returning to the chat screen if the service or UI components cannot handle the new configuration dynamically. |
| **LC-03** | **State Preservation in UI (In-flight Data):** 1. In the `ChatFragment`, type a message into the input field but **do not send it**. 2. Rotate the device or put the app in the background and bring it back. | The typed, unsent message must still be present in the input field. The scroll position of the message list should be preserved. | **Data loss:** The input text is cleared, frustrating the user. The chat view scrolls back to the top/bottom. |
| **LC-04** | **Multi-Window Mode Stress Test:** 1. With the app open, enter Android's multi-window (split-screen) mode. 2. While in split-screen, resize the app's window multiple times. 3. Trigger a core function like ASR or TTS. | The UI should adapt to the new dimensions without crashing or rendering incorrectly. Core functions remain operational. | **Crash** due to layout errors or improper handling of dynamic resizing. UI elements become unusable or distorted. |