# Git Submodules 使用指南

本項目使用Git Submodules來管理子倉庫，包含以下子模組：

- `BreezeApp-engine`: BreezeApp引擎核心組件
- `BreezeApp-client`: BreezeApp客戶端組件

## 初始設置

### 首次克隆項目（包含子模組）
```bash
git clone --recursive https://github.com/mtkresearch/BreezeApp.git
```

### 如果已經克隆了項目，需要初始化子模組
```bash
git submodule init
git submodule update
```

## 日常使用

### 更新所有子模組到最新版本
```bash
git submodule update --remote
```

### 更新特定子模組
```bash
git submodule update --remote BreezeApp-engine
git submodule update --remote BreezeApp-client
```

### 進入子模組目錄進行開發
```bash
cd BreezeApp-engine
# 進行修改後提交
git add .
git commit -m "Update BreezeApp-engine"
git push origin main
cd ..
# 更新主項目中的子模組引用
git add BreezeApp-engine
git commit -m "Update BreezeApp-engine submodule"
```

### 切換子模組到特定分支或標籤
```bash
cd BreezeApp-engine
git checkout <branch-or-tag>
cd ..
git add BreezeApp-engine
git commit -m "Switch BreezeApp-engine to <branch-or-tag>"
```

## 團隊協作

### 新成員加入項目
1. 克隆主項目：`git clone https://github.com/mtkresearch/BreezeApp.git`
2. 初始化子模組：`git submodule init && git submodule update`

### 推送包含子模組更新的提交
```bash
# 確保子模組更新已提交
git submodule update --remote
git add .
git commit -m "Update submodules"
git push
```

## 故障排除

### 子模組狀態異常
```bash
# 重置子模組到正確狀態
git submodule deinit -f BreezeApp-engine
git submodule deinit -f BreezeApp-client
git submodule update --init --recursive
```

### 清理子模組
```bash
# 完全移除子模組（謹慎使用）
git submodule deinit -f BreezeApp-engine
git rm BreezeApp-engine
rm -rf .git/modules/BreezeApp-engine
git commit -m "Remove BreezeApp-engine submodule"
```

## 最佳實踐

1. **定期更新**: 定期運行 `git submodule update --remote` 保持子模組最新
2. **明確提交**: 在子模組中進行修改後，記得在主項目中提交子模組的更新
3. **版本控制**: 使用標籤或特定提交來確保子模組版本的一致性
4. **文檔同步**: 當子模組API發生變化時，及時更新相關文檔 