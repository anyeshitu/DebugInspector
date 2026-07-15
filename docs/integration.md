# Android 项目接入

## 1. 依赖

将 `DebugInspector/local-maven` 配置为 Maven 仓库，然后按变体依赖：

```groovy
repositories {
    maven { url uri("C:/lwd/project/DebugInspector/local-maven") }
}

dependencies {
    debugImplementation "com.allynav.debug:inspector:0.1.0-SNAPSHOT"
    betaImplementation "com.allynav.debug:inspector:0.1.0-SNAPSHOT"
    releaseImplementation "com.allynav.debug:inspector-no-op:0.1.0-SNAPSHOT"

    // 宿主继续使用自己的 OkHttp 版本；Phase 1 支持 3.4.1 API 面。
    implementation "com.squareup.okhttp3:okhttp:3.4.1"
}
```

完整库和 no-op 提供相同的 `DebugInspector`、`DebugInspectorInterceptor` 宿主调用签名，因此代码放在 `main` 源集即可。

## 2. 初始化

```java
InspectorConfig config = InspectorConfig.builder()
        .redactHeaders("Auth-Token", "Authorization")
        .addBodyTransformer(new PlatformBodyTransformer())
        .entryConfig(EntryConfig.builder()
                .notificationEnabled(true)
                .shortcutEnabled(true)
                .shakeEnabled(false)
                .build())
        .build();

DebugInspector.initialize(application, config);
```

`redactHeaders` 按 Header 名称匹配且不区分大小写。`Bearer` 通常是 `Authorization` 的值前缀，不是 Header 名；配置 `Authorization` 即可遮盖 Bearer Token。

SDK 不脱敏 Body。账号、密码、参数以及加解密前后的 Body 都会持久化、搜索、显示和导出。

## 3. OkHttp 拦截器顺序

```java
OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(new CommonHeaderInterceptor())
        .addInterceptor(new RequestEncryptInterceptor())
        .addInterceptor(new DebugInspectorInterceptor())
        .build();
```

将 DebugInspector 放在 Header 和请求加密拦截器之后，可捕获实际发送的 Header/密文。解密插件从捕获到的 `para`/`data` 密文生成明文视图。

如果放在加密拦截器之前，记录的是加密前请求；这不等同于线上实际发送内容。

## 4. Body 解密插件

```java
public final class PlatformBodyTransformer implements HttpBodyTransformer {
    @Override
    public boolean supports(HttpTransformContext context) {
        return context.getContentType().contains("json");
    }

    @Override
    public TransformResult transform(HttpTransformContext context, BodyData raw) throws Exception {
        JSONObject json = new JSONObject(raw.asText());
        String field = context.getDirection() == HttpTransformContext.Direction.REQUEST
                ? "para" : "data";
        String plaintext = PlatformCrypto.decrypt(json.getString(field));
        return TransformResult.utf8(plaintext, "application/json");
    }
}
```

可以通过 `context.getHostTag()` 读取 OkHttp 3 的 `Request.tag()`，由宿主自行桥接 Retrofit 方法注解。核心 SDK 不依赖 Retrofit，也不保存密钥。

插件失败不会影响 HTTP 请求；UI 会保留原始密文并显示转换错误。

## 5. 打开和控制

```java
DebugInspector.open(context);
DebugInspector.pause();
DebugInspector.resume();
DebugInspector.clear();
```

通知栏和支持设备的桌面快捷方式默认开启；摇一摇默认关闭。SDK 只检查 Android 13+ 通知权限，不主动请求权限。

## 6. 数据库注册

标准 `Context.getDatabasePath()` 数据库自动发现。自定义/xUtils 路径可显式注册：

```java
DebugInspector.databases().register(
        new DatabaseRegistration("作业数据库", customDbPath)
);
```

数据库页面只使用 `OPEN_READONLY` 和生成的 `SELECT/PRAGMA`，不提供 SQL 控制台或修改操作。
