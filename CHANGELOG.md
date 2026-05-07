# Changelog

## v1.3.4 (2026-05-07)

### Fixed
- 从系统设置返回后 UI 自动刷新权限状态，已授权则自动启动服务

---

## v1.3.3 (2026-05-07)

### Fixed
- 加固开机自启：扩大异常捕获，startForegroundService 失败后自动降级 WorkManager 拉起

---

## v1.3.2 (2026-05-07)

### Changed
- 保留桌面图标作为手动恢复通道，防止保活全部失效后无法启动服务
- 已授权状态下打开 App：秒启动服务并自动关闭页面，全程无感

---

## v1.3.1 (2026-05-07)

### Added
- 「系统设置」快捷跳转按钮，一键直达应用权限管理页
- 权限全部授予后自动启动服务，无需手动点击

---

## v1.3.0 (2026-05-07)

### Added
- 权限检查页实时显示各权限授予状态（已授权/未授权）
- 点击按钮批量请求所有缺失权限
- 仅当全部权限授予后才启动服务

### Changed
- 引导页 UI 改为权限清单列表 + 完成按钮
- 移除 @AndroidEntryPoint，改用现代 ActivityResultContracts API

---

## v1.2.9 (2026-05-07)

### Fixed
- 修复权限未授予时启动前台服务导致 SecurityException 崩溃
- 服务启动推迟到权限授予后，由引导页触发

---

## v1.2.8 (2026-05-07)

### Fixed
- Watchdog 改为双重拉活：startForegroundService 失败后自动降级为 WorkManager 拉起
- 扩大异常捕获范围，防止 OEM 特有异常导致静默失败

---

## v1.2.7 (2026-05-07)

### Fixed
- 修复飞书 interactive card 元素格式，`plain_text` 改为 `div` 包裹（修复 report_fail）

---

## v1.2.6 (2026-05-07)

### Fixed
- 补全 FeishuClient HTTP 异常日志，code=-1 时记录完整堆栈

---

## v1.2.5 (2026-05-07)

### Fixed
- 修正 Gitee 配置源分支名 `main` → `master`，路径加 `config/` 前缀

---

## v1.2.4 (2026-05-07)

### Fixed
- 修复按返回键退出引导页时图标未被禁用的问题
- 图标禁用时机改为 Activity 打开即执行，不再依赖用户点击完成按钮

---

## v1.2.3 (2026-05-07)

### Fixed
- 修复 onStartCommand 中未捕获异常导致服务静默崩溃的问题
- 添加异常保护并写入异常日志，避免服务闪退无法排查

---

## v1.2.2 (2026-05-07)

### Changed
- 更新 Gitee 配置源地址为 `changhao24/monitor2605`

---

## v1.2.1 (2026-05-07)

### Fixed
- 修复首次启动时图标过早被禁用导致 Activity 无法打开的问题
- 图标隐藏时机从 Application.onCreate 推迟至用户关闭引导页后

---

## v1.2 (2026-05-07)

### Added
- 独立异常日志文件 `exceptions_yyyy_MM_dd.log`，记录所有运行时异常堆栈
- 配置拉取/缓存读写异常日志
- 电量获取异常日志
- 采集事件日志（高峰和低峰模式均记录）

### Changed
- 日志目录移至公共存储，文件管理器可直接访问：
  `Android/data/com.monitor.app/files/logs/`

---

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
