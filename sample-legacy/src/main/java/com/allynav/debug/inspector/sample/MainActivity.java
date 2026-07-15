package com.allynav.debug.inspector.sample;

import android.app.Activity;
import android.os.Bundle;
import android.util.Base64;
import android.widget.TextView;

import com.allynav.debug.inspector.DebugInspector;
import com.allynav.debug.inspector.api.SerialEvent;
import com.allynav.debug.inspector.api.WebSocketEvent;
import com.allynav.debug.inspector.okhttp3.DebugInspectorInterceptor;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class MainActivity extends Activity {
    private TextView status;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.sample_status);
        client = new OkHttpClient.Builder()
                .addInterceptor(new DebugInspectorInterceptor("sample-session", "registration-001"))
                .addInterceptor((Interceptor) chain -> encryptedResponse(chain.request()))
                .build();
        findViewById(R.id.sample_open).setOnClickListener(v -> DebugInspector.open(this));
        findViewById(R.id.sample_http).setOnClickListener(v -> runHttp());
        findViewById(R.id.sample_websocket).setOnClickListener(v -> reportWebSocket());
        findViewById(R.id.sample_serial).setOnClickListener(v -> reportSerial());
    }

    private void runHttp() {
        status.setText(R.string.sample_http_loading);
        new Thread(() -> {
            try {
                JSONObject plain = new JSONObject().put("account", "field-user").put("password", "visible-in-body");
                String encoded = Base64.encodeToString(plain.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                String wireBody = new JSONObject().put("para", encoded).toString();
                Request request = new Request.Builder().url("https://sample.allynav.local/register")
                        .header("Authorization", "Bearer sample-token")
                        .post(RequestBody.create(MediaType.parse("application/json"), wireBody)).build();
                Response response = client.newCall(request).execute();
                response.close();
                runOnUiThread(() -> status.setText(R.string.sample_http_complete));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(error.toString()));
            }
        }, "Sample-Http").start();
    }

    private static Response encryptedResponse(Request request) throws IOException {
        try {
            JSONObject plain = new JSONObject().put("result", "registered").put("userId", 1001);
            String encoded = Base64.encodeToString(plain.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            String wireBody = new JSONObject().put("code", 0).put("data", encoded).toString();
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .header("Content-Type", "application/json")
                    .body(ResponseBody.create(MediaType.parse("application/json"), wireBody)).build();
        } catch (Exception error) {
            throw new IOException(error);
        }
    }

    private void reportWebSocket() {
        String connection = "vehicle-status";
        DebugInspector.webSocket().report(WebSocketEvent.builder(connection, WebSocketEvent.Type.OPEN).build());
        DebugInspector.webSocket().report(WebSocketEvent.builder(connection, WebSocketEvent.Type.MESSAGE)
                .direction(WebSocketEvent.Direction.RECEIVED).textPayload("{\"online\":true,\"speed\":2.4}").build());
        status.setText(R.string.sample_ws_complete);
    }

    private void reportSerial() {
        byte[] tx = "{\"cmd\":\"work\",\"id\":101}\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] rx = new byte[]{0x06, 0x31, 0x30, 0x31, 0x0D, 0x0A};
        DebugInspector.serial().report(SerialEvent.builder("/dev/ttyWCH2", SerialEvent.Direction.TX)
                .payload(tx).baudRate(38400).configuration("8N1 / RS485-CH2").correlationId("work-101").build());
        DebugInspector.serial().report(SerialEvent.builder("/dev/ttyWCH2", SerialEvent.Direction.RX)
                .payload(rx).baudRate(38400).configuration("8N1 / RS485-CH2").correlationId("work-101").build());
        status.setText(R.string.sample_serial_complete);
    }
}
