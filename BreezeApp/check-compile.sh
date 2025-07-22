#!/bin/bash

# 檢查編譯錯誤的腳本
echo "🔍 檢查 Kotlin 編譯錯誤..."

# 進入項目目錄
cd "$(dirname "$0")"

# 清理編譯
echo "🧹 清理之前的編譯..."
./gradlew clean > /dev/null 2>&1

# 檢查單元測試編譯
echo "🔧 檢查單元測試編譯..."
./gradlew compileDebugUnitTestKotlin --no-daemon 2>&1 | grep -E "(error|Error|ERROR|FAILED)" || echo "✅ 單元測試編譯成功"

# 檢查UI測試編譯
echo "🔧 檢查UI測試編譯..."
./gradlew compileDebugAndroidTestKotlin --no-daemon 2>&1 | grep -E "(error|Error|ERROR|FAILED)" || echo "✅ UI測試編譯成功"

# 檢查主代碼編譯
echo "🔧 檢查主代碼編譯..."
./gradlew compileDebugKotlin --no-daemon 2>&1 | grep -E "(error|Error|ERROR|FAILED)" || echo "✅ 主代碼編譯成功"

echo "🎉 編譯檢查完成！" 