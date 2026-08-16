package dev.nobugs.sharerouter;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

/**
 * Shared hamburger/title bar used across activities to navigate between
 * them. "GPT Cleaning" and "Licenses" only appear when built with PAW=1
 * (BuildConfig.PAW_ENABLED) — Home and Tracking Filters are always present.
 *
 * Navigates to the PAW-only activities by class name string rather than a
 * .class literal so this file compiles unchanged whether or not those
 * classes are part of the build (the minimal/PAW=0 build excludes them).
 */
final class TopBar {

    private static final String MAIN_ACTIVITY_CLASS = "dev.nobugs.sharerouter.MainActivity";
    private static final String FILTER_CONFIG_ACTIVITY_CLASS = "dev.nobugs.sharerouter.FilterConfigActivity";
    private static final String PAW_ACTIVITY_CLASS = "dev.nobugs.sharerouter.PawActivity";
    private static final String LICENSES_ACTIVITY_CLASS = "dev.nobugs.sharerouter.LicensesActivity";

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
            popup.getMenu().add("Home");
            popup.getMenu().add("Tracking Filters");
            if (BuildConfig.PAW_ENABLED) {
                popup.getMenu().add("GPT Cleaning");
                popup.getMenu().add("Licenses");
            }
            popup.setOnMenuItemClickListener(item -> {
                String choice = item.getTitle().toString();

                if ("Licenses".equals(choice)) {
                    // A subscreen, not a tab — push it onto the back stack
                    // rather than finish()ing the current activity.
                    activity.startActivity(new Intent().setClassName(activity, LICENSES_ACTIVITY_CLASS));
                    return true;
                }

                String targetClass;
                switch (choice) {
                    case "Home":
                        targetClass = MAIN_ACTIVITY_CLASS;
                        break;
                    case "Tracking Filters":
                        targetClass = FILTER_CONFIG_ACTIVITY_CLASS;
                        break;
                    case "GPT Cleaning":
                        targetClass = PAW_ACTIVITY_CLASS;
                        break;
                    default:
                        return true;
                }

                if (!currentActivity.getName().equals(targetClass)) {
                    activity.startActivity(new Intent().setClassName(activity, targetClass));
                    activity.finish();
                }
                return true;
            });
            popup.show();
        });

        return bar;
    }
}
