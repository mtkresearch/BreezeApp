# BreezeApp AI Router

BreezeApp v2 的 AI Router 是整個 AI 能力整合的核心。它以高度模組化、可擴充、型別安全為設計主軸，讓 App 開發者、AI 工程師、模型擁有者都能各司其職、協作無礙。

---

## 1. 角色定位與責任分工

這張圖明確標示三大角色在專案中的主要任務、交付物與互動界面，讓每個人一眼知道「我該做什麼、和誰互動」。

```mermaid
flowchart LR
    subgraph AppDeveloper["App Developer"]
        AD1[設計 UI/UX]
        AD2[串接 AIRouterService AIDL]
        AD3[處理/顯示 AIResponse]
    end
    subgraph AIEngineer["AI Engineer"]
        AE1[設計/實作 BaseRunner]
        AE2[註冊 runner_config.json]
        AE3[調整 default_config.json]
    end
    subgraph AIModelOwner["AI Model Owner"]
        MO1[訓練/準備 AI Model]
        MO2[提供模型格式]
        MO3[協助整合/測試]
    end
    MO3 --> AE1
    AE1 --> AE2 --> AE3
    AD2 --> AD3
```

> **這張圖讓 App Developer、AI Engineer、AI Model Owner 能一眼看出自己在這個專案中的定位與主要任務。**

---

## 2. 模組架構與資料流向

本圖展示 BreezeApp v2 以 Router 為核心的模組依賴與資料流向，強調 Router 的中樞地位與各模組的邊界。

```mermaid
flowchart LR
    subgraph ClientApp["Client App"]
        C1[ViewModel]
    end
    %% 強調 Router 區域
    subgraph Router["AI Router (breeze-app-router)"]
        R1[AIRouterService]
        R2[AIEngineManager]
        R3[RunnerRegistry]
    end
    subgraph EdgeAI["EdgeAI (AIDL/Contracts)"]
        E1[.chat/.tts/.asr]
    end
    subgraph Runners["Runners"]
        RU1[BaseRunner]
        RU2[CustomRunner]
    end
    subgraph Models["Models"]
        M1[CPU Model]
        M2[NPU Model]
        M3[GPU Model]
    end
    C1 -- AIDL --> R1
    R1 --> R2
    R2 --> R3
    R3 -- implements --> RU1
    RU1 -- loads --> M1
    RU1 -- loads --> M2
    RU1 -- loads --> M3
    R1 -. uses .-> E1
    C1 -. uses .-> E1

    %% 強調 Router 區域
    style Router fill:#FFF3CD,stroke:#FFB300,stroke-width:3px
```

> **這張圖說明 Router 作為中樞，如何串接 Client、EdgeAI 合約、Runner 與 Model，並清楚劃分模組責任。**

---

## 3. Router 運作與決策流程

本圖細緻描述 Router 收到請求後，如何根據 config 決定用哪個 Runner、如何注入參數、執行與回傳。

```mermaid
flowchart LR
    A[收到 AIRequest]
    B{查詢 runner_config.json}
    C[取得 Runner 列表]
    D{查詢 default_config.json}
    E[選定 Runner 與參數]
    F[執行 Runner]
    G[回傳 AIResponse]

    A --> B
    B --> C
    C --> D
    D --> E
    E --> F
    F --> G
```

> **這張圖橫向呈現 Router 如何根據兩個設定檔自動選擇與執行正確的 Runner，並標註每個步驟的重點。**

---

## 4. Runner 與 Model 的客製化關聯

本圖說明一個 Runner 可支援多種硬體（CPU/NPU/GPU），每種硬體下又有不同格式的 Model，並標註擴充方式。

```mermaid
flowchart TD
    R[BaseRunner]
    subgraph HW["硬體層"]
        CPU[CPU e.g.ExecRunner]
        NPU[NPU e.g.MTKLLMRunner]
        GPU[GPU]
    end
    subgraph ModelCPU["CPU 支援 Model"]
        MC1[ONNX]
        MC2[TensorFlow Lite]
    end
    subgraph ModelNPU["NPU 支援 Model"]
        MN1[自訂格式]
    end
    subgraph ModelGPU["GPU 支援 Model"]
        MG1[OpenCL]
    end
    R --> CPU --> MC1
    CPU --> MC2
    R --> NPU --> MN1
    R --> GPU --> MG1
```

> **這張圖讓 AI Engineer 與 Model Owner 清楚知道如何擴充 Runner 支援新硬體或新格式的 Model。**

---

## 5. 擴充流程圖：如何支援新模型/硬體

本圖以 step-by-step 流程，指引開發者如何擴充 Router 支援新模型或新硬體。

```mermaid
flowchart LR
    S1[新增 Model 檔案]
    S2[實作/擴充 BaseRunner]
    S3[註冊 runner_config.json]
    S4[調整 default_config.json]
    S5[測試 Runner]
    S6[交付/合併 PR]

    S1 --> S2 --> S3 --> S4 --> S5 --> S6
```

> **這張圖讓新手或進階開發者一目了然如何正確擴充 Router 能力。**

---

## 6. Router 運作細節與原有內容整合

### 高層資料流

```mermaid
graph TD
    subgraph "Client App"
        A[Client UI / ViewModel]
    end
    subgraph "AI Router Service (breeze-app-router)"
        direction TB
        B(AIRouterService)
        C(AIEngineManager)
        D(RunnerRegistry)
        E(BaseRunner)
    end
    A -- "1. AIRequest (via AIDL)" --> B
    B -- "2. Delegates Request" --> C
    C -- "3. Finds Correct Runner via<br/>runner_config.json" --> D
    D -- "4. Provides Runner" --> C
    C -- "5. Executes Runner" --> E
    E -- "6. Returns Flow<AIResponse>" --> C
    C -- "7. Streams Response Back" --> B
    style A fill:#D6EAF8,stroke:#3498DB
    style B fill:#E8DAEF,stroke:#8E44AD
    style C fill:#D1F2EB,stroke:#1ABC9C
    style D fill:#FEF9E7,stroke:#F1C40F
    style E fill:#FDEBD0,stroke:#F39C12
```

---

### End-to-End Communication Sequence

```mermaid
sequenceDiagram
    participant UI as Client UI/ViewModel
    participant AIDL as AIRouterService (AIDL)
    participant Manager as AIEngineManager
    participant Registry as RunnerRegistry
    participant Runner as BaseRunner
    UI->>AIDL: send AIRequest (via AIDL)
    AIDL->>Manager: delegate request
    Manager->>Registry: find runner (by capability)
    Registry->>Runner: instantiate/return runner
    Manager->>Runner: execute(request)
    Runner-->>Manager: Flow<AIResponse> (streaming)
    Manager-->>AIDL: stream AIResponse
    AIDL-->>UI: deliver AIResponse (callback)
```

> **這張圖說明了從 App 發送請求到收到 AI 回應的完整流程，包含 streaming 支援。**

---

## 7. 配置檔說明（Two-File System）

### 1. `runner_config.json` (Discovery & Registration)

- **Purpose:** 註冊所有可用 Runner 類別，類似 plugin registry。

- **範例：**
  
  ```json
  {
  "runners": [
    {
      "name": "breeze_llm_mock_v1",
      "class": "com.mtkresearch.breezeapp.router.data.runner.MockLLMRunner",
      "capabilities": ["LLM"],
      "priority": 100
    }
  ]
  }
  ```

### 2. `default_config.json` (Orchestration & Behavior)

- **Purpose:** 定義每個任務預設用哪個 runner 及其參數。

- **範例：**
  
  ```json
  {
  "runnerConfigurations": {
    "TEXT_GENERATION": {
      "defaultRunner": "breeze_llm_mock_v1",
      "runners": {
        "breeze_llm_mock_v1": {
          "type": "MOCK",
          "model": {
            "parameters": {
              "response_delay_ms": 100
            }
          }
        }
      }
    }
  }
  }
  ```

---

## 8. 開發與貢獻

- **[🧩 Runner Development Guide](docs/RUNNER_DEVELOPMENT.md)**: 學習如何擴充自訂 Runner。
- 請遵循主專案 `CONTRIBUTING.md` 的規範。

---

> 本文件以 Router 為主體，分層說明角色、模組、流程、擴充與細節，並以圖輔助理解，協助各類開發者快速上手與正確擴充。