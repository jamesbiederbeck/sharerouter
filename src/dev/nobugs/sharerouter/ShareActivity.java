package dev.nobugs.sharerouter;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ShareActivity extends Activity {

    // Configure your Wallabag instance URL here (no trailing slash)
    private static final String WALLABAG_BASE_URL = "https://your-wallabag.example.com";

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private String sharedText;
    private TextView resultView;
    private LinearLayout actionButtons;
    private String cleanedUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedText = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText == null) {
            finish();
            return;
        }

        buildUI();
    }

    private void buildUI() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(48), pad, pad);

        // Shared text preview
        TextView preview = new TextView(this);
        String displayText = sharedText.length() > 300
                ? sharedText.substring(0, 300) + "\u2026"
                : sharedText;
        preview.setText(displayText);
        preview.setPadding(0, 0, 0, dp(24));
        root.addView(preview);

        // Tracking cleaner
        Button cleanBtn = new Button(this);
        cleanBtn.setText("Clean Tracking URL");
        cleanBtn.setOnClickListener(v -> cleanTracking());
        root.addView(cleanBtn);

        // Wallabag
        Button wallabagBtn = new Button(this);
        wallabagBtn.setText("Add to Wallabag");
        wallabagBtn.setOnClickListener(v -> addToWallabag());
        root.addView(wallabagBtn);

        // Inline result (cleaned URL, errors)
        resultView = new TextView(this);
        resultView.setPadding(0, dp(16), 0, 0);
        resultView.setTextIsSelectable(true);
        root.addView(resultView);

        // Action buttons shown after cleaning
        actionButtons = new LinearLayout(this);
        actionButtons.setOrientation(LinearLayout.HORIZONTAL);
        actionButtons.setPadding(0, dp(8), 0, 0);
        actionButtons.setVisibility(View.GONE);

        Button copyBtn = new Button(this);
        copyBtn.setText("Copy");
        copyBtn.setOnClickListener(v -> copyCleanedUrl());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(4));
        copyBtn.setLayoutParams(lp);
        actionButtons.addView(copyBtn);

        Button openBtn = new Button(this);
        openBtn.setText("Open");
        openBtn.setOnClickListener(v -> openCleanedUrl());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMarginEnd(dp(4));
        openBtn.setLayoutParams(lp2);
        actionButtons.addView(openBtn);

        Button shareBtn = new Button(this);
        shareBtn.setText("Share");
        shareBtn.setOnClickListener(v -> shareCleanedUrl());
        shareBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actionButtons.addView(shareBtn);

        root.addView(actionButtons);

        setContentView(root);
    }

    private void cleanTracking() {
        String url = extractUrl(sharedText);
        if (url == null) {
            resultView.setText("No URL found.");
            actionButtons.setVisibility(View.GONE);
            return;
        }
        cleanedUrl = stripTrackingParams(url);
        cleanedUrl = applyCustomFilters(cleanedUrl);
        resultView.setText(cleanedUrl);
        actionButtons.setVisibility(View.VISIBLE);
    }

    private void copyCleanedUrl() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("url", cleanedUrl));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private void openCleanedUrl() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(cleanedUrl)));
    }

    private void shareCleanedUrl() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, cleanedUrl);
        startActivity(Intent.createChooser(intent, "Share via"));
    }

    private void addToWallabag() {
        String url = extractUrl(sharedText);
        if (url == null) {
            resultView.setText("No URL found.");
            return;
        }
        try {
            String encoded = URLEncoder.encode(url, "UTF-8");
            Uri target = Uri.parse(WALLABAG_BASE_URL + "/?action=add&url=" + encoded);
            startActivity(new Intent(Intent.ACTION_VIEW, target));
            finish();
        } catch (Exception e) {
            resultView.setText("Error: " + e.getMessage());
        }
    }

    private String extractUrl(String text) {
        Matcher m = URL_PATTERN.matcher(text);
        return m.find() ? m.group() : null;
    }

    private String stripTrackingParams(String url) {
        try {
            return TrackingUrlCleaner.stripTrackingParams(url);
        } catch (Exception e) {
            return url;
        }
    }

    private String applyCustomFilters(String url) {
        for (String regex : RegexFilterStore.load(this)) {
            try {
                url = url.replaceAll(regex, "");
            } catch (PatternSyntaxException e) {
                // skip filters that no longer compile
            }
        }
        return url;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
