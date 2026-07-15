# WebSocket 日志接入

Phase 1 使用手工回调上报，不绑定 WebSocket 库：

```java
DebugInspector.webSocket().report(
        WebSocketEvent.builder(connectionId, WebSocketEvent.Type.OPEN).build()
);

DebugInspector.webSocket().report(
        WebSocketEvent.builder(connectionId, WebSocketEvent.Type.MESSAGE)
                .direction(WebSocketEvent.Direction.RECEIVED)
                .textPayload(message)
                .sessionId(sessionId)
                .build()
);

DebugInspector.webSocket().report(
        WebSocketEvent.builder(connectionId, WebSocketEvent.Type.FAILURE)
                .detail(error.toString())
                .build()
);
```

支持 `CONNECTING`、`OPEN`、`MESSAGE`、`CLOSING`、`CLOSED`、`FAILURE`。二进制消息使用 `binaryPayload(byte[])`。

SDK 不保存可控制连接的 socket 引用，不发送、关闭或重连。
