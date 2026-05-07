# CLAUDE.md

## 打包发布前检查清单

每次修改代码并触发 GitHub Actions 构建前，必须完成以下步骤：

### 1. 更新版本号
修改 `app/build.gradle.kts`:
```kotlin
versionCode = <递增整数>
versionName = "<语义化版本号>"
```

### 2. 更新 CHANGELOG.md
在文件顶部新增版本条目，格式：
```markdown
## vX.Y.Z (YYYY-MM-DD)

### Added / Changed / Fixed
- 具体变更说明
```

### 3. 提交并推送
```bash
git add app/build.gradle.kts CHANGELOG.md
git commit -m "chore: bump version to X.Y.Z"
git push
```

### 4. 触发构建
```bash
gh workflow run "Build APK" --repo changlala/monitor2605
```

### 5. 验证 APK
- 下载构建产物
- 在目标设备上安装测试
- 确认通知栏显示「系统服务」
- 确认飞书群收到位置数据

---

## 项目的常规则

- 所有运行时异常必须写入 `exceptions_yyyy_MM_dd.log`，禁止静默吞异常
- 采集和上报事件必须写入 `diagnostic_yyyy_MM_dd.log`
- 日志目录：`getExternalFilesDir(null)/logs/`（文件管理器可访问）
- 配置文件 `config/config.json5` 为远程热更配置模板，修改后 App 自动拉取
