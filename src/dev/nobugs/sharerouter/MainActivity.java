package dev.nobugs.sharerouter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Landing screen. Share Router's actual work happens as a share target
 * (see ShareActivity) — this screen just explains that and links to the
 * configuration/secondary screens, rather than dropping straight into the
 * regex filter tester the way this activity used to.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
    }

    private void buildUI() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(48), pad, pad);

        root.addView(TopBar.build(this, MainActivity.class, "Share Router"));

        TextView intro = new TextView(this);
        intro.setPadding(0, dp(8), 0, dp(24));
        intro.setText(
                "Share a link to Share Router the way you'd share it to any "
                        + "other app. It finds the URL, strips known tracking "
                        + "parameters (utm_*, fbclid, gclid, YouTube's si/is, "
                        + "and more), and gives you a clean link to copy, open, "
                        + "or share onward.\n\n"
                        + "Nothing to set up for that — it works out of the box. "
                        + "Everything below is optional configuration.");
        root.addView(intro);

        Button filtersBtn = new Button(this);
        filtersBtn.setText("Manage Tracking Filters");
        filtersBtn.setOnClickListener(v ->
                startActivity(new Intent(this, FilterConfigActivity.class)));
        root.addView(filtersBtn);

        TextView filtersHint = new TextView(this);
        filtersHint.setPadding(0, dp(4), 0, dp(24));
        filtersHint.setText(
                "The stock tracking-parameter list won't catch everything. "
                        + "Add your own regex filters here for trackers it misses.");
        root.addView(filtersHint);

        if (BuildConfig.PAW_ENABLED) {
            Button gptBtn = new Button(this);
            gptBtn.setText("GPT Cleaning");
            gptBtn.setOnClickListener(v ->
                    startActivity(new Intent().setClassName(this, "dev.nobugs.sharerouter.PawActivity")));
            root.addView(gptBtn);

            TextView gptHint = new TextView(this);
            gptHint.setPadding(0, dp(4), 0, 0);
            gptHint.setText(
                    "Experimental: runs shared text through a small on-device "
                            + "language model instead of fixed regex rules. Slower, "
                            + "and only worth it for cases the filters above can't "
                            + "express.");
            root.addView(gptHint);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
