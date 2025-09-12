### **BreezeApp-engine (iOS): Ground Truth Specification**

**Version:** 3.3
**Audience:** Core AI/ML Engineers, Backend-focused iOS Developers
**Purpose:** To provide a complete, self-contained blueprint for building the core `BreezeApp-engine`, from high-level vision to detailed technical requirements.

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

### **Part B: Engine-Specific Technical Specification**

**PoC Strategy:** For the initial Proof of Concept (PoC), the primary goal is to validate the overall architecture. Development should prioritize modularity but avoid over-engineering. The first phase will focus exclusively on implementing a suite of **Mock Runners** to solidify API contracts and data flow before introducing real model integrations.

#### **3.0 Core Mission**

Your mission is to build the "AI Brain" for iOS. This engine must be a robust, efficient, and extensible service that powers not only our own `BreezeApp` but any third-party app that uses the `EdgeAIKit`. The engine is everything *except* the UI.

#### **4.0 Engine Responsibilities**

1.  **Request Orchestration:** Serve as the single entry point for all AI requests.
2.  **Runner Management:** Maintain a registry of all available AI "Runners" and manage their lifecycle.
3.  **Intelligent Routing:** Select the optimal runner based on user configuration and request parameters.
4.  **Configuration Service:** Manage and apply a three-tiered configuration system for all AI operations. The configuration must be resolved using a 3-layer override system: (1) **Runner Defaults** (hardcoded in the runner), (2) **Engine Settings** (set by the user in the `BreezeApp` UI and persisted), and (3) **Client Overrides** (per-request parameters sent via `EdgeAIKit`).
5.  **Model Lifecycle Management:** Handle the on-demand downloading, background compilation, secure storage, and versioning of on-device AI models. Models should be downloaded to a shared location accessible via an **App Group** container, using background `URLSession` tasks.

#### **5.0 The Runner Protocol & Data Contracts**

The foundation of the engine's extensibility is the `Runner` protocol.

```swift
// The universal contract for any AI task processor
public protocol Runner {
    var id: String { get }
    var supportedCapabilities: [AICapability] { get }
    func canRun(request: InferenceRequest) -> Bool
    func load() async throws
    func run(request: InferenceRequest) async throws -> InferenceResult
    func unload()
}
```

**Data Contracts (Detailed):**

*   **`InferenceRequest`:** `struct InferenceRequest { let requestId: UUID; let capability: AICapability; let modelId: String?; let prompt: String?; let imageData: Data?; let configuration: InferenceConfig; let streamingCallback: ((InferenceChunk) -> Void)? }`
*   **`InferenceResult`:** `struct InferenceResult { let requestId: UUID; let output: [String: Any]; let metadata: [String: Any]; let error: Error? }`
*   **`InferenceChunk`:** A piece of a streaming response, containing a partial output and status.

#### **6.0 Required Initial Runners (PoC Phase)**

To fast-track architectural validation, the initial development will focus on creating mock runners.

1.  **`MockLLMRunner`:** Simulates streaming and non-streaming text generation.
2.  **`MockASRRunner`:** Simulates speech-to-text.
3.  **`MockTTSRunner`:** Simulates text-to-speech.
4.  **Other Mocks (`VLM`, `Guardian`):** Basic mock implementations to ensure the engine's routing logic is comprehensive.

#### **7.0 Inter-App Communication API (URL Scheme)**

The engine's public-facing API is its URL scheme handler, which must be implemented within the main `BreezeApp`.

*   **Scheme:** `breezeapp://`
*   **API Endpoint:** `breezeapp://api/{capability}`
    *   **Purpose:** To receive and process AI requests from third-party apps.
    *   **Parameters:** Must accept a Base64-encoded JSON `InferenceRequest` and a `callback` URL.
    *   **Security:** The handler must validate the `sourceApplication` parameter provided by iOS during launch against a user-configurable list of approved third-party app bundle IDs to prevent misuse.
*   **Settings Endpoint:** `breezeapp://settings`
    *   **Purpose:** To deep-link directly to the Engine Settings UI within the `BreezeApp`.

#### **8.0 `EdgeAIKit` SDK Design Responsibilities**

This team is also responsible for developing the public-facing `EdgeAIKit` SDK.

1.  **Core Functionality:** The SDK must handle the logic of checking if `BreezeApp` is installed and routing requests either to the `breezeapp://` URL scheme or to a fallback runner.
2.  **Callback Handling:** The SDK must manage the complexity of app-switching. It will register a custom URL scheme in the host app's `Info.plist` (with clear instructions for the developer) and use an `AsyncStream` or continuation to resume the original `EdgeAI.chat()` call when the callback URL is received.
3.  **Fallback Mechanism:** The SDK must include a default, cloud-based `APIRunner` as a fallback.
4.  **Settings Deep-Link:** The SDK must provide a simple function, `EdgeAI.presentSettings()`, which constructs and calls the `breezeapp://settings` URL.
5.  **Status UI Component:** The SDK must provide a standard, embeddable SwiftUI View (e.g., `EdgeAIStatusView`). This component should be implemented as a `ViewModifier` for ease of use. The SDK will manage an internal `ObservableObject` that publishes the processing state, which this view will subscribe to, automatically showing animations (like a breathing border) when a request is in progress.