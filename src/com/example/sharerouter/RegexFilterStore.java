package com.example.sharerouter;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

class RegexFilterStore {

    private static final String PREFS_NAME = "regex_filters";
    private static final String KEY_FILTERS = "filters";

    static List<String> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_FILTERS, "[]");
        List<String> filters = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                filters.add(arr.getString(i));
            }
        } catch (Exception e) {
            // corrupt prefs; treat as no saved filters
        }
        return filters;
    }

    static void save(Context context, List<String> filters) {
        JSONArray arr = new JSONArray();
        for (String filter : filters) {
            arr.put(filter);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FILTERS, arr.toString()).apply();
    }
}
