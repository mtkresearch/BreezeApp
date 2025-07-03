# ⚙️ Configuration Guide

本指南說明 BreezeApp AI Router 的 Runner 配置方式與設計理念。

---

## 1. 配置檔案位置

- `breeze-app-router/src/main/assets/runner_config.json`

---

## 2. runner_config.json 格式

```json
{
  "runners": [
    {
      "name": "breeze_llm_mock_v1",
      "class": "com.mtkresearch.breezeapp.router.data.runner.MockLLMRunner",
      "capabilities": ["LLM"],
      "priority": 100,
      "is_real": false
    }
  ]
}
```

---

## 3. 欄位說明
- **name**：Runner 唯一名稱
- **class**：完整類別路徑
- **capabilities**：支援能力（LLM/ASR/TTS/VLM/GUARDIAN）
- **priority**：數字越小優先級越高（0=最高）
- **is_real**：是否為真實模型（true=需支援檢查，false=mock）

---

## 4. 如何新增/移除 Runner
1. 新增 Runner 類別（實作 BaseRunner）
2. 在 config 加入對應條目
3. 設定 priority 與 is_real
4. 重新啟動服務

---

## 5. priority 決策與降級策略
- 系統會自動選擇 priority 最小且支援的 Runner
- 若裝置不支援 real runner，會自動降級至 mock runner
- 可同時註冊多個 runner，依 priority 排序

---

## 6. is_real 與裝置支援檢查
- is_real=true 時，系統會呼叫 Runner 的 `isSupported()` 靜態方法
- 若不支援則自動跳過註冊
- mock runner 永遠可用於測試與降級

---

## 7. 動態註冊流程
- 修改 runner_config.json 後，重啟服務即可生效
- 不需重新編譯 APK

---

## 8. 常見錯誤
- class 路徑錯誤 → 無法反射實例化
- capabilities 拼寫錯誤 → 無法被選用
- priority 設定衝突 → 以排序為準
- is_real=true 但未實作 isSupported() → 不會註冊

---

> 建議每次修改後，檢查 logcat 是否有註冊失敗訊息！ 