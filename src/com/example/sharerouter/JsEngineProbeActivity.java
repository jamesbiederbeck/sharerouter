package com.example.sharerouter;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executor;

/**
 * Throwaway probe: confirms androidx.javascriptengine loads, dexes, and can
 * compile a WASM byte array on-device. Not wired into the app's normal flow.
 */
public class JsEngineProbeActivity extends Activity {

    private static final String TAG = "JsEngineProbe";

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        statusView = new TextView(this);
        statusView.setPadding(dp(16), dp(48), dp(16), dp(16));
        statusView.setText("Running probe...");
        setContentView(statusView);

        // Minimal valid WASM module: '\0asm' magic + version 1, no sections.
        byte[] minimalWasmBinary = new byte[]{0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00};
        runWasmSandbox(minimalWasmBinary);
    }

    private void runWasmSandbox(final byte[] wasmBytes) {
        Executor mainExecutor = this::runOnUiThread;

        if (!JavaScriptSandbox.isSupported()) {
            report("Sandbox not supported on this device/WebView.");
            return;
        }

        final ListenableFuture<JavaScriptSandbox> sandboxFuture =
                JavaScriptSandbox.createConnectedInstanceAsync(this);

        sandboxFuture.addListener(() -> {
            try (JavaScriptSandbox sandbox = sandboxFuture.get();
                 JavaScriptIsolate isolate = sandbox.createIsolate()) {

                if (!sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROVIDE_CONSUME_ARRAY_BUFFER)) {
                    report("ArrayBuffer sharing not supported on this device.");
                    return;
                }

                isolate.provideNamedData("my_binary", wasmBytes);

                String jsCode =
                        "android.consumeNamedDataAsArrayBuffer('my_binary').then(bytes => {" +
                        "   return WebAssembly.compile(bytes);" +
                        "}).then(module => {" +
                        "   return 'Wasm compiled successfully!';" +
                        "}).catch(err => {" +
                        "   return 'Error: ' + err.toString();" +
                        "});";

                ListenableFuture<String> resultFuture = isolate.evaluateJavaScriptAsync(jsCode);
                String output = resultFuture.get();
                report(output);
            } catch (Exception e) {
                Log.e(TAG, "Execution failed", e);
                report("Exception: " + e);
            }
        }, mainExecutor);
    }

    private void report(String message) {
        Log.i(TAG, message);
        statusView.setText(message);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
