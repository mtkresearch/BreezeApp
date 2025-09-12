### **BreezeApp-engine (iOS): Ground Truth Specification**

**Version:** 3.1
**Audience:** Core AI/ML Engineers, Backend-focused iOS Developers
**Purpose:** To provide a complete, self-contained blueprint for building the core `BreezeApp-engine`, from high-level vision to detailed technical requirements.

---

### **Part A: High-Level Project Vision**

#### **1.0 Vision & Mission**

**Vision:** To establish the **BreezeApp-engine** as the central, on-device AI hub for iOS. This engine is delivered and managed through **BreezeApp**, a feature-rich, standalone application that functions as the user's control panel for the engine.

**Mission:** To democratize on-device AI on iOS by creating a single, user-installable application (BreezeApp) that provides a powerful 'AI Brain' (the BreezeApp-engine) to the entire device.

#### **2.0 Core Product Goals & Philosophy**

*   **A Central AI Engine:** The **BreezeApp-engine** provides a core service to the device. It is installed via the **BreezeApp** application and, once configured, stands by to serve AI requests from other applications.
*   **Privacy-First, On-Device Processing:** The engine's primary function is to run AI tasks directly on the user's hardware. No data leaves the device unless a user explicitly chooses a cloud-based AI model.
*   **Radical Simplicity for Developers:** Third-party developers should need zero AI expertise. By integrating our simple SDK, they can add chat, voice, and vision features to their apps in minutes.
*   **User in Control:** The main BreezeApp application will act as the "control panel." Users can manage their AI models, view performance, and control which apps have access to the AI engine.
*   **A Hybrid, Extensible "Runner" System:** The engine will be capable of routing AI requests to the best available "runner," whether it's a hyper-efficient on-device model (using Apple's Neural Engine) or a powerful cloud-based API (like OpenRouter), giving users and developers maximum flexibility.

---

### **Part B: Engine-Specific Technical Specification**

**PoC Strategy:** For the initial Proof of Concept (PoC), the primary goal is to validate the overall architecture and ensure all components are connected correctly. Development should prioritize modularity but avoid over-engineering. To achieve this rapidly, the first phase will focus exclusively on implementing a suite of **Mock Runners**, similar to the Android project's `runner.mock` package. This will allow the API contracts and data flow to be solidified before introducing complex, real-world model integrations.

#### **3.0 Core Mission**

Your mission is to build the "AI Brain" for iOS. This engine must be a robust, efficient, and extensible service that powers not only our own `BreezeApp` but any third-party app that uses the `EdgeAIKit`. The engine is everything *except* the UI.

#### **4.0 Engine Responsibilities**

1.  **Request Orchestration:** Serve as the single entry point for all AI requests, whether they originate from the `BreezeApp` itself or a third-party app via the URL scheme.
2.  **Runner Management:** Maintain a registry of all available AI "Runners" and manage their lifecycle.
3.  **Intelligent Routing:** For each incoming request, select the optimal runner based on user configuration, model availability, and request parameters.
4.  **Configuration Service:** Manage and apply a three-tiered configuration system for all AI operations.
5.  **Model Lifecycle Management:** Handle the on-demand downloading, background compilation, secure storage, and versioning of on-device AI models.

#### **5.0 The Runner Protocol & Data Contracts**

The foundation of the engine's extensibility is the `Runner` protocol.

```swift
// The universal contract for any AI task processor
public protocol Runner {
    // A unique identifier, e.g., "CoreML-OnDevice-LLM"
    var id: String { get }

    // The capabilities this runner supports, e.g., [.chat, .vision]
    var supportedCapabilities: [AICapability] { get }

    // Called by the engine to see if this runner can handle a specific request
    func canRun(request: InferenceRequest) -> Bool

    // Prepares the runner for execution (e.g., loads a model into memory)
    // This should be idempotent.
    func load() async throws

    // The main execution function
    func run(request: InferenceRequest) async throws -> InferenceResult

    // Releases resources (e.g., unloads the model)
    func unload()
}
```

**Data Contracts:**

*   **`InferenceRequest`:** Must contain all data needed for any request:
    *   `capability`: `AICapability` (enum: `.chat`, `.tts`, etc.)
    *   `prompt`: `String`
    *   `imageData`: `Data?` (for VLM)
    *   `configuration`: `InferenceConfig` (e.g., temperature, max tokens)
    *   `streamingCallback`: `((InferenceChunk) -> Void)?` (for real-time responses)
*   **`InferenceResult`:** The object returned upon completion, containing the final output.
*   **`InferenceChunk`:** A piece of a streaming response.

#### **6.0 Required Initial Runners (PoC Phase)**

To fast-track architectural validation, the initial development will focus on creating mock runners that simulate the behavior of real AI models without the overhead of actual inference. These runners should be inspired by the existing Android mock implementations.

1.  **`MockLLMRunner`:**
    *   Simulates text generation for the `.chat` capability.
    *   Must support both a single, complete response and a streaming response (word-by-word).
    *   Should return pre-defined, canned responses based on keywords in the prompt.

2.  **`MockASRRunner`:**
    *   Simulates speech-to-text for the `.asr` capability.
    *   Accepts `Data` representing audio and returns a pre-defined string transcription.

3.  **`MockTTSRunner`:**
    *   Simulates text-to-speech for the `.tts` capability.
    *   Accepts a string and returns generated `Data` representing mock audio (e.g., a simple sine wave).

4.  **Other Mocks (`VLM`, `Guardian`):**
    *   Basic mock implementations for other key capabilities like `.vlm` and `.guardian` should also be created to ensure the engine's routing logic is comprehensive from day one.

#### **7.0 Inter-App Communication API (URL Scheme)**

The engine's public-facing API is its URL scheme handler.

*   **Scheme:** `breezeapp://`
*   **Endpoint Structure:** `breezeapp://api/{capability}` (e.g., `breezeapp://api/chat`)
*   **Required Query Parameters:**
    *   `request`: A URL-safe, Base64-encoded JSON representation of the `InferenceRequest` object.
    *   `callback`: The URL scheme of the calling app (e.g., `mycoolapp://`).
*   **Workflow:**
    1.  Receive the incoming URL.
    2.  Decode and validate the `InferenceRequest`.
    3.  Pass the request to the internal engine for processing.
    4.  Upon receiving a result (or error), construct a response object.
    5.  Base64-encode the JSON response and call the `callback` URL with the data (e.g., `mycoolapp://breeze-response?result=...`).