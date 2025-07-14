# BreezeApp Router 模型下載架構優化計劃

## 目標
- 提供穩健、可擴充、易維護的模型下載與管理機制
- 支援多 runner、多模型、OTA、遠端管理等未來需求
- 貼合 Clean Architecture 與 Android 專業實踐

---

## 1. 下載觸發時機建議

### (A) Service 啟動時自動檢查/下載（推薦）
- 在 `AIRouterService.onCreate()` 檢查預設模型是否已下載，若未下載則自動觸發下載。
- 優點：確保 Service 啟動後，模型 always ready，無需 client 介入。
- 缺點：首次啟動有下載延遲，需設計進度/錯誤回報。

### (B) Client 端主動要求下載（進階/多模型情境）
- 由 client（App、UI、或其他 IPC client）透過 AIDL/IPC 呼叫 Service 下載指定模型。
- 適合多模型、用戶可切換模型、或需遠端 OTA 更新時。
- 需設計 AIDL 介面（如 downloadModel(modelId)），並將進度/錯誤回報給 client。

### (C) Use Case 層自動觸發（推論前自動下載）
- 在 AIEngineManager 處理推論請求時，若發現模型未下載，則自動觸發下載並等待完成。
- 適合「即用即下」場景，但需設計好 timeout、用戶體驗。

---

## 2. 設計原則
- 下載行為一律在 background thread/coroutine 執行，避免阻塞 Service。
- 進度/錯誤要能回報給 client（AIDL listener）、log 或 notification。
- 支援多 runner、多模型，能根據硬體/需求自動決定下載哪個模型。
- 責任分離：Service 負責生命週期與觸發，ModelManager/VersionStore 處理下載與 metadata。

---

## 3. 進階功能規劃
- 下載策略：自動重試、網路狀態感知、空間不足提示。
- OTA/遠端管理：可設計 broadcast receiver 或 server push 觸發下載。
- 多 runner 支援：根據硬體/需求自動決定下載哪個模型。
- 下載暫停/續傳、進度通知、失敗自動清理。
- metadata 儲存展開後的檔案資訊，便於校驗與管理。

---

## 4. 未來擴充建議
- 提供 IPC 介面讓 client 可主動要求下載/切換/刪除模型。
- 支援多模型並行下載與管理。
- 下載進度與錯誤可透過 callback、log、或 notification 回報。
- 可根據用戶行為或 server 指令自動觸發模型更新。

---

## 5. 實作備註
- 目前下載邏輯已可正確展開 group/pattern 並自動命名檔案。
- 未來如需進一步優化，建議先設計好 IPC 介面與進度回報機制，再進行多 runner/多模型/OTA 等進階功能開發。

---

> 本文件為 BreezeApp Router 模型下載架構優化計劃，供團隊後續回頭改善時參考。 