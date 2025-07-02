# BreezeApp Refactoring Plan: A Contract-First, Test-Driven Approach

**Project:** BreezeApp Refactoring
**Goal:** Successfully decouple the UI (`breeze-app-ui`) from the AI services (`breeze-app-router`) into a robust, maintainable, and scalable dual-app architecture using a clean AIDL-based IPC mechanism. All development must adhere strictly to **MVVM + Clean Architecture (Use Cases)** principles.

**Update:** We have successfully implemented the `breeze-app-router-client` as a reference implementation and demonstration app. This client showcases best practices for integrating with the AI Router Service and serves as comprehensive documentation for third-party developers.

---

### **1. Overall Architecture & Guiding Principles**

#### **Module Boundaries**

The high-level architecture is composed of four main modules. The two applications (`breeze-app-ui` and `breeze-app-router`) are completely decoupled and only communicate through the shared `shared-contracts` module. Additionally, we've created a reference client implementation (`breeze-app-router-client`) to demonstrate integration best practices.

```mermaid
graph LR;
    subgraph "breeze-app-ui (Client App)"
        UI_COMPONENTS["- AIRouterClient<br/>- ViewModels<br/>- MockRouterService"]
    end

    subgraph "shared-contracts (AIDL & Models)"
        CONTRACTS["- IAIRouter.aidl<br/>- AIRequest.kt (Parcelable)"]
    end
    
    subgraph "breeze-app-router (Service App)"
        ROUTER_COMPONENTS["- AIRouterService<br/>- AI Engines<br/>- Runtime Settings"]
    end
    
    subgraph "breeze-app-router-client (Reference Implementation)"
        CLIENT_COMPONENTS["- MainViewModel<br/>- Integration Examples<br/>- Testing Tools"]
    end

    UI_COMPONENTS -- "depends on" --> CONTRACTS;
    ROUTER_COMPONENTS -- "depends on" --> CONTRACTS;
    CLIENT_COMPONENTS -- "depends on" --> CONTRACTS;
    CLIENT_COMPONENTS -- "connects to" --> ROUTER_COMPONENTS;
```

#### **Architectural Layers**

The application is divided into distinct layers, each with a clear responsibility. Communication flows from the UI down to the native layer and back.

**Presentation Layer (`breeze-app-ui` & `breeze-app-router-client`)**: Contains UI elements (Activities/Fragments) and ViewModels. It is responsible for displaying data and handling user interaction. It knows nothing about how data is fetched, only that it gets it from a Use Case.
**Domain Layer (`breeze-app-ui` & `shared-contracts`)**: Contains the business logic (Use Cases) and the core domain models (e.g., `AIRequest`, `AIResponse`). It defines the `Repository` interfaces that the presentation layer uses.
**Data Layer (`breeze-app-ui` & `breeze-app-router`)**: Contains the `Repository` implementations. Its job is to fetch data from one or more sources (the AI Router Service, a local database, etc.) and deliver it to the Domain layer. `AIRouterRepositoryImpl` in the UI app will handle the IPC communication.
**AI Engine Layer (`breeze-app-router`)**: The `AIRouterService` lives here. It orchestrates calls to various AI engines (LLM, VLM, ASR, etc.) based on the incoming requests from the Data Layer.
**Runtime Layer (`breeze-app-router`)**: Manages the execution backend (CPU/GPU/NPU) and model files, providing a generalized interface for the AI Engine layer.
**Native Layer (`breeze-app-router`)**: The core C++/JNI implementation of the AI models.

#### **Communication Flow (AIDL)**

```mermaid
sequenceDiagram
    participant UI as UI
    participant UC as UseCase
    participant Repo as AIRouterRepo
    participant Svc as AIRouterService
    participant AIM as AIEngineMgr
    participant Eng as AIEngine

    Note over UI, UC: breeze-app-ui 模組流程
    UI->>UC: 1. ViewModel 呼叫 UseCase (e.g., SendMessage)
    UC->>Repo: 2. UseCase 呼叫 Repository
    Repo->>Svc: 3. AIDL IPC 呼叫 sendMessage()

    Note over Svc, AIM: breeze-app-router 模組流程
    Svc->>Svc: 4. onTransact 處理 IPC 請求
    Svc->>AIM: 5. 委派給 AIEngineMgr
    AIM->>Eng: 6. 執行推論
    Eng-->>AIM: 7. 回傳推論結果
    AIM-->>Svc: 8. 回傳結果
    Svc-->>Repo: 9. AIDL listener 回傳 onResponse()

    Note over UI, UC: 回傳與畫面更新流程
    Repo-->>UC: 10. Repository 發出結果 (e.g., Flow)
    UC-->>UI: 11. UseCase 傳遞結果至 ViewModel
    UI->>UI: 12. ViewModel 更新 UI 狀態
```

#### **AI Router Architecture: Extensibility & Strategy**

To ensure the `breeze-app-router` is a flexible and future-proof platform, its internal architecture will be built on the **Strategy Design Pattern**. This allows us to add, remove, or switch out AI backends (runners) without altering the core service logic.

* **Core Components:**
  
  * **AIEngineManager:** The central coordinator within the router app. It receives an `AIRequest`, determines the required function (LLM, VLM, ASR, Guardrail, etc.), and delegates the task to the currently configured `Runner`.
  * **Runner Interface (`interface Runner<REQUEST, RESPONSE>`):** A generic interface that all runners for a specific AI function will implement. This enforces a common contract.
  * **Concrete Runners:** These are the specific implementations of the `Runner` interface. For each AI function, we can have multiple, swappable runners.
    * **LLM Runners:** `ExecuTorchLLMRunner`, `LlamaCppLLMRunner`, `MediaTekLLMRunner`, etc.
    * **ASR Runners:** `SystemASRRunner`, `SherpaOnnxASRRunner`, etc.
  * **Guardrail & Future Functions:** This design makes it trivial to add new capabilities like content safety checks. We would simply define a `GuardrailRunner` and implement it, and the `AIEngineManager` would be updated to call it in the processing pipeline.

* **"Mock-First" Development Principle:**
  
  * For initial development and integration testing (**Milestones 2-4**), we will exclusively use **`MockRunner`** implementations (e.g., `MockLLMRunner`, `MockASRRunner`).
  * These mocks will simulate the behavior of a real runner (e.g., return a pre-defined text stream, simulate a delay) but have no native dependencies.
  * **Benefit:** This decouples the UI team's development from the complex, time-consuming work of implementing the native runners, allowing for parallel workstreams and a much faster path to end-to-end integration testing.
  * **Performance Requirements for Mock Runners:**
    * **Response Time**: Mock responses should complete within 100-500ms to simulate realistic AI processing
    * **Streaming Latency**: Streaming responses should emit chunks every 50-200ms
    * **Memory Usage**: Each Mock Runner should consume < 50MB RAM when active
    * **Concurrent Sessions**: Support minimum 4 concurrent requests without performance degradation
  * **Thread Safety Requirements:**
    * **State Isolation**: Each request must be processed in isolation
    * **Resource Sharing**: Shared resources (e.g., predefined responses) must be thread-safe
    * **Lifecycle Management**: Load/unload operations must be atomic and thread-safe
    * **Error Propagation**: Errors must be correctly propagated across thread boundaries

#### **Core Principles for Robustness**

To build a production-quality system, we will adhere to the following core principles throughout development:

1. **Security:** The `AIRouterService` will be protected by a `signature`-level permission. This ensures that only applications signed with our key (i.e., `breeze-app-ui`) can bind to and communicate with the service, preventing unauthorized access.
2. **Threading:** All IPC calls must be handled off the main thread to prevent UI freezes (ANRs).
   * **Client-Side (`breeze-app-ui`):** Repository calls to the service binder will be dispatched to a background thread (`Dispatchers.IO`).
   * **Service-Side (`breeze-app-router`):** Long-running inference tasks within the service will be launched in a dedicated coroutine scope to avoid blocking the binder thread pool.
3. **Error Handling & Resilience:** The connection must be resilient to failures.
   * **Connection:** The client-side repository will implement a reconnection strategy (e.g., exponential backoff) if the service connection is lost unexpectedly.
   * **Calls:** All binder calls will be wrapped in `try-catch` blocks to handle `RemoteException`.
   * **Data:** The `AIResponse` object will be designed to carry either a success payload or a structured error, making failures an explicit part of the contract.
4. **Configuration:** The service will be configured via a dedicated `initialize` method in the AIDL interface, using a `Configuration` Parcelable to pass settings. This is more robust than passing data via the `startService` Intent.

---

### **2. The New Roadmap: Milestone-Driven Development**

We will follow a "contract-first" and "service-first" approach. Each milestone represents a stable, verifiable state of the project. **We will not proceed to the next milestone until all tasks in the current one are marked as "Done" and validated.** The core principle is to build and validate the `breeze-app-router` service as a standalone component *before* developing the UI client.

---

#### **Milestone 1: The Unbreakable Contract**

* **Objective:** Create a `shared-contracts` module that is self-contained, correct, and can be built independently. This is the single source of truth for all inter-app communication.

* **Implementation & Validation Tracking:**
  
  | Task                                                                     | Status       | Validation Method                            | Unit Test            |
  |:------------------------------------------------------------------------ |:------------ |:-------------------------------------------- |:-------------------- |
  | **1.1** Initialize `shared-contracts` module as an Android Library       | ✅ `Done`    | `build.gradle.kts` review                    | N/A                  |
  | **1.2** Enable `aidl` and `kotlin-parcelize` build features              | ✅ `Done`    | `build.gradle.kts` review                    | N/A                  |
  | **1.3** Define `Configuration.kt` Parcelable for service setup           | ✅ `Done`    | Code review (242 lines, comprehensive)       | `ParcelableTest`     |
  | **1.4** Define `AIRequest.kt` Parcelable with binary data support        | ✅ `Done`    | Code review (91 lines)                       | `ParcelableTest`     |
  | **1.5** Define `AIResponse.kt` Parcelable with error-state fields        | ✅ `Done`    | Code review (131 lines)                      | `ParcelableTest`     |
  | **1.6** Define `BinaryData.kt` Parcelable for multimedia support         | ✅ `Done`    | Code review (90 lines)                       | `ParcelableTest`     |
  | **1.7** Define `IAIRouterService.aidl` with `initialize` & `sendMessage` | ✅ `Done`    | Code review (58 lines, complete interface)   | `ContractSyntaxTest` |
  | **1.8** Define `IAIRouterListener.aidl` for callbacks                    | ✅ `Done`    | Code review (15 lines)                       | `ContractSyntaxTest` |
  | **1.9** Implement comprehensive test suite                               | ✅ `Done`    | `ContractSyntaxTest` + `ParcelableTest`      | All tests pass       |
  | **1.10** **Build & Verify Module**                                       | ✅ `Done`    | `./gradlew :shared-contracts:build` succeeds | All tests pass       |
  | **1.11** Create comprehensive API documentation                          | ✅ `Done`    | `docs/api.md` (543 lines)                    | N/A                  |

---

#### **Milestone 2: The Standalone Router Service**

* **Objective:** Implement a secure, standalone `AIRouterService` in `breeze-app-router` that contains all necessary business logic, using mock runners for initial validation.

* **Implementation & Validation Tracking:**
  
  | Task                                                                        | Status       | Validation Method                                                        | Unit Test              |
  |:--------------------------------------------------------------------------- |:------------ |:------------------------------------------------------------------------ |:---------------------- |
  | **2.1** Define `signature`-level permission in `breeze-app-router` Manifest | ✅ `Done`    | Manifest review (permission declared)                                    | N/A                    |
  | **2.2** Add `:shared-contracts` dependency to `breeze-app-router`           | ✅ `Done`    | `build.gradle.kts` review                                                | N/A                    |
  | **2.3** Implement `AIRouterService.kt` with comprehensive AIDL stub         | ✅ `Done`    | Code review (196 lines, full implementation)                             | `AIRouterServiceTest`  |
  | **2.4** Enforce signature permission in service's `onBind` method           | ✅ `Done`    | Code review (permission check implemented)                               | `SecurityTest`         |
  | **2.5** Implement threading: offload inference to CoroutineScope            | ✅ `Done`    | Code review (Dispatchers.IO usage)                                       | `ThreadingTest`        |
  | **2.6** Implement robust listener management with death monitoring          | ✅ `Done`    | Code review (RemoteCallbackList usage)                                   | `ListenerTest`         |
  | **2.7** Implement comprehensive configuration validation                     | ✅ `Done`    | Code review (validateConfiguration method)                               | `ConfigValidationTest` |
  | **2.8** Declare service in Manifest with permission & intent filter         | ✅ `Done`    | Manifest review (service declared with permissions)                      | N/A                    |
  | **2.9** Implement `BaseRunner` Interface & `RunnerRegistry`                 | ✅ `Done`    | Code review of runner contract and factory pattern                       | `BaseRunnerTest`       |
  | **2.10** Implement full suite of Mock Runners (`LLM`, `ASR`, `VLM`...)      | ✅ `Done`    | Code review of all mock implementations                                  | `MockRunnerSuiteTest`  |
  | **2.11** Implement `AIEngineManager` for business logic orchestration       | ✅ `Done`    | Code review of central coordinator                                       | `AIEngineManagerTest`  |
  | **2.12** Implement build-variant DI using Gradle source sets                | ✅ `Done`    | `build.gradle.kts` review, `DependencyProvider` in `mock`/`prod` folders | `InjectionTest`        |
  | **2.13** Integrate `AIEngineManager` into `AIRouterService` via DI          | ✅ `Done`    | Code review, service delegates all requests                              | `ServiceIntegrationTest` |
  | **2.14** **Build & Verify App**                                             | ✅ `Done`    | `./gradlew :breeze-app-router:assembleDebug` succeeds                    | All tests pass         |

---

#### **Milestone 3: Headless Validation via ADB**

* **Objective:** Verify the `AIRouterService` is functioning correctly in the background, using only command-line tools (`adb`), before any UI is built. This proves the router is an independent, testable component.

* **Documentation Preparation Complete:** ✅
  - Quick Start Guide created for new developers
  - Developer Guide with comprehensive API examples
  
  | Task                                                                         | Status       | Validation Method                                                    | Unit Test                |
  |:---------------------------------------------------------------------------- |:------------ |:-------------------------------------------------------------------- |:------------------------ |
  | **3.1** Add test-only logic to `onStartCommand` in `AIRouterService`         | ✅ `Done`    | Code review: Service handles a test Intent to trigger a mock request | N/A (Integration)        |
  | **3.2** Create script/docs for `adb` commands to start the service           | ✅ `Done`    | Execute `adb shell am start-service ...` successfully               | N/A                      |
  | **3.3** Trigger test message via `adb` and verify Logcat output              | ✅ `Done`    | `adb logcat` shows expected logs from service mock responses        | N/A (Integration)        |
  | **3.4** (Optional) Create minimal UI-less test client APK for binder testing | ✅ `Done`    | `adb shell am instrument` triggers binder call successfully         | `BinderIntegrationTest`  |
  | **3.5** **Validation Complete**                                              | ✅ `Done`    | The service is confirmed to be working standalone                   | All tests pass           |
  | **3.6** **Test Runner extensibility via ADB commands**                       | ✅ `Done`    | Add new MockRunner via ADB intent, verify it's registered and usable| `ExtensibilityTest`      |
  | **3.7** **Validate Runner fallback mechanism headlessly**                    | ✅ `Done`    | Force MockLLMRunner failure via ADB, verify fallback to MockAPIRunner| `FallbackTest`          |
  | **3.8** **Test concurrent requests with different Runners**                  | ✅ `Done`    | Send LLM, ASR, TTS requests simultaneously via ADB, verify all handled| `ConcurrencyTest`       |
  | **3.9** **Validate Runtime Runner configuration changes**                    | ✅ `Done`    | Change Runner priority via ADB, verify selection logic updates      | `RuntimeConfigTest`      |

---

#### **Milestone 3.5: Reference Client Implementation (NEW)**

* **Objective:** Create a standalone reference client app (`breeze-app-router-client`) that demonstrates best practices for integrating with the `AIRouterService`. This app will serve as both a testing tool and comprehensive documentation for third-party developers.

* **Implementation & Validation Tracking:**
  
  | Task                                                                            | Status       | Validation Method                                                | Unit Test                |
  |:------------------------------------------------------------------------------- |:------------ |:---------------------------------------------------------------- |:------------------------ |
  | **3.5.1** Create new Android application module `breeze-app-router-client`      | ✅ `Done`    | Module structure review                                          | N/A                      |
  | **3.5.2** Add `:shared-contracts` dependency & permission in Manifest           | ✅ `Done`    | `build.gradle.kts` & Manifest review                             | N/A                      |
  | **3.5.3** Implement MVVM architecture with clean separation of concerns         | ✅ `Done`    | Code review of `MainActivity` and `MainViewModel`                | `ArchitectureTest`       |
  | **3.5.4** Implement reactive UI with `StateFlow` and `UiState` data class       | ✅ `Done`    | Code review of state management pattern                          | `ReactiveUITest`         |
  | **3.5.5** Implement robust service connection with fallback to debug/prod       | ✅ `Done`    | Code review of connection logic with error handling              | `ConnectionTest`         |
  | **3.5.6** Implement comprehensive testing tools (`AIRouterTester` class)        | ✅ `Done`    | Code review of test utilities                                    | `TesterFunctionalityTest`|
  | **3.5.7** Implement diagnostics script (`test_connection.sh`)                   | ✅ `Done`    | Script execution validates connection                            | N/A (Integration)        |
  | **3.5.8** Create professional API documentation with examples                   | ✅ `Done`    | Documentation review (tables, examples, troubleshooting)         | N/A                      |
  | **3.5.9** Add KDoc documentation to all public methods                          | ✅ `Done`    | Code review of documentation coverage                            | `DocumentationCoverageTest` |
  | **3.5.10** **Build & Verify App**                                               | ✅ `Done`    | `./gradlew :breeze-app-router-client:assembleDebug` succeeds     | All tests pass           |

---

#### **Milestone 4: The Resilient Client**

* **Objective:** Implement a client-side `AIRouterRepository` in `breeze-app-ui` that can securely and robustly bind to the **pre-validated** `AIRouterService`.
* **Note:** The implementation of the repository, its data models, and error handling should closely follow the detailed designs in **`client_api_spec.md`** and the reference implementation in `breeze-app-router-client`.

* **Implementation & Validation Tracking:**
  
  | Task                                                                           | Status       | Validation Method                                 | Unit Test                 |
  |:------------------------------------------------------------------------------ |:------------ |:------------------------------------------------- |:------------------------- |
  | **4.1** Add `:shared-contracts` dependency & custom permission use in Manifest | ✅ `Done`    | `build.gradle.kts` & Manifest review              | N/A                       |
  | **4.2** Define `domain/repository/AIRouterRepository.kt` interface             | ✅ `Done`    | Code review                                       | N/A                       |
  | **4.3** Implement `data/repository/AIRouterRepositoryImpl.kt`                  | ✅ `Done`    | Code review (see `client_api_spec.md`)            | `RepositoryBindingTest`   |
  | **4.4** Implement `ServiceConnection` with reconnection logic                  | ✅ `Done`    | Code review, simulate service crash               | `ReconnectionTest`        |
  | **4.5** Implement threading: dispatch binder calls to `Dispatchers.IO`         | ✅ `Done`    | Code review                                       | `ThreadingTest`           |
  | **4.6** Wrap all binder calls in `try-catch(RemoteException)`                  | ✅ `Done`    | Code review                                       | `ExceptionHandlingTest`   |
  | **4.7** Provide repository via Hilt DI module                                  | ✅ `Done`    | Code review                                       | `DependencyInjectionTest` |
  | **4.8** **Build & Verify App**                                                 | ✅ `Done`    | `./gradlew :breeze-app-ui:assembleDebug` succeeds | All tests pass            |

---

#### **Milestone 5: Full E2E Integration**

* **Objective:** Validate the complete end-to-end communication loop: send a message from the UI App, have it received by the Router App, and get a mock response back.
* **Note:** The implementation of the ViewModel, its state management, and interaction with the UseCase should follow the patterns in **`client_api_spec.md`** and the reference implementation in `breeze-app-router-client`.

* **Implementation & Validation Tracking:**
  
  | Task                                                                       | Status       | Validation Method                                        | Unit Test                |
  |:-------------------------------------------------------------------------- |:------------ |:-------------------------------------------------------- |:------------------------ |
  | **5.1** Implement `SendMessageUseCase` in UI app's domain layer            | ✅ `Done`    | Code review                                              | `SendMessageUseCaseTest` |
  | **5.2** Inject UseCase into `ChatViewModel`                                | ✅ `Done`    | Code review (see `client_api_spec.md`)                   | `ChatViewModelTest`      |
  | **5.3** Call use case from ViewModel on user action                        | ✅ `Done`    | Code review                                              | `ChatViewModelTest`      |
  | **5.4** Validate E2E message flow through existing Mock Runner system      | ✅ `Done`    | Logcat shows request delegation to AIEngineManager       | N/A (Integration)        |
  | **5.5** Implement client-side listener to receive response                 | ✅ `Done`    | Logcat on UI app shows response                          | N/A (Integration)        |
  | **5.6** Update ViewModel with response via `Flow`                          | ✅ `Done`    | Code review (see `client_api_spec.md`)                   | `ChatViewModelTest`      |
  | **5.7** **Deploy & Test E2E**                                              | ✅ `Done`    | Install both apps, send message, see mock response in UI | E2E Test                 |
  | **5.8** **Validate Runner switching during E2E flow**                      | ✅ `Done`    | Test fallback from MockLLMRunner to MockAPIRunner       | E2E Fallback Test        |
  | **5.9** **Performance test with multiple concurrent requests**             | ✅ `Done`    | Send multiple messages concurrently, verify proper handling | Performance Test      |
  | **5.10** **Validate streaming response handling**                          | ✅ `Done`    | Test streaming mock responses from MockLLMRunner        | Streaming Test           |

---

### **3. Summary**

This refactoring approach has successfully achieved its goals through a methodical, milestone-driven development process. The addition of the `breeze-app-router-client` as a reference implementation has provided significant value:

1. **Validation of the Architecture**: The client app confirms that our AIDL-based IPC design is robust and performant.
2. **Documentation for Developers**: The client serves as living documentation with comprehensive examples.
3. **Testing Tool**: The client includes utilities for testing and diagnosing integration issues.
4. **Best Practices Showcase**: The client demonstrates MVVM architecture, reactive UI patterns, and proper error handling.

All milestones have been completed, resulting in a fully decoupled system with a clean architecture that separates concerns and promotes maintainability. The use of Mock Runners has allowed for parallel development and thorough testing before integrating with actual AI models.

**Next Steps:**
1. Continue refining the documentation based on developer feedback
2. Consider publishing the `shared-contracts` module as a standalone dependency for third-party developers
3. Implement production runners with actual AI models
4. Expand the test coverage with more edge cases and performance scenarios