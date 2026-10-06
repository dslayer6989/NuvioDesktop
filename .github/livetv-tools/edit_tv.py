"""Adds the Android TV layer on top of a Nuvio Mobile tree that already has android-live-tv.patch
and phone_cleanup.py applied: the TV launcher entry and banner, TV detection, and a TV guide layout.

Usage: python3 edit_tv.py <root of the Nuvio Mobile checkout>
Safe to run twice. Stops with an error if the code it expects is not there.

The TV flag lives in androidMain, so the Windows build (which compiles commonMain) is untouched.
"""
import pathlib
import sys

ROOT = pathlib.Path(sys.argv[1])
LIVETV = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/features/livetv"
GRID = LIVETV / "LiveTvGuideGrid.kt"
SCREEN = LIVETV / "LiveTvScreen.kt"
ANDROID_LIVETV = ROOT / "composeApp/src/androidMain/kotlin/com/nuvio/app/features/livetv"
DETECT = ANDROID_LIVETV / "LiveTvTelevision.android.kt"
MANIFEST = ROOT / "androidApp/src/main/AndroidManifest.xml"
BANNER = ROOT / "composeApp/src/androidMain/res/drawable/live_tv_banner.xml"
MAIN_ACTIVITY = ROOT / "composeApp/src/androidMain/kotlin/com/nuvio/app/MainActivity.kt"


def fail(message):
    sys.exit("ERROR: " + message)


def read(path):
    if not path.exists():
        fail(str(path) + " does not exist")
    raw = path.read_bytes().decode("utf-8")
    return raw.replace("\r\n", "\n"), "\r\n" in raw


def write(path, text, crlf=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    if crlf:
        text = text.replace("\n", "\r\n")
    path.write_bytes(text.encode("utf-8"))


def replace_once(text, old, new, what):
    count = text.count(old)
    if count != 1:
        fail("expected exactly one match for " + what + " but found " + str(count))
    return text.replace(old, new)


# ---------------------------------------------------------------- TV detection (androidMain only)
DETECT_SOURCE = """package com.nuvio.app.features.livetv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Whether this device is a television: Android TV, Google TV or Fire TV.
 *
 * All three report the television UI mode. Some boxes only declare the leanback feature, and Fire TV
 * devices also declare their own feature, so any of the three counts. Never throws: if the system
 * cannot be asked, the app keeps its normal phone layout.
 */
internal object LiveTvTelevision {
    @Volatile
    var isTelevision: Boolean = false
        private set

    /** Fire TV does not show app-provided home screen rows, so those are skipped there. */
    @Volatile
    var isFireTv: Boolean = false
        private set

    fun apply(context: Context) {
        val result = runCatching {
            val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val pm = context.packageManager
            val television = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature(FireTvFeature)
            television to pm.hasSystemFeature(FireTvFeature)
        }.getOrNull()
        isTelevision = result?.first ?: false
        isFireTv = result?.second ?: false
    }

    private const val FireTvFeature = "amazon.hardware.fire_tv"
}
"""

if DETECT.exists():
    print("TV detection already present: " + DETECT.name)
else:
    write(DETECT, DETECT_SOURCE)
    print("Added " + DETECT.name)

text, crlf = read(MAIN_ACTIVITY)
if "LiveTvTelevision.apply" in text:
    print("TV detection call already present: " + MAIN_ACTIVITY.name)
else:
    anchor = "        com.nuvio.app.features.livetv.LiveTvStorage.initialize(applicationContext)\n"
    text = replace_once(
        text,
        anchor,
        anchor + "        com.nuvio.app.features.livetv.LiveTvTelevision.apply(applicationContext)\n",
        "the Live TV storage initialization in MainActivity",
    )
    write(MAIN_ACTIVITY, text, crlf)
    print("Edited " + MAIN_ACTIVITY.name)

# ---------------------------------------------------------------- guide grid: TV metrics and focus
text, crlf = read(GRID)
if "val Television" in text:
    print("Television metrics already present: " + GRID.name)
else:
    text = replace_once(
        text,
        "            compact = true,\n        )\n    }\n}\n",
        "            compact = true,\n        )\n"
        "        /** Couch-distance layout for Android TV: bigger rows, wider slots, larger channel column. */\n"
        "        val Television = LiveTvGridMetrics(\n"
        "            channelColumnWidth = 300.dp,\n"
        "            rowHeight = 96.dp,\n"
        "            halfHourWidth = 260.dp,\n"
        "            headerHeight = 56.dp,\n"
        "            compact = false,\n"
        "        )\n"
        "    }\n}\n",
        "the end of LiveTvGridMetrics",
    )
    text = replace_once(
        text,
        "    cellWidthPx: Float,\n    scrollXPx: () -> Float,\n    onClick: () -> Unit,\n) {\n    val palette = LiveTvColors.current\n    val airing",
        "    cellWidthPx: Float,\n    scrollXPx: () -> Float,\n    onClick: () -> Unit,\n    television: Boolean = false,\n) {\n    val palette = LiveTvColors.current\n    val airing",
        "the program cell signature",
    )
    text = replace_once(
        text,
        "                    Modifier.border(1.5.dp, LiveTvColors.AccentGradient, shape)\n",
        "                    Modifier.border(if (television) 3.dp else 1.5.dp, LiveTvColors.AccentGradient, shape)\n",
        "the focus border",
    )
    text = replace_once(
        text,
        "                    fontSize = if (compact) 13.sp else 14.sp,\n",
        "                    fontSize = if (television) 18.sp else if (compact) 13.sp else 14.sp,\n",
        "the program title size",
    )
    text = replace_once(
        text,
        "                        compact = metrics.compact,\n",
        "                        compact = metrics.compact,\n                        television = LiveTvTelevision.isTelevision,\n",
        "the program cell call",
    )
    write(GRID, text, crlf)
    print("Edited " + GRID.name)

# ---------------------------------------------------------------- guide screen: pick the TV metrics
text, crlf = read(SCREEN)
if "LiveTvGridMetrics.Television" in text:
    print("Television wiring already present: " + SCREEN.name)
else:
    text = replace_once(
        text,
        "    val metrics = if (compact) LiveTvGridMetrics.Compact else LiveTvGridMetrics.Desktop\n",
        "    val metrics = when {\n"
        "        LiveTvTelevision.isTelevision -> LiveTvGridMetrics.Television\n"
        "        compact -> LiveTvGridMetrics.Compact\n"
        "        else -> LiveTvGridMetrics.Desktop\n"
        "    }\n",
        "the metrics choice",
    )
    write(SCREEN, text, crlf)
    print("Edited " + SCREEN.name)

# ---------------------------------------------------------------- TV launcher entry and banner
BANNER_SOURCE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Android TV launcher banner (320x180dp): the app icon on a dark tile. -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <gradient
                android:angle="0"
                android:endColor="#FF1A1A1A"
                android:startColor="#FF0D0D0D" />
            <size android:width="320dp" android:height="180dp" />
        </shape>
    </item>
    <item
        android:width="150dp"
        android:height="150dp"
        android:drawable="@mipmap/ic_launcher_foreground"
        android:gravity="center" />
</layer-list>
"""
if BANNER.exists():
    print("Banner already present: " + BANNER.name)
else:
    write(BANNER, BANNER_SOURCE)
    print("Added " + BANNER.name)

text, crlf = read(MANIFEST)
if "LEANBACK_LAUNCHER" in text:
    print("Manifest already has the TV launcher entry: " + MANIFEST.name)
else:
    text = replace_once(
        text,
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n\n    <uses-permission android:name="android.permission.INTERNET" />\n',
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n\n'
        '    <!-- Android TV, Google TV and Fire TV; the app still installs on devices without these features. -->\n'
        '    <uses-feature android:name="android.software.leanback" android:required="false" />\n'
        '    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />\n\n'
        '    <uses-permission android:name="android.permission.INTERNET" />\n',
        "the start of the manifest",
    )
    text = replace_once(
        text,
        '            android:name="com.nuvio.app.launcher.AppIconDefault"\n',
        '            android:name="com.nuvio.app.launcher.AppIconDefault"\n            android:banner="@drawable/live_tv_banner"\n',
        "the default launcher activity",
    )
    text = replace_once(
        text,
        '                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n        </activity>\n\n        <activity\n            android:name="com.nuvio.app.launcher.AppIconArcticBlue"',
        '                <category android:name="android.intent.category.LAUNCHER" />\n                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />\n            </intent-filter>\n        </activity>\n\n        <activity\n            android:name="com.nuvio.app.launcher.AppIconArcticBlue"',
        "the default launcher intent filter",
    )
    write(MANIFEST, text, crlf)
    print("Edited " + MANIFEST.name)
