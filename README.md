# DebugInspector

面向新老 Android 项目的中文调试 UI SDK，可查看和分享 HTTP、WebSocket、串口事件，并只读浏览应用 SQLite 数据库。

## Phase 1 能力

- Java 8、AGP 4.2.1、Gradle 6.7.1、compileSdk 30、minSdk 23。
- OkHttp 3.4.1 兼容拦截器，不强制宿主升级 OkHttp。
- HTTP Header 按名称脱敏；Body 不脱敏，同时保存和显示加密前后数据。
- HTTP 文本、cURL、JSON、HAR 查看/复制/分享。
- WebSocket 生命周期与消息手工上报。
- 串口 TX/RX/ERROR 字节手工上报，提供文本和 HEX 视图，不操作串口硬件。
- 自动发现标准 SQLite/Room 数据库并允许注册自定义/xUtils 路径，只读分页和 CSV/JSON 导出。
- HTTP、WebSocket、串口、数据库四个独立页面，默认中文、英文回退。
- Debug 完整实现与 Release no-op 变体。

## 模块

| Artifact | 用途 |
| --- | --- |
| `inspector-api` | 稳定 Java 8 数据模型和上报/解密插件接口 |
| `inspector-core` | SQLite 存储、清理、查询和数据库浏览 |
| `inspector-ui` | 平台 View/XML 中文 UI、通知、快捷方式、分享 |
| `inspector-okhttp3` | OkHttp 3.4.1 兼容拦截器 |
| `inspector` | Debug 完整聚合依赖 |
| `inspector-no-op` | Release 无副作用实现 |

完整接入见 [docs/integration.md](docs/integration.md)，串口接入见 [docs/serial.md](docs/serial.md)。

## 构建

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk'
.\gradlew.bat clean test assembleDebug assembleRelease
.\gradlew.bat apiCheck noOpParityCheck dependencyIsolationCheck
.\gradlew.bat publish
```

首次运行 Gradle Wrapper 需要下载 Gradle 6.7.1。本机必须安装 Android SDK 30，并在 `local.properties` 中配置 `sdk.dir`。

本地 Maven 产物输出到 `local-maven/com/allynav/debug/`。

## 默认限制

- 保留 24 小时。
- 捕获库最大 100 MB，超限从最旧记录删除。
- 每份 HTTP 明文/密文 Body 最大 250 KB。
- 每条 WebSocket/串口数据最大 64 KB。
- 数据库单次导出最大 10,000 行。

这些值均可通过 `RetentionPolicy` 修改。
