package com.example.sharerouter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Runs a compiled ProgramAsWeights (PAW) LoRA adapter through native
 * llama.cpp (see LlamaBridge / jni/llama_jni.cpp) and shows the output for a
 * given input, the same shape as MainActivity's regex tester.
 *
 * The base model is fetched over HTTP the same way js_sdk in
 * programasweights-js does (see BASE_MODEL_URL below), since it's large
 * (~133MB) and publicly hosted. The adapter/prompt template are this
 * specific PAW program's own compiled output — a locally-compiled artifact,
 * never published to HF's paw-programs repo — so they're bundled directly
 * in assets/paw/ instead of fetched.
 */
public class PawActivity extends Activity {

    // From the .paw's meta.json ("local_sdk"/"js_sdk" base_model).
    private static final String BASE_MODEL_URL =
            "https://huggingface.co/programasweights/GPT2-GGUF-Q8_0/resolve/main/gpt2-q8_0.gguf";

    private EditText inputField;
    private TextView outputView;
    private TextView statusView;
    private Button runBtn;

    private File modelFile;
    private File adapterFile;

    private volatile LlamaBridge bridge;
    private volatile String promptTemplate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File pawDir = new File(getFilesDir(), "paw");
        pawDir.mkdirs();
        modelFile = new File(pawDir, "model.gguf");
        adapterFile = new File(pawDir, "adapter.gguf");

        buildUI();
        new Thread(this::loadModel).start();
    }

    private void buildUI() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(48), pad, pad);

        root.addView(TopBar.build(this, PawActivity.class, "PAW Inference"));

        statusView = new TextView(this);
        statusView.setText("Loading model...");
        statusView.setPadding(0, 0, 0, dp(12));
        root.addView(statusView);

        TextView inputLabel = new TextView(this);
        inputLabel.setText("Input");
        root.addView(inputLabel);

        inputField = new EditText(this);
        inputField.setHint("Text to run through the PAW program");
        root.addView(inputField);

        runBtn = new Button(this);
        runBtn.setText("Run");
        runBtn.setEnabled(false);
        runBtn.setOnClickListener(v -> runInference());
        root.addView(runBtn);

        TextView outputLabel = new TextView(this);
        outputLabel.setText("Output");
        outputLabel.setPadding(0, dp(12), 0, 0);
        root.addView(outputLabel);

        outputView = new TextView(this);
        outputView.setPadding(0, dp(4), 0, dp(8));
        outputView.setTextIsSelectable(true);
        root.addView(outputView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadModel() {
        try {
            if (!modelFile.exists()) {
                report("Downloading base model (~133MB)...");
                downloadTo(BASE_MODEL_URL, modelFile);
            }
            if (!adapterFile.exists()) {
                copyAsset("paw/adapter.gguf", adapterFile);
            }
            promptTemplate = readAsset("paw/prompt_template.txt");
        } catch (IOException e) {
            report("Failed to load PAW assets: " + e);
            return;
        }

        try {
            // n_ctx=2048 matches the .paw's meta.json ("local_sdk"/"js_sdk" n_ctx).
            LlamaBridge loaded = new LlamaBridge(
                    modelFile.getAbsolutePath(), adapterFile.getAbsolutePath(), 2048);
            bridge = loaded;
            runOnUiThread(() -> {
                statusView.setText("Model loaded.");
                runBtn.setEnabled(true);
            });
        } catch (Exception e) {
            report("Failed to load model: " + e);
        }
    }

    private void copyAsset(String assetPath, File dest) throws IOException {
        try (InputStream in = getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    private String readAsset(String assetPath) throws IOException {
        try (InputStream in = getAssets().open(assetPath)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void downloadTo(String urlStr, File dest) throws IOException {
        File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setInstanceFollowRedirects(true);
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
        } finally {
            conn.disconnect();
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("Failed to move " + tmp + " to " + dest);
        }
    }

    private void runInference() {
        String input = inputField.getText().toString();
        if (input.isEmpty() || bridge == null) {
            return;
        }
        // The adapter was trained against this exact wrapped format, not raw input
        // (see the PAW program's prompt_template.txt) — raw text won't trigger it.
        String prompt = promptTemplate.replace("{INPUT_PLACEHOLDER}", input);
        runBtn.setEnabled(false);
        statusView.setText("Generating...");
        new Thread(() -> {
            String result;
            try {
                result = bridge.generate(prompt, 128);
            } catch (Exception e) {
                result = "Error: " + e;
            }
            String finalResult = result;
            runOnUiThread(() -> {
                outputView.setText(finalResult);
                statusView.setText("Model loaded.");
                runBtn.setEnabled(true);
            });
        }).start();
    }

    private void report(String message) {
        runOnUiThread(() -> statusView.setText(message));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
