# Share Router

An Android share target that strips tracking parameters out of URLs before you copy, open, or re-share them.

Share a link to Share Router the way you'd share it to any other app. It finds the URL in the shared text, strips known tracking parameters, and gives you a clean link to copy, open, or share onward.

## Removing tracking URLs

This is the app's main job. Share any link containing a URL — from a browser, a messaging app, YouTube, wherever — and pick **Share Router**. Tap **Clean Tracking URL** and you get the same link with tracking noise removed:

```
https://youtu.be/dQw4w9WgXcQ?is=9coizs2-QX7MX
→ https://youtu.be/dQw4w9WgXcQ

https://youtu.be/dQw4w9WgXcQ?t=43&is=EqsQvKcA3_Wo
→ https://youtu.be/dQw4w9WgXcQ?t=43
```

Non-tracking parameters (like YouTube's `t=` timestamp) are left alone — only known tracking keys are removed.

Once cleaned, you can:
- **Copy** the URL to your clipboard
- **Open** it directly
- **Share** it onward to another app
- **Add to Wallabag**, if you've configured a Wallabag instance (see below)

### Stock tracking parameters

Stripped automatically, no setup required:

`utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, `utm_id`, `utm_source_platform`, `fbclid`, `gclid`, `msclkid`, `mc_eid`, `_openstat`, `yclid`, `igshid`, `dclid`, `gbraid`, `wbraid`, `si`, `is`

Matching is case-insensitive and covers common ad/analytics/social trackers (Google, Facebook, Microsoft, YouTube's `si`/`is` share IDs, etc). See `TrackingUrlCleaner.java` for the exact set.

### Custom filters

The stock list won't catch everything — new trackers show up constantly, and some are site-specific. Open the app directly (the launcher icon, not via share) to manage custom regex filters:

1. Type a string to test and a regex to apply; the output preview updates live.
2. Tap **Save as Filter** to keep it — it'll be applied automatically on every future cleanup, in addition to the stock list.
3. Saved filters apply in order, and you can reorder them (↑/↓) or remove them. Order matters when filters could interact — e.g. stripping `is=\w+` and then a trailing `?` needs the `is=` filter to run first.

Filters are subtractive: whatever the regex matches gets deleted from the string. A filter like `is=\w+` removes just the tracking parameter's key/value pair, wherever it appears.

### Configuring Wallabag (optional)

`ShareActivity.java` has a `WALLABAG_BASE_URL` constant near the top — set it to your own Wallabag instance (no trailing slash) to enable the **Add to Wallabag** button. Leave it as the placeholder if you don't use Wallabag; the tracking-cleanup features work regardless.

## Other handlers

Share Router also has a couple of unrelated share/open targets bundled in, since it's a general share-routing app:

- **GPX files** — opening or sharing a `.gpx` file routes to a simple map view.
- **PAW Inference** (optional, see below) — runs shared text through a locally-compiled [ProgramAsWeights](https://github.com/programasweights/programasweights-js) program via on-device llama.cpp inference.

## Building

No Gradle, no IDE — just `make`. From this directory:

```bash
make keystore              # one-time: generate a debug signing key
make                       # build a signed APK (aligned.apk)
make install DEVICE=<ip>:<port>   # build and install via adb
make run DEVICE=<ip>:<port>       # install and launch
make test                  # run the pure-Java unit tests (no device needed)
make clean                 # remove build artifacts
```

`DEVICE` auto-detects a connected/already-paired `adb` device if omitted; pass `DEVICE=<ip>:<port>` (or set `ANDROID_DEVICE`) to target a specific one.

### Build variants

```bash
make PAW=0   # minimal build (~1MB): tracking-URL cleaning, GPX handling — no PAW/llama.cpp
make PAW=1   # full build (default, ~8.5MB): adds on-device PAW inference
```

`PAW=1` needs the Android NDK and a [llama.cpp](https://github.com/ggml-org/llama.cpp) checkout built for `arm64-v8a` (see `jni/CMakeLists.txt` and the `LLAMA_CPP_DIR`/`NDK` Makefile variables). `PAW=0` needs neither — if you only care about tracking-URL cleaning, that's the simpler build.

CI (`.github/workflows/release.yml`) builds and releases both variants on every tag push.

## License

App code has no explicit license file yet. Bundled dependencies (llama.cpp, programasweights-js) are MIT-licensed — see the in-app **Licenses** screen (hamburger menu, full build only) or `assets/licenses/`.
