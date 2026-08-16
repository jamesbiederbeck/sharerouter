package dev.nobugs.sharerouter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Shows the licenses for the PAW-inference dependencies bundled in this build. */
public class LicensesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(16);

        TextView text = new TextView(this);
        text.setPadding(pad, dp(48), pad, pad);
        text.setTextIsSelectable(true);
        text.setText(buildText());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        setContentView(scroll);
    }

    private String buildText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Licenses\n\n");
        appendSection(sb, "llama.cpp", "licenses/llama-cpp-LICENSE.txt");
        sb.append("\n\n");
        appendSection(sb, "ProgramAsWeights (paw)", "licenses/paw-LICENSE.txt");
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, String assetPath) {
        sb.append(title).append('\n').append("--------\n");
        try {
            sb.append(readAsset(assetPath));
        } catch (IOException e) {
            sb.append("(license text unavailable: ").append(e).append(')');
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
