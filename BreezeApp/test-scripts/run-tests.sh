#!/bin/bash

# BreezeApp 測試運行腳本
echo "🧪 開始運行 BreezeApp 測試..."

# 設置顏色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 切換到項目目錄
cd "$(dirname "$0")/.."

echo -e "${YELLOW}📱 運行單元測試...${NC}"

# 運行單元測試
./gradlew test --console=plain

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 單元測試通過${NC}"
else
    echo -e "${RED}❌ 單元測試失敗${NC}"
    exit 1
fi

echo -e "${YELLOW}🔧 運行測試套件...${NC}"

# 運行特定測試套件
./gradlew test --tests "com.mtkresearch.breezeapp_kotlin.BreezeAppTestSuite" --console=plain

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 測試套件通過${NC}"
else
    echo -e "${RED}❌ 測試套件失敗${NC}"
fi

echo -e "${YELLOW}📊 生成測試報告...${NC}"

# 顯示測試報告位置
echo -e "${GREEN}📋 測試報告已生成: file://$(pwd)/app/build/reports/tests/test/index.html${NC}"

echo -e "${GREEN}🎉 測試完成！${NC}" 