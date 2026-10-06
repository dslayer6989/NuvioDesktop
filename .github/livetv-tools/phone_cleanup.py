"""Makes the phone Android build phone-only: removes the Android TV launcher pieces from a Nuvio
Mobile source tree that already has android-live-tv.patch applied.

Usage: python3 phone_cleanup.py <root of the Nuvio Mobile checkout>
Safe to run twice. Stops with an error if the code it expects is not there.
"""
import pathlib
import sys

ROOT = pathlib.Path(sys.argv[1])
MANIFEST = ROOT / "androidApp/src/main/AndroidManifest.xml"
BANNER = ROOT / "composeApp/src/androidMain/res/drawable/live_tv_banner.xml"


def fail(message):
    sys.exit("ERROR: " + message)


def read(path):
    if not path.exists():
        fail(str(path) + " does not exist")
    raw = path.read_bytes().decode("utf-8")
    return raw.replace("\r\n", "\n"), "\r\n" in raw


def write(path, text, crlf):
    if crlf:
        text = text.replace("\n", "\r\n")
    path.write_bytes(text.encode("utf-8"))


REMOVALS = [
    (
        '    <!-- Installs on Android TV too; phones and tablets are unaffected. -->\n'
        '    <uses-feature android:name="android.software.leanback" android:required="false" />\n'
        '    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />\n'
        "\n",
        "the leanback and touchscreen uses-feature lines",
    ),
    ('            android:banner="@drawable/live_tv_banner"\n', "the TV banner attribute"),
    (
        '                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />\n',
        "the leanback launcher category",
    ),
]

text, crlf = read(MANIFEST)
if "leanback" not in text.lower() and "live_tv_banner" not in text:
    print("Manifest is already phone-only: " + MANIFEST.name)
else:
    for old, what in REMOVALS:
        if text.count(old) != 1:
            fail("expected exactly one match for " + what + " but found " + str(text.count(old)))
        text = text.replace(old, "")
    if "leanback" in text.lower() or "live_tv_banner" in text:
        fail("TV entries are still in the manifest after the cleanup")
    write(MANIFEST, text, crlf)
    print("Removed the Android TV entries from " + MANIFEST.name)

if BANNER.exists():
    BANNER.unlink()
    print("Removed " + BANNER.name)
else:
    print("Banner already removed: " + BANNER.name)
