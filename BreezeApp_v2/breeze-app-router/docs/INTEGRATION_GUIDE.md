# 🧩 Integration Guide

本指南協助 Android App 開發者快速整合 BreezeApp AI Router Service。

---

## 1. 加入依賴

```kotlin
// build.gradle.kts
implementation(project(":BreezeApp_v2:shared-contracts"))
```

---

## 2. 綁定 Service

```kotlin
val intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService")
intent.setPackage("com.mtkresearch.breezeapp.router")
bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
```

### ServiceConnection 實作

```kotlin
private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        aiRouterService = IAIRouterService.Stub.asInterface(binder)
        aiRouterService.registerListener(aiRouterListener)
    }
    override fun onServiceDisconnected(name: ComponentName?) {
        aiRouterService = null
    }
}
```

---

## 3. 發送請求

```kotlin
val request = AIRequest(
    id = "req-001",
    sessionId = "sess-001",
    text = "Hello, AI!",
    timestamp = System.currentTimeMillis(),
    options = mapOf("request_type" to "text_generation")
)
aiRouterService.sendMessage(request)
```

---

## 4. 處理回應

```kotlin
private val aiRouterListener = object : IAIRouterListener.Stub() {
    override fun onResponse(response: AIResponse) {
        Log.d(TAG, "AI Response: ${response.text}")
    }
    override fun onError(requestId: String, error: String) {
        Log.e(TAG, "AI Error: $error")
    }
}
```

---

## 5. 串流請求（Streaming）

```kotlin
val streamingRequest = AIRequest(
    id = "req-002",
    sessionId = "sess-002",
    text = "Stream this!",
    timestamp = System.currentTimeMillis(),
    options = mapOf("request_type" to "text_generation", "streaming" to "true")
)
aiRouterService.sendMessage(streamingRequest)
```

> **注意**：串流回應會多次觸發 `onResponse`，可根據 `response.partial` 判斷是否為最終回應。

---

## 6. 取消請求

```kotlin
val success = aiRouterService.cancelRequest("req-001")
```

---

## 7. 多 Session 管理
- 每個 request/sessionId 可獨立追蹤
- 建議用唯一 sessionId 管理多用戶/多對話

---

## 8. 常見問題與除錯

- **Q: 綁定失敗？**
  - 檢查 package name 與 intent action 是否正確
  - 確認已安裝 router APK 並有正確權限
- **Q: 沒有收到回應？**
  - 檢查 listener 是否正確註冊
  - 檢查 requestId 是否唯一
- **Q: 如何 debug streaming？**
  - 觀察多次 onResponse 呼叫，最後一次 partial=false
- **Q: 如何確認支援的能力？**
  - `aiRouterService.hasCapability("streaming")` 回傳 true/false

---

## 9. 進階整合建議
- 支援多型態資料（文字、圖片、音訊）
- 可自訂 options 傳遞額外參數
- 建議封裝 Service 綁定與 listener 管理於 ViewModel 或 Repository

---

> 歡迎參考 `/examples` 目錄獲取更多實作範例！ 