#!/bin/bash

# BreezeApp UI 測試運行腳本
echo "📱 開始運行 BreezeApp UI 測試..."

# 設置顏色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 切換到項目目錄
cd "$(dirname "$0")/.."

echo -e "${BLUE}🔍 檢查連接的設備...${NC}"

# 檢查ADB設備
adb devices

echo -e "${YELLOW}🧪 運行UI測試...${NC}"

# 運行UI測試
./gradlew connectedAndroidTest --console=plain

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ UI測試通過${NC}"
else
    echo -e "${RED}❌ UI測試失敗${NC}"
    echo -e "${YELLOW}💡 提示：請確保已連接Android設備或模擬器${NC}"
    exit 1
fi

echo -e "${YELLOW}📊 生成UI測試報告...${NC}"

# 顯示測試報告位置
echo -e "${GREEN}📋 UI測試報告已生成: file://$(pwd)/app/build/reports/androidTests/connected/index.html${NC}"

echo -e "${GREEN}🎉 UI測試完成！${NC}" 