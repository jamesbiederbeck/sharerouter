package com.example.sharerouter;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Xml;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GpxActivity extends Activity {

    private static class Waypoint {
        final double lat, lon;
        final String name;
        Waypoint(double lat, double lon, String name) {
            this.lat = lat;
            this.lon = lon;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri uri = resolveUri(getIntent());
        if (uri == null) {
            finish();
            return;
        }

        List<Waypoint> waypoints = parseGpx(uri);
        buildUI(waypoints, getFileName(uri));
    }

    // --- UI ---

    private void buildUI(List<Waypoint> waypoints, String fileName) {
        int pad = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(32), pad, pad);

        TextView title = new TextView(this);
        title.setText(fileName != null ? fileName : "GPX File");
        title.setTextSize(18);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < waypoints.size(); i++) {
            final Waypoint wp = waypoints.get(i);
            final int idx = i + 1;

            if (i > 0) {
                View div = new View(this);
                div.setBackgroundColor(0xFFDDDDDD);
                list.addView(div, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }

            TextView item = new TextView(this);
            String label = wp.name != null ? wp.name : "Waypoint " + idx;
            item.setText(label + "\n" + String.format("%.6f, %.6f", wp.lat, wp.lon));
            item.setPadding(0, dp(12), 0, dp(12));
            item.setOnClickListener(v -> {
                openWaypoint(wp);
            });
            list.addView(item);
        }

        if (waypoints.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No waypoints found in this file.");
            empty.setPadding(0, dp(12), 0, 0);
            list.addView(empty);
        }

        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button navigate = new Button(this);
        navigate.setText("Navigate");
        navigate.setEnabled(!waypoints.isEmpty());
        navigate.setOnClickListener(v -> {
            openSequence(waypoints);
            finish();
        });
        root.addView(navigate);

        setContentView(root);
    }

    // --- Maps intents ---

    private void openWaypoint(Waypoint wp) {
        String label = wp.name != null ? Uri.encode(wp.name) : "";
        Uri geo = Uri.parse("geo:" + wp.lat + "," + wp.lon
                + "?q=" + wp.lat + "," + wp.lon + "(" + label + ")");
        launchGeo(geo);
    }

    private void openSequence(List<Waypoint> wps) {
        if (wps.isEmpty()) return;
        if (wps.size() == 1) {
            openWaypoint(wps.get(0));
            return;
        }
        // Multi-stop: https://www.google.com/maps/dir/lat1,lon1/lat2,lon2/...
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/");
        for (Waypoint wp : wps) {
            url.append(wp.lat).append(",").append(wp.lon).append("/");
        }
        Uri mapsUri = Uri.parse(url.toString());
        Intent intent = new Intent(Intent.ACTION_VIEW, mapsUri);
        intent.setPackage("com.google.android.apps.maps");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW, mapsUri));
        }
    }

    private void launchGeo(Uri geo) {
        Intent intent = new Intent(Intent.ACTION_VIEW, geo);
        intent.setPackage("com.google.android.apps.maps");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, geo), "Open with"));
        }
    }

    // --- Intent resolution ---

    @SuppressWarnings("deprecation")
    private Uri resolveUri(Intent intent) {
        // ACTION_VIEW: URI is in getData()
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            return intent.getData();
        }
        // ACTION_SEND: URI is in EXTRA_STREAM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        } else {
            return (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
    }

    // --- GPX parsing ---

    private List<Waypoint> parseGpx(Uri uri) {
        List<Waypoint> result = new ArrayList<>();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return result;
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);

            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = localName(parser.getName());
                    if (tag.equals("wpt") || tag.equals("rtept") || tag.equals("trkpt")) {
                        Waypoint wp = parsePoint(parser);
                        if (wp != null) result.add(wp);
                    }
                }
                event = parser.next();
            }
        } catch (Exception ignored) {
            // return what we have
        }
        return result;
    }

    private Waypoint parsePoint(XmlPullParser parser) throws Exception {
        String latStr = parser.getAttributeValue(null, "lat");
        String lonStr = parser.getAttributeValue(null, "lon");
        if (latStr == null || lonStr == null) return null;

        double lat, lon;
        try {
            lat = Double.parseDouble(latStr);
            lon = Double.parseDouble(lonStr);
        } catch (NumberFormatException e) {
            return null;
        }

        String name = null;
        int depth = parser.getDepth();
        int event = parser.next();

        while (!(event == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
            if (event == XmlPullParser.START_TAG
                    && localName(parser.getName()).equals("name")) {
                String text = parser.nextText().trim();
                if (!text.isEmpty()) name = text;
            }
            event = parser.next();
        }

        return new Waypoint(lat, lon, name);
    }

    private String localName(String qName) {
        if (qName == null) return "";
        int colon = qName.indexOf(':');
        return (colon >= 0 ? qName.substring(colon + 1) : qName).toLowerCase();
    }

    // --- Helpers ---

    private String getFileName(Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col >= 0) return c.getString(col);
                }
            } catch (Exception ignored) {}
        }
        return uri.getLastPathSegment();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
