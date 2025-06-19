# BreezeApp Kotlin 推薦目錄結構

## 🏗️ 完整架構目錄設計

```
app/src/main/java/com/mtkresearch/breezeapp/
├── presentation/           ← Presentation Layer (UI + ViewModel)
│   ├── chat/              ← 聊天功能模組
│   │   ├── fragment/      ← Fragment實現
│   │   │   ├── ChatFragment.kt
│   │   │   ├── InputFragment.kt  
│   │   │   └── HistoryFragment.kt
│   │   ├── viewmodel/     ← ViewModel狀態管理
│   │   │   ├── ChatViewModel.kt
│   │   │   └── HistoryViewModel.kt
│   │   └── adapter/       ← RecyclerView Adapters
│   │       ├── MessageAdapter.kt
│   │       └── HistoryAdapter.kt
│   ├── settings/          ← 設定功能模組
│   │   ├── fragment/
│   │   │   └── SettingsFragment.kt
│   │   └── viewmodel/
│   │       └── SettingsViewModel.kt
│   ├── download/          ← 下載功能模組
│   │   ├── fragment/
│   │   │   └── DownloadFragment.kt
│   │   └── viewmodel/
│   │       └── DownloadViewModel.kt
│   ├── common/            ← 共用UI組件
│   │   ├── base/          ← Base類別
│   │   │   ├── BaseFragment.kt
│   │   │   └── BaseViewModel.kt
│   │   └── widget/        ← 自訂UI組件
│   │       ├── AudioWaveView.kt
│   │       └── MessageBubbleView.kt
│   └── MainActivity.kt    ← 主Activity
├── domain/                ← Domain Layer (業務邏輯核心)
│   ├── usecase/          ← 業務用例
│   │   ├── chat/         ← 聊天相關UseCase
│   │   │   ├── SendMessageUseCase.kt
│   │   │   ├── ProcessVoiceInputUseCase.kt
│   │   │   └── ManageConversationUseCase.kt
│   │   ├── media/        ← 媒體處理UseCase
│   │   │   ├── AnalyzeImageUseCase.kt
│   │   │   └── SpeakTextUseCase.kt
│   │   └── model/        ← 模型管理UseCase
│   │       └── DownloadModelUseCase.kt
│   ├── repository/       ← Repository介面定義
│   │   ├── ChatRepository.kt
│   │   ├── ModelRepository.kt
│   │   ├── ConfigRepository.kt
│   │   └── MediaRepository.kt
│   └── model/            ← Domain實體
│       ├── ChatMessage.kt
│       ├── AIRequest.kt
│       ├── AIResponse.kt
│       ├── ConversationModel.kt
│       └── ModelInfo.kt
├── data/                 ← Data Layer (數據存取實現)
│   ├── repository/       ← Repository實現
│   │   ├── ChatRepositoryImpl.kt
│   │   ├── ModelRepositoryImpl.kt
│   │   ├── ConfigRepositoryImpl.kt
│   │   └── MediaRepositoryImpl.kt
│   ├── source/           ← 數據來源
│   │   ├── local/        ← 本地數據源
│   │   │   ├── database/ ← Room Database
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── MessageDao.kt
│   │   │   │   └── ConversationDao.kt
│   │   │   ├── preferences/ ← SharedPreferences
│   │   │   │   └── PreferencesDataSource.kt
│   │   │   └── file/     ← 檔案系統
│   │   │       └── FileDataSource.kt
│   │   ├── ai/           ← AI引擎數據源
│   │   │   └── AIEngineDataSource.kt
│   │   └── remote/       ← 遠端數據源 (可選)
│   │       └── CloudDataSource.kt
│   └── entity/           ← 數據實體
│       ├── MessageEntity.kt
│       ├── ConversationEntity.kt
│       └── ConfigEntity.kt
├── core/                 ← Core Layer (基礎設施)
│   ├── ai/               ← AI Engine Layer
│   │   ├── manager/      ← AI引擎管理
│   │   │   ├── AIEngineManager.kt
│   │   │   └── EngineRegistry.kt
│   │   ├── engine/       ← 各AI引擎實現
│   │   │   ├── llm/      ← 大語言模型引擎
│   │   │   │   ├── LLMEngine.kt
│   │   │   │   └── LLMEngineImpl.kt
│   │   │   ├── vlm/      ← 視覺語言模型引擎
│   │   │   │   ├── VLMEngine.kt
│   │   │   │   └── VLMEngineImpl.kt
│   │   │   ├── asr/      ← 語音識別引擎
│   │   │   │   ├── ASREngine.kt
│   │   │   │   └── ASREngineImpl.kt
│   │   │   └── tts/      ← 文字轉語音引擎
│   │   │       ├── TTSEngine.kt
│   │   │       └── TTSEngineImpl.kt
│   │   ├── backend/      ← Backend Strategy
│   │   │   ├── BackendStrategy.kt
│   │   │   ├── HardwareDetector.kt
│   │   │   └── PerformanceBenchmark.kt
│   │   └── runtime/      ← Runtime Engines
│   │       ├── executorch/
│   │       │   └── ExecuTorchRuntime.kt
│   │       ├── mtk/
│   │       │   └── MTKNPURuntime.kt
│   │       ├── onnx/
│   │       │   └── ONNXRuntime.kt
│   │       └── cloud/
│   │           └── CloudRuntime.kt
│   ├── native/           ← Native Layer
│   │   ├── bridge/       ← JNI Bridge
│   │   │   ├── UnifiedJNIBridge.kt
│   │   │   └── NativeError.kt
│   │   ├── loader/       ← Model Loader
│   │   │   ├── UniversalModelLoader.kt
│   │   │   └── ModelValidator.kt
│   │   └── library/      ← Native Library封裝
│   │       ├── ExecuTorchLibrary.kt
│   │       ├── MTKLibrary.kt
│   │       └── SherpaLibrary.kt
│   ├── di/               ← Dependency Injection
│   │   ├── DatabaseModule.kt
│   │   ├── RepositoryModule.kt
│   │   ├── UseCaseModule.kt
│   │   ├── AIEngineModule.kt
│   │   └── ApplicationModule.kt
│   ├── config/           ← 配置管理
│   │   ├── AppConfig.kt
│   │   ├── ModelConfig.kt
│   │   └── AIConfig.kt
│   ├── error/            ← 錯誤處理
│   │   ├── AppException.kt
│   │   ├── ErrorHandler.kt
│   │   └── ErrorMapper.kt
│   └── utils/            ← 工具類別
│       ├── FileUtils.kt
│       ├── ImageUtils.kt
│       ├── AudioUtils.kt
│       └── Extensions.kt
```

## 🧪 測試目錄結構

```
app/src/test/java/com/mtkresearch/breezeapp/        ← Unit Tests
├── domain/
│   ├── usecase/
│   │   ├── SendMessageUseCaseTest.kt
│   │   └── ProcessVoiceInputUseCaseTest.kt
│   └── model/
│       └── ChatMessageTest.kt
├── data/
│   └── repository/
│       └── ChatRepositoryImplTest.kt
└── core/
    └── ai/
        └── manager/
            └── AIEngineManagerTest.kt

app/src/androidTest/java/com/mtkresearch/breezeapp/ ← Integration Tests
├── presentation/
│   └── chat/
│       └── ChatFragmentTest.kt
├── data/
│   └── source/
│       └── local/
│           └── DatabaseTest.kt
└── core/
    └── ai/
        └── engine/
            └── LLMEngineIntegrationTest.kt
```

## 📋 當前狀態檢查清單

### ✅ 已建立的目錄
- [x] presentation/chat/
- [x] presentation/settings/  
- [x] presentation/download/
- [x] domain/model/
- [x] domain/repository/
- [x] domain/usecase/
- [x] data/repository/
- [x] data/source/
- [x] core/ai/
- [x] core/native/
- [x] core/di/

### ⚠️ 需要補充的目錄
- [ ] presentation/*/fragment/
- [ ] presentation/*/viewmodel/
- [ ] presentation/common/
- [ ] domain/usecase/*/
- [ ] data/source/local/
- [ ] data/source/ai/
- [ ] data/entity/
- [ ] core/ai/manager/
- [ ] core/ai/engine/
- [ ] core/ai/backend/
- [ ] core/ai/runtime/
- [ ] core/native/bridge/
- [ ] core/native/loader/
- [ ] core/config/
- [ ] core/error/
- [ ] core/utils/
- [ ] 測試目錄結構

### 📋 建議行動計畫

1. **階段1**: 補充基礎目錄結構
2. **階段2**: 建立核心介面和抽象類別
3. **階段3**: 實現Domain Layer (UseCase + Repository Interface)
4. **階段4**: 實現Data Layer和AI Engine Layer
5. **階段5**: 實現Presentation Layer
6. **階段6**: 建立測試架構

這個完整的目錄結構確保了新架構的六層設計得到完整實現，同時為未來擴展提供了清晰的組織基礎。 