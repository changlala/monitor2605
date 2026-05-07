# 家长监护定位系统 — 设计文档

## 概述

Android 客户端应用，用于家长监护场景下静默采集设备位置信息，本地存储后定时上报至飞书 Webhook。全程低功耗运行，具备进程保活能力，支持服务端热更新配置。

- 目标用户：家长，对未成年子女设备进行位置监护
- 最低系统：Android 10 (API 29)
- 定位精度：高精度优先，带多级降级策略
- 采集与上报：完全解耦，各自独立调度

---

## 架构总览

```
┌──────────────────────────────────────────┐
│            客户端 (Android, Kotlin)        │
│                                           │
│  ┌─────────────────────────────────────┐  │
│  │        配置管理模块                  │  │
│  │  (多源回退拉取 GitHub Raw JSON5)     │  │
│  └──────────────┬──────────────────────┘  │
│                 │ Kotlin Flow 下发策略     │
│  ┌──────────────▼──────────────────────┐  │
│  │       StrategyDecider               │  │
│  │  时间 + 电量 + 配置 → 当前模式       │  │
│  └──────┬───────────────────┬──────────┘  │
│         │                   │             │
│  ┌──────▼──────┐   ┌────────▼─────────┐   │
│  │ Peak Mode   │   │ Off-peak Mode    │   │
│  │ Foreground  │   │ WorkManager      │   │
│  │ Service     │   │ PeriodicWork     │   │
│  └──────┬──────┘   └────────┬─────────┘   │
│         │                   │             │
│         └────────┬──────────┘             │
│                  ▼                        │
│  ┌─────────────────────────────────────┐  │
│  │        Room 数据库                   │  │
│  │  location_record + report_log       │  │
│  └──────────────┬──────────────────────┘  │
│                 │                         │
│  ┌──────────────▼──────────────────────┐  │
│  │       上报引擎 (WorkManager)         │  │
│  │  去重查询 → JSON → POST Webhook     │  │
│  └──────────────┬──────────────────────┘  │
│                 │                         │
│  ┌──────────────▼──────────────────────┐  │
│  │       诊断日志 (文件追加写入)         │  │
│  └─────────────────────────────────────┘  │
└──────────────────┬───────────────────────┘
                   │ HTTP POST
    ┌──────────────▼──────────────┐
    │  飞书自定义机器人 Webhook    │
    │  (接收数据，群消息展示)       │
    └─────────────────────────────┘
```

---

## 第一节：配置管理模块

### 配置文件格式（JSON5，存储于 GitHub 仓库，多源拉取）

```json5
{
  // ==================== 基础配置 ====================
  "version": 1,                         // 配置版本号，递增触发全量刷新
  "update_interval_minutes": 60,        // 配置自身拉取间隔（分钟）

  // ==================== 配置拉取地址（多源回退） ====================
  "config_sources": [
    {
      "url": "https://cdn.jsdelivr.net/gh/<user>/<repo>@<branch>/config.json5",
      "priority": 1
    },
    {
      "url": "https://gitee.com/<user>/<repo>/raw/<branch>/config.json5",
      "priority": 2
    },
    {
      "url": "https://raw.githubusercontent.com/<user>/<repo>/<branch>/config.json5",
      "priority": 3
    }
  ],
  "config_fetch_timeout_seconds": 10,
  "config_fetch_strategy": "sequential",

  // ==================== 定位采集策略 ====================
  "location_strategy": {
    "peak_hours": {
      "start": "07:00",
      "end": "20:00",
      "interval_seconds": 300,
      "priority": "HIGH_ACCURACY"
    },
    "off_peak_hours": {
      "start": "20:00",
      "end": "07:00",
      "interval_seconds": 1800,
      "priority": "BALANCED_POWER_ACCURACY"
    }
  },

  // ==================== 电量降级策略 ====================
  "degradation": [
    { "battery_pct_above": 50, "mode": "normal" },
    {
      "battery_pct_above": 20,
      "interval_multiplier": 2,
      "mode": "low_power",
      "max_accuracy_seconds": 600
    },
    {
      "battery_pct_above": 0,
      "interval_multiplier": 5,
      "mode": "critical",
      "force_workmanager": true
    }
  ],

  // ==================== 上报策略 ====================
  "report": {
    "batch_size": 100,
    "intervals": [
      { "start": "08:00", "end": "22:00", "interval_seconds": 600 },
      { "start": "22:00", "end": "08:00", "interval_seconds": 3600 }
    ],
    "wifi_only": false,
    "retry_max": 5,
    "retry_backoff_base_seconds": 30
  },

  // ==================== 网络端点 ====================
  "network": {
    "base_url": "https://open.feishu.cn/open-apis/bot/v2/hook/xxxxx",
    "timeout_seconds": 30
  },

  // ==================== 进程保活策略 ====================
  "keep_alive": {
    "foreground_service_notification": {
      "title": "系统服务",
      "text": "设备服务运行中",
      "on_click": "none"
    },
    "restart_on_kill": {
      "enabled": true,
      "max_restarts_per_hour": 3,
      "restart_delay_seconds": 30,
      "alarm_wakeup_enabled": true
    },
    "watchdog": {
      "enabled": true,
      "check_interval_seconds": 300
    },
    "device_specific": {
      "xiaomi_autostart_guide": true,
      "huawei_protected_app_guide": true,
      "oppo_background_guide": true
    }
  }
}
```

### 客户端加载机制

**配置源三层优先级：** 远程配置 > 本地缓存 > APK 内置默认值

首次启动时没有本地缓存，使用 APK 内硬编码的默认 `config_sources`（即同样的 GitHub/jsDelivr/Gitee 地址列表）完成首次拉取。远程配置中可以更新 `config_sources` 字段本身，拉取成功后新的源地址覆盖本地缓存，后续启动使用更新后的地址。

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | 远程配置 | 拉取成功后的最新配置，写入本地缓存 |
| 2 | 本地缓存 | 上次成功拉取的配置，持久化在应用私有目录 |
| 3 | APK 内置 | 编译时硬编码的默认配置，包含初始 config_sources |

- 启动时：异步拉取远程配置，成功则更新缓存 + 通过 Kotlin Flow 热生效
- 拉取失败：沿用本地缓存（有则用），缓存也没有则用 APK 内置默认值
- 多源回退：按 `config_sources` 优先级顺序逐个尝试，每个超时 10 秒，全部失败即视为本次拉取失败
- 定时刷新由 `update_interval_minutes` 控制
- 配置变更不重启进程，仅通过 Flow 驱动各模块调整行为
- 配置文件为纯 JSON 数据，天然支持热更新，无需 Tinker/Sophix 等代码热更方案

---

## 第二节：数据存储与上报去重

### 数据库（Room）

仅两张表：`location_record`（位置数据）和 `report_log`（上报范围记录）。

```sql
-- 位置采集表
CREATE TABLE location_record (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    latitude      REAL    NOT NULL,
    longitude     REAL    NOT NULL,
    altitude      REAL,
    accuracy      REAL,
    provider      TEXT    NOT NULL,          -- gps | network | fused
    recorded_at   INTEGER NOT NULL,          -- 采集时间戳（毫秒）
    battery_pct   INTEGER,
    created_at    INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

-- 上报日志表（记录每次上报覆盖的记录ID范围）
CREATE TABLE report_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    from_record_id  INTEGER NOT NULL,
    to_record_id    INTEGER NOT NULL,
    record_count    INTEGER NOT NULL,
    reported_at     INTEGER NOT NULL,
    response_code   INTEGER,
    success         INTEGER NOT NULL DEFAULT 0  -- 0失败 1成功
);
```

### 去重机制

不逐行标记 `is_reported` 字段，而是通过 `report_log` 的范围标记实现批量去重。每次查询待上报数据时：

```sql
SELECT * FROM location_record lr
WHERE lr.id NOT IN (
    SELECT lr2.id FROM location_record lr2
    INNER JOIN report_log rl ON lr2.id >= rl.from_record_id
                            AND lr2.id <= rl.to_record_id
    WHERE rl.success = 1
)
ORDER BY lr.recorded_at ASC
LIMIT :batch_size
```

优势：批量上报一次 INSERT 覆盖一片范围，比逐行 UPDATE 更高效；自然支持分批追踪和重试审计。

### 数据清理

- 已成功上报超过 7 天的位置数据 → DELETE
- 失败上报日志超过 30 天 → DELETE
- 由 WorkManager 每天执行一次

---

## 第三节：定位采集引擎

### 双模式架构

| 特性 | Peak Mode（高峰期） | Off-peak Mode（低峰期） |
|------|-------------------|------------------------|
| 实现 | ForegroundService | WorkManager PeriodicWork |
| 定位方式 | requestLocationUpdates 持续回调 | getLastLocation + getCurrentLocation 单次 |
| 精度 | HIGH_ACCURACY（GPS+WiFi+BT+基站） | BALANCED_POWER_ACCURACY |
| 唤醒锁 | PARTIAL_WAKE_LOCK | 无（系统管理） |
| 间隔 | 配置文件定义（默认 5 分钟） | 配置文件定义（默认 30 分钟，最少 15 分钟） |

### StrategyDecider（策略决策器）

- 协程每分钟 tick，读取当前时间 + 电量
- 首先匹配 `degradation` 降级表：按顺序遍历，命中第一个满足电量条件的即生效
- 降级策略通过 `interval_multiplier` 乘到当前时段的基础间隔上
- `force_workmanager: true` 时无论时段都强制退化为 WorkManager 模式
- 模式切换时写入 `mode_switch` 诊断日志
- 切换逻辑：peak→off_peak 执行 stopService + scheduleWorkManager；off_peak→peak 执行 cancelWorkManager + startForegroundService

### 防抖处理

- 连续两次定位间距 < 1 米 且 时间差 < 采集间隔的一半 → 丢弃后一条
- 即使丢弃，每分钟至少保留一条记录（防止设备长时间静止导致完全无数据）
- 防抖在 LocationRepository 层实现

---

## 第四节：上报引擎

### 核心设计

上报引擎与采集引擎完全解耦，Room 数据库是唯一交汇点。上报由独立的 `ReportWorker`（WorkManager PeriodicWork）驱动。

### 上报流程

```
WorkManager 触发
  │
  ├─ 1. 查 report_log WHERE success=1 取最大 to_record_id
  │     作为 last_reported_id
  │
  ├─ 2. 查 location_record WHERE id > last_reported_id
  │     LIMIT batch_size
  │     └─ 无数据 → 结束
  │
  ├─ 3. 构造 JSON payload
  │
  ├─ 4. POST 到飞书 Webhook
  │     └─ HTTP 200 且返回 "Success" → 写入 report_log(success=1)
  │     └─ 网络/超时 → 写入 report_log(success=0)，指数退避重试
  │     └─ HTTP 4xx → 不再重试（配置错误）
  │     └─ HTTP 5xx → 指数退避重试
  │
  └─ 5. retry_max 耗尽 → 放弃此批，写 report_log(success=0, response_code=-1)
                       继续处理下一批
```

### JSON Payload

```json
{
  "timestamp": 1715080000000,
  "device_id": "sha256(android_id)",
  "records": [
    {
      "lat": 39.9042,
      "lng": 116.4074,
      "alt": 45.2,
      "acc": 8.5,
      "provider": "gps",
      "ts": 1715079000000,
      "battery": 85
    }
  ]
}
```

### 上报策略参数（均由配置文件控制）

| 项目 | 说明 |
|------|------|
| 调度间隔 | 分时段不同间隔，动态 cancel + re-schedule |
| 批量上限 | batch_size，超过则分批独立上报 |
| 重试 | 指数退避 30s→60s→120s→240s→480s，最多 5 次 |
| 网络约束 | 默认不限，可通过 wifi_only 改为仅 WiFi |

---

## 第五节：诊断日志

### 存储方案

纯文件追加写入，不经过 Room。每日生成一个新文件。

```
/data/data/<package>/files/logs/
├── diagnostic_2026_05_05.log
├── diagnostic_2026_05_06.log
├── diagnostic_2026_05_07.log
└── diagnostic_2026_05_08.log
```

### 日志格式

```
[2026-05-07 14:32:01.234] service_killed | {"last_alive_at":1715080000}
[2026-05-07 14:32:31.456] service_restart | {"restart_count_this_hour":2,"delay_seconds":30}
[2026-05-07 14:35:00.789] config_update | {"version":2,"source":"jsdelivr","success":true}
```

### 事件类型

| event | 触发时机 |
|-------|---------|
| `service_start` | 前台服务启动 |
| `service_stop` | 前台服务正常停止 |
| `service_killed` | 进程被系统杀死（下次启动时检测到 last_alive_ts 残留） |
| `service_restart` | 被杀后自动拉起 |
| `restart_throttled` | 每小时重启次数超限，暂停拉起 |
| `alarm_wakeup` | AlarmManager 唤醒检测 |
| `mode_switch` | 采集模式切换 |
| `config_update` | 配置热更新成功/失败 |
| `config_fetch_fail` | 所有源拉取失败 |
| `report_success` | 单批上报成功 |
| `report_fail` | 单批上报失败（含重试次数） |
| `report_abandon` | 重试耗尽，舍弃批次 |
| `battery_degradation` | 电量降级触发 |
| `device_reboot` | 设备重启 |
| `database_cleanup` | 历史数据清理 |

### 被杀检测逻辑

- 前台服务正常 stop 时清除 SharedPreferences 中的 `last_alive_ts` 标记
- 下次启动时若 `last_alive_ts` 残留且距现在超过预期存活间隔 → 判定为被杀
- 非正常死亡时系统可能不回调 onDestroy，`last_alive_ts` 自然残留作为判定依据

### 日志清理

- 保留最近 7 天，超期文件在每次写入时顺带检查删除

### 日志导出

- 通过隐藏 Activity 中的特殊操作导出（如连续点击通知图标 5 次，或拨号盘 `*#*#MONITOR#*#*`），将日志文件复制到 Download 目录

---

## 第六节：进程保活

### 保活策略矩阵

| 策略 | 机制 | 说明 |
|------|------|------|
| 前台服务 | ForegroundService + 不可划通知 | Android 存活最高优先级的后台组件 |
| 自重启 | Service.onDestroy 中重新 startService | 覆盖正常销毁和部分异常销毁 |
| AlarmManager 兜底 | 每 5 分钟触发检查，服务不存活则拉起 | 覆盖系统强杀场景 |
| 重启限频 | 每小时最多 3 次 | 防止系统反复杀→反复拉导致的电量耗尽 |
| 开机自启 | BOOT_COMPLETED 广播 | 覆盖设备重启场景 |
| OEM 白名单引导 | 首次检测到小米/华为/OPPO 时弹出引导页 | 指导家长开启自启动、后台保护 |

### 前台服务通知

- 通知文字可自定义为"系统服务"/"设备服务运行中"
- 点击行为由 `on_click` 配置控制：`none` 无反应 / `open_app` 打开 Activity / `broadcast` 发广播
- 通知标志设为 ongoing（不可手动划掉，Android 8-12）

---

## 第七节：服务端（飞书 Webhook）

### 初期方案

飞书自定义机器人 Webhook 直接接收 JSON，数据在群消息中展示为富文本卡片，原始数据包含在消息体中。

- 成本：免费
- 限制：单条消息约 20KB，`batch_size` 需控制在此范围内
- 消息格式：飞书 interactive card，包含设备标识、时间范围、条数、原始 JSON

### 后期升级路径

飞书多维表格 API 支持批量写入，升级后数据可自动入表，在飞书内建仪表盘和地图视图。切换时只需更改 `network.base_url` 和 payload 格式，客户端其余代码不变。

---

## 第八节：App 图标与可见性

- 不在 Manifest 的 Activity 中声明 `LAUNCHER` category，启动后通过 `PackageManager.setComponentEnabledSetting()` 动态禁用启动器组件
- 用户桌面无图标，但系统设置→应用管理中仍可见
- 前台服务通知使用低调文字描述，降低用户对非目标用户的关注度

---

## 第九节：项目结构

```
monitor2605/
├── app/
│   ├── src/main/kotlin/com/monitor/app/
│   │   ├── MonitorApplication.kt
│   │   ├── config/
│   │   │   ├── ConfigManager.kt
│   │   │   ├── ConfigModel.kt
│   │   │   └── ConfigSources.kt
│   │   ├── location/
│   │   │   ├── LocationService.kt
│   │   │   ├── LocationWorker.kt
│   │   │   ├── LocationRepository.kt
│   │   │   └── StrategyDecider.kt
│   │   ├── report/
│   │   │   ├── ReportWorker.kt
│   │   │   ├── ReportPayload.kt
│   │   │   └── FeishuClient.kt
│   │   ├── keepalive/
│   │   │   ├── KeepAliveManager.kt
│   │   │   ├── WatchdogReceiver.kt
│   │   │   └── BootReceiver.kt
│   │   ├── diag/
│   │   │   └── DiagnosticLogger.kt
│   │   ├── db/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── LocationRecordDao.kt
│   │   │   ├── ReportLogDao.kt
│   │   │   └── entities/
│   │   │       ├── LocationRecord.kt
│   │   │       └── ReportLog.kt
│   │   ├── ui/
│   │   │   ├── HiddenActivity.kt
│   │   │   └── GuideActivity.kt
│   │   └── util/
│   │       ├── DeviceId.kt
│   │       ├── BatteryMonitor.kt
│   │       └── TimeRangeMatcher.kt
│   ├── src/main/res/
│   └── build.gradle.kts
├── config/
│   └── config.json5
└── build.gradle.kts
```

---

## 第十节：跨午夜时段匹配

配置中的时段（如 `off_peak_hours: 20:00-07:00`）可能跨越午夜。`TimeRangeMatcher` 将 `HH:mm` 转换为当日分钟偏移量（0-1439），判断逻辑：

- 若 `start <= end`（如 08:00-22:00）：`start_minute <= now_minute <= end_minute`
- 若 `start > end`（如 20:00-07:00）：`now_minute >= start_minute || now_minute <= end_minute`

此逻辑对 `location_strategy` 的 `peak_hours`/`off_peak_hours` 和 `report.intervals` 均适用。

---

## 技术选型

| 类别 | 选择 | 原因 |
|------|------|------|
| 语言 | Kotlin | Android 官方首选 |
| 最低 SDK | API 29 (Android 10) | 2020年后设备全覆盖 |
| 定位 | FusedLocationProviderClient | Google Play Services 融合定位最优 |
| 数据库 | Room | Jetpack 轻量 DB，Flow 集成 |
| 后台调度 | WorkManager | 自动处理 Doze/电池优化/重试 |
| HTTP | OkHttp + Kotlin Serialization | 稳定，拦截器链 |
| DI | Hilt | Android 生命周期感知 |
| 配置解析 | Gson + 手工剥离 JSON5 注释 | 轻量，免引入完整 JSON5 解析库 |
