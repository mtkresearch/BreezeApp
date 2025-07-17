# 📦 EdgeAI Library 發佈至 JitPack SOP

> **維護責任人**：mtkresearch 團隊  
> **目的**：讓 EdgeAI 模組可由其他應用透過 JitPack 導入，並確保僅有 mtkresearch 成員能執行版本發佈

---

## 📁 專案結構要求

EdgeAI 模組必須符合以下結構（可在 monorepo 或獨立 repo 中）：

```
<REPO_ROOT>/
├── EdgeAI/
│   ├── build.gradle.kts         <-- 包含 maven-publish 設定
│   └── src/main/AndroidManifest.xml
├── build.gradle.kts             <-- root build script
├── settings.gradle.kts          <-- 包含 EdgeAI module
├── gradlew                      <-- gradle wrapper
├── gradle/wrapper/
│   ├── gradle-wrapper.properties
│   └── gradle-wrapper.jar
├── jitpack.yml                  <-- 見下方範例
```

---

## 🔧 EdgeAI Module 設定

### `EdgeAI/build.gradle.kts`

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group = "com.github.mtkresearch"
version = "edgeai-v0.1.0" // 必須與 Git Tag 相同

android {
    namespace = "com.mtkresearch.breezeapp.edgeai"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        aidl = true
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["release"])
            groupId = "com.github.mtkresearch"
            artifactId = "EdgeAI"
            version = project.version.toString()
        }
    }
}
```

---

## 📜 `jitpack.yml` 設定

```yaml
jdk:
  - openjdk17

install:
  - ./gradlew :EdgeAI:publishToMavenLocal

build:
  script: ./gradlew :EdgeAI:assembleRelease

env:
  GRADLE_OPTS: "-Xmx2048m -Dfile.encoding=UTF-8"
```

> 📌 如果 EdgeAI 是在子資料夾（例如 BreezeApp_v2/EdgeAI），請加上 `cd BreezeApp_v2` 開頭指令

---

## 🚀 發佈流程（mtkresearch 專用）

### ✅ 步驟 1：更新版本號
打開 `EdgeAI/build.gradle.kts`：
```kotlin
version = "edgeai-v0.1.1"  // 遞增版本號
```

---

### ✅ 步驟 2：提交變更
```bash
git add EdgeAI/build.gradle.kts
git commit -m "Release EdgeAI edgeai-v0.1.1"
git push origin breezeapp_v2
```

---

### ✅ 步驟 3：打 Git Tag
```bash
git tag edgeai-v0.1.1
git push origin edgeai-v0.1.1
```

---

### ✅ 步驟 4：驗證 JitPack 成功

到以下網址確認 build 狀態為綠色（成功）：
```
https://jitpack.io/#mtkresearch/BreezeApp/edgeai-v0.1.1
```

---

## 🔗 外部專案引用 EdgeAI

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.mtkresearch.BreezeApp:EdgeAI:edgeai-v0.1.1")
}
```

> 若未來 repo 改為 `BreezeApp_v2`，請將名稱改為：
>
> ```kotlin
> implementation("com.github.mtkresearch.BreezeApp_v2:EdgeAI:edgeai-v0.1.1")
> ```

---

## 🚫 權限與社群規範

| 行為 | 權限 | 備註 |
|------|------|------|
| 修改 EdgeAI 程式碼 | ✅ 所有人 | 可提 PR |
| 發佈 EdgeAI 新版本 | 🚫 限 mtkresearch | 僅由內部人員操作 tag |
| 合併包含 `version` 或 `tag` 的 PR | ❌ 禁止 | 將進行 revert |

---

## 🛠 常見錯誤排查

| 問題 | 錯誤訊息 | 解法 |
|------|----------|------|
| 缺少 Gradle wrapper | `./gradlew: No such file or directory` | 確保 repo 有 `gradlew` + `gradle-wrapper.properties` |
| module 找不到 | `Could not find :EdgeAI:` | 確保 `settings.gradle.kts` 有 `include(":EdgeAI")` |
| 無法辨識版本 | `version not found` | 確保 Git tag 格式正確、與 `version` 一致 |
| 無法解析依賴 | `Failed to resolve: EdgeAI` | 檢查是否使用了正確版本（非 SNAPSHOT） |

---

## ✅ 推薦 GitHub Actions 檢查

可以在 `.github/workflows/edgeai-validate.yml` 加入：

```yaml
name: Validate EdgeAI

on:
  push:
    paths:
      - 'EdgeAI/**'
      - '.github/workflows/edgeai-validate.yml'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build EdgeAI
        run: ./gradlew :EdgeAI:assembleRelease
```

---

## 📌 附錄：版本命名建議

請統一使用以下命名格式發佈：

```
edgeai-v0.1.0
edgeai-v0.1.1
edgeai-v0.2.0
...
```

---

## 🧪 發佈快速指令

```bash
# 1. 編輯版本號
vim EdgeAI/build.gradle.kts

# 2. Commit & Push
git commit -am "Release edgeai-v0.1.1"
git push origin breezeapp_v2

# 3. Tag & Push
git tag edgeai-v0.1.1
git push origin edgeai-v0.1.1
```

---

© 2025 mtkresearch internal use only
