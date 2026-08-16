package dev.nobugs.sharerouter;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regex-filter tester and manager for the tracking-URL cleanup feature.
 * Moved out of MainActivity so the launcher screen can be a short intro
 * instead of dropping straight into configuration.
 */
public class FilterConfigActivity extends Activity {

    private EditText inputField;
    private EditText regexField;
    private TextView outputView;
    private LinearLayout filterListContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
        refreshFilterList();
    }

    private void buildUI() {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(48), pad, pad);

        root.addView(TopBar.build(this, FilterConfigActivity.class, "Tracking Filters"));

        TextView testLabel = new TextView(this);
        testLabel.setText("Test a regex");
        root.addView(testLabel);

        inputField = new EditText(this);
        inputField.setHint("String to clean");
        root.addView(inputField);

        regexField = new EditText(this);
        regexField.setHint("Regex to apply");
        root.addView(regexField);

        TextView outputLabel = new TextView(this);
        outputLabel.setText("Output");
        outputLabel.setPadding(0, dp(12), 0, 0);
        root.addView(outputLabel);

        outputView = new TextView(this);
        outputView.setPadding(0, dp(4), 0, dp(8));
        outputView.setTextIsSelectable(true);
        root.addView(outputView);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateOutput();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        inputField.addTextChangedListener(watcher);
        regexField.addTextChangedListener(watcher);

        Button saveBtn = new Button(this);
        saveBtn.setText("Save as Filter");
        saveBtn.setOnClickListener(v -> saveFilter());
        root.addView(saveBtn);

        TextView filtersLabel = new TextView(this);
        filtersLabel.setText("Saved Filters");
        filtersLabel.setPadding(0, dp(24), 0, dp(8));
        root.addView(filtersLabel);

        filterListContainer = new LinearLayout(this);
        filterListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(filterListContainer);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void updateOutput() {
        String result = inputField.getText().toString();
        for (String savedRegex : RegexFilterStore.load(this)) {
            try {
                result = result.replaceAll(savedRegex, "");
            } catch (PatternSyntaxException e) {
                // skip filters that no longer compile
            }
        }

        String regex = regexField.getText().toString();
        if (!regex.isEmpty()) {
            try {
                result = result.replaceAll(regex, "");
            } catch (PatternSyntaxException e) {
                outputView.setText("Invalid regex: " + e.getMessage());
                return;
            }
        }

        outputView.setText(result);
    }

    private void saveFilter() {
        String regex = regexField.getText().toString();
        if (regex.isEmpty()) {
            Toast.makeText(this, "Enter a regex first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            Toast.makeText(this, "Invalid regex: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        List<String> filters = RegexFilterStore.load(this);
        if (filters.contains(regex)) {
            Toast.makeText(this, "Filter already exists", Toast.LENGTH_SHORT).show();
            return;
        }
        filters.add(regex);
        RegexFilterStore.save(this, filters);
        refreshFilterList();
        Toast.makeText(this, "Filter saved", Toast.LENGTH_SHORT).show();
    }

    private void refreshFilterList() {
        if (outputView != null) {
            updateOutput();
        }
        filterListContainer.removeAllViews();
        List<String> filters = RegexFilterStore.load(this);
        if (filters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No filters yet.");
            filterListContainer.addView(empty);
            return;
        }
        for (int i = 0; i < filters.size(); i++) {
            String filter = filters.get(i);
            int index = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(4), 0, dp(4));

            TextView label = new TextView(this);
            label.setText(filter);
            label.setTextIsSelectable(true);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            label.setLayoutParams(lp);
            row.addView(label);

            Button upBtn = new Button(this);
            upBtn.setText("↑");
            upBtn.setEnabled(index > 0);
            upBtn.setOnClickListener(v -> moveFilter(index, index - 1));
            row.addView(upBtn);

            Button downBtn = new Button(this);
            downBtn.setText("↓");
            downBtn.setEnabled(index < filters.size() - 1);
            downBtn.setOnClickListener(v -> moveFilter(index, index + 1));
            row.addView(downBtn);

            Button removeBtn = new Button(this);
            removeBtn.setText("Remove");
            removeBtn.setOnClickListener(v -> removeFilter(index));
            row.addView(removeBtn);

            filterListContainer.addView(row);
        }
    }

    private void moveFilter(int fromIndex, int toIndex) {
        List<String> filters = RegexFilterStore.load(this);
        if (toIndex < 0 || toIndex >= filters.size()) {
            return;
        }
        String filter = filters.remove(fromIndex);
        filters.add(toIndex, filter);
        RegexFilterStore.save(this, filters);
        refreshFilterList();
    }

    private void removeFilter(int index) {
        List<String> filters = RegexFilterStore.load(this);
        filters.remove(index);
        RegexFilterStore.save(this, filters);
        refreshFilterList();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
