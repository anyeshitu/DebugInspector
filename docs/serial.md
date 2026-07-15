# 串口日志接入

DebugInspector 不打开、关闭、配置、读取或写入串口。宿主在已有串口代码的成功/失败位置上报字节即可。

## 发送

```java
try {
    outputStream.write(bytes);
    outputStream.flush();
    DebugInspector.serial().report(
            SerialEvent.builder("/dev/ttyWCH2", SerialEvent.Direction.TX)
                    .payload(bytes)
                    .baudRate(38400)
                    .configuration("8N1 / RS485-CH2")
                    .correlationId(workId)
                    .build()
    );
} catch (IOException error) {
    DebugInspector.serial().report(
            SerialEvent.builder("/dev/ttyWCH2", SerialEvent.Direction.ERROR)
                    .error(error.toString())
                    .correlationId(workId)
                    .build()
    );
    throw error;
}
```

TX 应在真实 `write` 和 `flush` 成功后上报。将数据加入 LocalSocket/队列不代表串口发送成功。

## 接收

```java
void onSerialBytes(byte[] bytes) {
    DebugInspector.serial().report(
            SerialEvent.builder("/dev/ttyWCH2", SerialEvent.Direction.RX)
                    .payload(bytes)
                    .charsetName("UTF-8")
                    .baudRate(38400)
                    .build()
    );
    handleProtocol(bytes);
}
```

上报时 SDK 会复制数组，宿主可以继续复用接收缓冲区。UI 同时显示解码文本和原始 HEX。

T101 的 `/dev/ttyWCH2` 方向控制属于 WCH 驱动；接入日志时不要新增 `max485_state` sysfs 操作。
