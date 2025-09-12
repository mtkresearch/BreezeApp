### **BreezeApp (iOS): UI/UX Ground Truth Specification**

**Version:** 3.2
**Audience:** UI/UX Designers, Frontend iOS Developers
**Purpose:** To provide a complete, self-contained blueprint for the user-facing `BreezeApp` application, from high-level vision to detailed UI/UX and feature requirements.

---

### **Part A: High-Level Project Vision**

#### **1.0 Vision & Mission**

**Vision:** To establish the **BreezeApp-engine** as the central, on-device AI hub for iOS. This engine is delivered and managed through **BreezeApp**, a feature-rich, standalone application that functions as the user's control panel for the engine.

**Mission:** To democratize on-device AI on iOS by creating a single, user-installable application (BreezeApp) that provides a powerful 'AI Brain' (the BreezeApp-engine) to the entire device.

#### **2.0 Core Product Goals & Philosophy**

*   **A Central AI Engine:** The **BreezeApp-engine** provides a core service to the device. It is installed via the **BreezeApp** application and, once configured, stands by to serve AI requests from other applications.
*   **Privacy-First, On-Device Processing:** The engine's primary function is to run AI tasks directly on the user's hardware.
*   **Radical Simplicity for Developers:** Third-party developers should need zero AI expertise. By integrating our simple SDK, they can add chat, voice, and vision features to their apps in minutes.
*   **User in Control:** The main BreezeApp application will act as the "control panel" for the engine.
*   **A Hybrid, Extensible "Runner" System:** The engine will be capable of routing AI requests to the best available "runner."

---

### **Part B: UI/UX-Specific Application Specification**

#### **3.0 Core Mission**

Your mission is to build the face of BreezeApp. You will create a beautiful, intuitive, and responsive application that is both a delight for users to interact with directly and the essential "control panel" for the entire on-device AI ecosystem we are building. Your work is to make the complexity of the underlying engine feel simple and magical.

#### **4.0 UI/UX Principles**

*   **Clarity Above All:** The user must always understand what is happening. Follow Apple's Human Interface Guidelines (HIG) to ensure the app feels like a first-class iOS citizen.
*   **Never Block the User:** The UI must remain fluid and responsive at all times. All AI operations must be asynchronous. Display non-intrusive loading indicators.
*   **Delight in the Details:** Use subtle animations, smooth transitions, and haptic feedback to make the app feel polished and alive.
*   **Accessibility First:** Ensure the app is fully accessible using VoiceOver, Dynamic Type, and other iOS accessibility features.

#### **5.0 Screen-by-Screen Feature Requirements**

##### **5.1 Main Chat View**

*   **Layout:** A standard message list view, clearly distinguishing between user prompts and AI responses.
*   **Streaming AI Responses:** The view must update smoothly as new text chunks arrive. A blinking cursor animation should be used at the end of the streaming text to indicate that more text is coming.
*   **Markdown Support:** AI responses should be rendered with basic Markdown (bold, code, lists).
*   **Message Actions:** Each AI message bubble must have easily accessible "Copy," "Share," and "Read Aloud" (TTS) buttons.
*   **Error Handling:** If an AI request fails, a distinct error message bubble should be displayed. It should contain a user-friendly error message and a "Details" button to show technical information.

##### **5.2 Settings: Engine Control Center**

This entire section of the application serves as the main user interface for the `BreezeApp-engine`. It must handle being launched directly from third-party apps via the `breezeapp://settings` URL scheme. Your team is responsible for implementing the deep-link handling to present the correct screen.

*   **Screen 1: Model Management:** When a model is downloading, the UI must show granular progress (percentage and MB). The download must be pausable and resumable and should continue in the background using `URLSession`.

*   **Screen 2: Runner Defaults:** When a user selects a runner, the UI should display a brief description of its trade-offs (e.g., "On-Device: Fast and private, but less powerful," or "Cloud: Very powerful, but requires internet and may have costs").

*   **Screen 3: API Key Management:** When a user enters an API key, there must be a "Test" button that sends a simple request to validate the key before saving it to the **Keychain**. The result ("Success" or "Authentication Failed") should be shown to the user.

#### **6.0 Interaction with `EdgeAIKit`**

*   The `ViewModel` layer will be the sole point of contact with the `EdgeAIKit`.
*   ViewModels must manage a detailed state, such as `enum ViewState { case idle; case waitingForResponse; case streamingResponse(partialText: String); case error(message: String) }`. The View will update its appearance based on this state.

**Example ViewModel Snippet:**

```swift
class ChatViewModel: ObservableObject {
    @Published var viewState: ViewState = .idle

    @MainActor
    func sendMessage(prompt: String) async {
        viewState = .waitingForResponse
        // ...
        do {
            let response = try await EdgeAI.chat(prompt: prompt)
            viewState = .idle // Or display full response
        } catch {
            viewState = .error(message: error.localizedDescription)
        }
    }
}
```

#### **7.0 Visual Design Responsibility for Shared Components**

To provide a consistent brand experience, the `EdgeAIKit` will include a status view (`EdgeAIStatusView`) inspired by the `BreezeApp`'s design. Your team is responsible for providing the **formal design and animation specifications** for this component to the engine team. This spec should include:

1.  Animation curves and timings for the "breathing" effect.
2.  Color palettes for different states (e.g., processing, error, idle).
3.  Recommendations for placement and usage for third-party developers.
