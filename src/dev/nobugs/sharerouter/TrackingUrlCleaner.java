package dev.nobugs.sharerouter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Strips known tracking query parameters from a URL. Pure Java (no
 * android.* imports) so it can be unit tested on a plain JVM — android.jar
 * is a stub-only SDK jar (real Uri parsing isn't present outside a device
 * or Robolectric), so this intentionally avoids android.net.Uri.
 */
final class TrackingUrlCleaner {

    static final Set<String> TRACKING_PARAMS = new HashSet<>(Arrays.asList(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "utm_id", "utm_source_platform",
            "fbclid", "gclid", "msclkid", "mc_eid", "_openstat",
            "yclid", "igshid", "dclid", "gbraid", "wbraid",
            "si", "is"
    ));

    private TrackingUrlCleaner() {}

    static String stripTrackingParams(String url) {
        int queryIdx = url.indexOf('?');
        if (queryIdx < 0) {
            return url;
        }

        String base = url.substring(0, queryIdx);
        String rest = url.substring(queryIdx + 1);

        String fragment = "";
        int fragIdx = rest.indexOf('#');
        if (fragIdx >= 0) {
            fragment = rest.substring(fragIdx);
            rest = rest.substring(0, fragIdx);
        }

        StringBuilder kept = new StringBuilder();
        for (String pair : rest.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (TRACKING_PARAMS.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(pair);
        }

        String result = kept.length() > 0 ? base + "?" + kept : base;
        return result + fragment;
    }
}
