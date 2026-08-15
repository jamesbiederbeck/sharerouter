package com.example.sharerouter;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

/** Shared hamburger/title bar used by MainActivity and PawActivity to switch between them. */
final class TopBar {

    private TopBar() {}

    static LinearLayout build(Activity activity, Class<?> currentActivity, String title) {
        int dp8 = Math.round(8 * activity.getResources().getDisplayMetrics().density);

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, dp8);

        Button hamburger = new Button(activity);
        hamburger.setText("☰");
        bar.addView(hamburger);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setPadding(dp8, 0, 0, 0);
        bar.addView(titleView);

        hamburger.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(activity, hamburger);
            popup.getMenu().add("Regex Filters");
            popup.getMenu().add("PAW Inference");
            popup.setOnMenuItemClickListener(item -> {
                Class<?> target = "Regex Filters".equals(item.getTitle())
                        ? MainActivity.class
                        : PawActivity.class;
                if (target != currentActivity) {
                    activity.startActivity(new Intent(activity, target));
                    activity.finish();
                }
                return true;
            });
            popup.show();
        });

        return bar;
    }
}
