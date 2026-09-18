# 按功能依赖

DebugInspector 支持完整聚合依赖，也支持按 HTTP、WebSocket、串口和数据库分别依赖。UI 会从应用最终合并的 Manifest 自动发现已安装模块，只显示对应菜单。

## 完整功能

旧项目继续使用原依赖即可，默认显示四个菜单：

```groovy
debugImplementation "com.allynav.debug:inspector-http:1.0.0"
releaseImplementation "com.allynav.debug:inspector-no-op:1.0.0"
```

## 按需组合

例如只启用 HTTP 和串口：

```groovy
debugImplementation "com.allynav.debug:inspector-http:1.0.0"
releaseImplementation "com.allynav.debug:inspector-no-op:1.0.0"

implementation "com.squareup.okhttp3:okhttp:3.4.1"
```

此组合只显示 `HTTP`、`串口` 两个菜单。`inspector-http` 已包含 `DebugInspectorInterceptor`，但 OkHttp 仍由宿主提供。

## 功能模块

| Artifact | 菜单与能力 |
| --- | --- |
| `inspector-http` | HTTP 请求、响应、Header、密文/明文、复制与分享 |
| `inspector-websocket` | WebSocket 生命周期与消息日志 |
| `inspector-serial` | 串口 TX/RX/ERROR 文本与 HEX 日志，不操作串口 |
| `inspector-database` | 只读数据库、表、行浏览与 CSV/JSON 导出 |

四个模块都会传递依赖公共 `inspector-runtime`。宿主不需要直接依赖 runtime。

## 依赖规则

- 完整聚合方式与按需组合方式二选一，不要同时声明 `inspector` 和单功能模块。
- `inspector-no-op` 仍用于 release 变体，保持宿主 main 源集调用签名一致。
- 未依赖的功能不会出现在调试器菜单中。
- 串口模块只有日志上报与显示能力，不会打开、关闭、配置或写入串口设备。
