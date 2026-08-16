package dev.nobugs.sharerouter;

/**
 * Plain-JVM test runner for TrackingUrlCleaner (no JUnit — android.jar is a
 * stub-only SDK jar, so testing anything that touches android.net.Uri
 * would need Robolectric; this deliberately stays framework-free by
 * exercising only the pure-Java cleaner). Run via `make test`.
 */
public final class TrackingUrlCleanerTest {

    private static int failures = 0;

    public static void main(String[] args) {
        // "is" tracking param, YouTube-style share links.
        check("strips a lone is= param",
                "https://youtu.be/dQw4w9WgXcQ?is=9coizs2-QX7MX",
                "https://youtu.be/dQw4w9WgXcQ");

        check("strips is= but keeps a preceding non-tracking param",
                "https://youtu.be/dQw4w9WgXcQ?t=43&is=EqsQvKcA3_Wo",
                "https://youtu.be/dQw4w9WgXcQ?t=43");

        check("strips is= but keeps a following non-tracking param",
                "https://youtube.com/dQw4w9WgXcQ?is=flibbertygibbet0ekacA3_Wo&t=69",
                "https://youtube.com/dQw4w9WgXcQ?t=69");

        // Pre-existing stock filters, kept as regression coverage.
        check("strips utm_source",
                "https://example.com/page?utm_source=newsletter&id=42",
                "https://example.com/page?id=42");

        check("strips si (YouTube's real share-id tracking param)",
                "https://youtu.be/dQw4w9WgXcQ?si=abc123",
                "https://youtu.be/dQw4w9WgXcQ");

        check("leaves a URL with no query string untouched",
                "https://example.com/page",
                "https://example.com/page");

        check("leaves a URL with no tracking params untouched",
                "https://example.com/page?id=42&sort=asc",
                "https://example.com/page?id=42&sort=asc");

        if (failures > 0) {
            System.err.println(failures + " test(s) failed.");
            System.exit(1);
        }
        System.out.println("All TrackingUrlCleaner tests passed.");
    }

    private static void check(String description, String input, String expected) {
        String actual = TrackingUrlCleaner.stripTrackingParams(input);
        if (expected.equals(actual)) {
            System.out.println("PASS: " + description);
        } else {
            failures++;
            System.out.println("FAIL: " + description);
            System.out.println("  input:    " + input);
            System.out.println("  expected: " + expected);
            System.out.println("  actual:   " + actual);
        }
    }
}
