# 现有项目迁移要点

## pile111

- 使用 `debugImplementation inspector` / `releaseImplementation inspector-no-op`。
- OkHttp 3.4.1 无需升级；将 `DebugInspectorInterceptor` 放到请求加密拦截器之后。
- 在 `Pile485Transmitter` 的 `write + flush` 成功后上报 TX，在异常分支上报 ERROR。
- SDK 不接收 `SerialPort`、`InputStream` 或 `OutputStream` 所有权。

## Sowing_0427

- 将 `PlatformChuckerBodyDecoder` 的解密逻辑迁移为 `HttpBodyTransformer`。
- 保留现有 Retrofit 注解判断，通过 `HttpTransformContext.getHostTag()` 或宿主映射读取。
- 继续配置 `Auth-Token`、`Authorization` Header 脱敏；Body 明文和密文都保留。

## robot_1125

- 将 `ProtoDecoder` 的 PKCS5、PKCS7、NONE 分支迁移到宿主 transformer。
- 把普通 `implementation(libs.chucker)` 改为完整/no-op 变体依赖，防止 Release 打入调试 UI。
- Debug 或 TEST 环境是否加入拦截器仍由宿主控制。
