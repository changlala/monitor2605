# Changelog

## v1.1 (2026-05-07)

### Added
- Release APK 签名，OPPO/ColorOS 可正常安装
- 首次安装显示桌面图标，点击启动后自动隐藏
- GitHub Actions 自动构建签名 APK

### Changed
- 诊断日志路径从私有目录移至公共存储，文件管理器可直接查看
  - 新路径：`/storage/emulated/0/Android/data/com.monitor.app/files/logs/`
- Gradle 升级至 8.10.2，JDK 17 编译

### Fixed
- 修复 BroadcastReceiver SAM 转换编译错误
- 修复 LocationRequest.Builder 参数错误
- 修复 WorkManager/Kotlin Result 类型冲突
- 修复缺失 hilt-work 依赖
- 修复签名配置声明顺序

---

## v1.0 (2026-05-07)

### Initial Release
- 双模式位置采集（前台 Service + WorkManager）
- 电量三级降级策略
- Room 数据库本地存储 + 范围去重上报
- 飞书 Webhook 交互式卡片推送
- 远程 JSON5 配置热更新（jsDelivr/Gitee/GitHub Raw 多源回退）
- 文件追加式诊断日志，每日轮转，7 天清理
- 进程保活（前台通知、AlarmManager 看门狗、开机自启、杀后拉起重启限频）
- 自动隐藏桌面图标
- 无图标应用，通知栏低调展示
