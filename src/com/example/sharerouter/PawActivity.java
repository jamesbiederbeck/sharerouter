package com.example.sharerouter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runs a compiled ProgramAsWeights (.paw) LoRA adapter through native
 * llama.cpp (see LlamaBridge / jni/llama_jni.cpp) and shows the output for a
 * given input, the same shape as MainActivity's regex tester.
 *
 * Expects two files under getExternalFilesDir(null)/paw/, pushed manually:
 *   model.gguf   - the base GGUF model the .paw's adapter was trained against
 *   program.paw  - the compiled .paw archive (a zip containing adapter.gguf)
 */
public class PawActivity extends Activity {

    private EditText inputField;
    private TextView outputView;
    private TextView statusView;
    private Button runBtn;

    private File modelFile;
    private File pawArchiveFile;
    private File adapterExtracted;

    private volatile LlamaBridge bridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File pawDir = new File(getExternalFilesDir(null), "paw");
        modelFile = new File(pawDir, "model.gguf");
        pawArchiveFile = new File(pawDir, "program.paw");
        adapterExtracted = new File(getCacheDir(), "adapter.gguf");

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
        if (!modelFile.exists()) {
            report("Missing base model. Push it with:\n"
                    + "adb push <model.gguf> " + modelFile.getAbsolutePath());
            return;
        }
        if (!pawArchiveFile.exists()) {
            report("Missing .paw archive. Push it with:\n"
                    + "adb push <program>.paw " + pawArchiveFile.getAbsolutePath());
            return;
        }

        try {
            extractAdapter();
        } catch (IOException e) {
            report("Failed to extract adapter.gguf from .paw: " + e);
            return;
        }

        try {
            LlamaBridge loaded = new LlamaBridge(
                    modelFile.getAbsolutePath(), adapterExtracted.getAbsolutePath(), 512);
            bridge = loaded;
            runOnUiThread(() -> {
                statusView.setText("Model loaded.");
                runBtn.setEnabled(true);
            });
        } catch (Exception e) {
            report("Failed to load model: " + e);
        }
    }

    private void extractAdapter() throws IOException {
        try (ZipFile zip = new ZipFile(pawArchiveFile)) {
            ZipEntry entry = zip.getEntry("adapter.gguf");
            if (entry == null) {
                throw new IOException("program.paw has no adapter.gguf entry");
            }
            try (InputStream in = zip.getInputStream(entry);
                 OutputStream out = new FileOutputStream(adapterExtracted)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
        }
    }

    private void runInference() {
        String prompt = inputField.getText().toString();
        if (prompt.isEmpty() || bridge == null) {
            return;
        }
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
