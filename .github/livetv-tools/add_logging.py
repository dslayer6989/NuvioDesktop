"""Adds persistent file-based logging to a Nuvio source tree.

Usage: python3 add_logging.py <root of the Nuvio checkout> <desktop|mobile>

Writes a commonMain expect object plus a Kermit LogWriter that appends to a
rotating file, the platform actuals (android/desktop/ios), and installs the
writer from the platform entry point. Safe to run twice: existing files are
left alone and the entry-point edit is skipped when already present.

Both NuvioDesktop and NuvioMobile declare android, desktop and ios targets, so
all four files are written for both platforms. An unused actual is inert; a
missing actual for a declared target is a compile error.
"""
import pathlib
import sys

if len(sys.argv) < 2:
    sys.exit("ERROR: usage: add_logging.py <root> <desktop|mobile>")

ROOT = pathlib.Path(sys.argv[1])
PLATFORM = sys.argv[2] if len(sys.argv) > 2 else "mobile"
if PLATFORM not in ("desktop", "mobile"):
    sys.exit("ERROR: platform must be 'desktop' or 'mobile'")

COMMON_DIR = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/core/logging"
ANDROID_DIR = ROOT / "composeApp/src/androidMain/kotlin/com/nuvio/app/core/logging"
DESKTOP_DIR = ROOT / "composeApp/src/desktopMain/kotlin/com/nuvio/app/core/logging"
IOS_DIR = ROOT / "composeApp/src/iosMain/kotlin/com/nuvio/app/core/logging"

COMMON_SOURCE = r'''package com.nuvio.app.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlin.concurrent.Volatile

/**
 * Where this platform keeps its log files, or null when the platform has no writable log
 * directory. The desktop actual returns %LOCALAPPDATA%\Nuvio\logs on Windows; the Android
 * actual returns the app-specific external files dir.
 */
internal expect object PlatformLogFile {
    val directory: String?

    /** A wall-clock timestamp for one log line, e.g. "2026-10-07 01:22:34.123". */
    fun timestamp(): String

    /** Appends one already-formatted line, rotating the file when it grows past the cap. Never throws. */
    fun append(line: String)
}

/**
 * Installs a file-writing Kermit LogWriter so the app keeps a persistent log on disk.
 * Call once, as early as possible, from the platform entry point.
 */
internal object AppLogging {
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        // Keep the platform writer (console on desktop, logcat on Android) and add the file.
        Logger.setMinSeverity(Severity.Info)
        Logger.addLogWriter(FileLogWriter)
    }

    /** The directory the log file is written to, or null when the platform has none. */
    val directory: String? get() = PlatformLogFile.directory
}

private object FileLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val line = buildString {
            append(PlatformLogFile.timestamp())
            append(' ')
            append(severity.name.uppercase())
            append(' ')
            append(tag)
            append(": ")
            append(message)
            if (throwable != null) {
                append('\n')
                append(throwable.stackTraceToString())
            }
        }
        PlatformLogFile.append(line)
    }
}
'''

DESKTOP_SOURCE = r'''package com.nuvio.app.core.logging

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal actual object PlatformLogFile {
    private const val MaxBytes = 5L * 1024 * 1024
    private const val MaxFiles = 3
    private const val FileName = "nuvio.log"

    private val lock = Any()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val logDir: File? by lazy {
        runCatching { resolveLogDir().also { it.mkdirs() } }.getOrNull()
    }

    actual val directory: String?
        get() = logDir?.absolutePath

    actual fun timestamp(): String = LocalDateTime.now().format(formatter)

    actual fun append(line: String) {
        val dir = logDir ?: return
        synchronized(lock) {
            runCatching {
                val file = File(dir, FileName)
                if (file.exists() && file.length() >= MaxBytes) rotate(dir)
                file.appendText(line + "\n")
            }
        }
    }

    private fun rotate(dir: File) {
        for (i in MaxFiles - 1 downTo 1) {
            val older = File(dir, "$FileName.$i")
            val newer = File(dir, if (i == 1) FileName else "$FileName.${i - 1}")
            if (newer.exists()) {
                older.delete()
                newer.renameTo(older)
            }
        }
    }

    private fun resolveLogDir(): File {
        val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        val home = File(System.getProperty("user.home").orEmpty())
        return when {
            os.contains("win") -> {
                val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                    ?: File(home, "AppData/Local").path
                File(base, "Nuvio/logs")
            }
            os.contains("mac") -> File(home, "Library/Logs/Nuvio")
            else -> {
                val base = System.getenv("XDG_STATE_HOME")?.takeIf { it.isNotBlank() }
                    ?: File(home, ".local/state").path
                File(base, "nuvio/logs")
            }
        }
    }
}
'''

ANDROID_SOURCE = r'''package com.nuvio.app.core.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal actual object PlatformLogFile {
    private const val MaxBytes = 5L * 1024 * 1024
    private const val MaxFiles = 3
    private const val FileName = "nuvio.log"

    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logDir: File? = null

    /** Called once from MainActivity.onCreate, before AppLogging.install(). */
    fun initialize(context: Context) {
        val app = context.applicationContext
        logDir = runCatching {
            // App-specific external storage: no permission, readable via adb pull.
            val dir = app.getExternalFilesDir("logs") ?: File(app.filesDir, "logs")
            dir.mkdirs()
            dir
        }.getOrNull()
    }

    actual val directory: String?
        get() = logDir?.absolutePath

    actual fun timestamp(): String = synchronized(formatter) { formatter.format(Date()) }

    actual fun append(line: String) {
        val dir = logDir ?: return
        synchronized(lock) {
            runCatching {
                val file = File(dir, FileName)
                if (file.exists() && file.length() >= MaxBytes) rotate(dir)
                file.appendText(line + "\n")
            }
        }
    }

    private fun rotate(dir: File) {
        for (i in MaxFiles - 1 downTo 1) {
            val older = File(dir, "$FileName.$i")
            val newer = File(dir, if (i == 1) FileName else "$FileName.${i - 1}")
            if (newer.exists()) {
                older.delete()
                newer.renameTo(older)
            }
        }
    }
}
'''

IOS_SOURCE = r'''package com.nuvio.app.core.logging

/** iOS is not a shipping target for this distribution; the no-op keeps KMP metadata valid. */
internal actual object PlatformLogFile {
    actual val directory: String? = null
    actual fun timestamp(): String = ""
    actual fun append(line: String) = Unit
}
'''


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


def write_new(path, text):
    if path.exists():
        print("Already present: " + path.name)
    else:
        write(path, text)
        print("Added " + path.name)


def replace_once(text, old, new, what):
    count = text.count(old)
    if count != 1:
        fail("expected exactly one match for " + what + " but found " + str(count))
    return text.replace(old, new)


# Both repos declare android, desktop and ios targets, so all four files are written in both.
write_new(COMMON_DIR / "AppLogging.kt", COMMON_SOURCE)
write_new(ANDROID_DIR / "PlatformLogFile.android.kt", ANDROID_SOURCE)
write_new(DESKTOP_DIR / "PlatformLogFile.desktop.kt", DESKTOP_SOURCE)
write_new(IOS_DIR / "PlatformLogFile.ios.kt", IOS_SOURCE)

if PLATFORM == "desktop":
    entry = ROOT / "composeApp/src/desktopMain/kotlin/com/nuvio/app/Main.kt"
    anchor = "    SentryInitializer.start()\n"
    install = "    AppLogging.install()\n"
    import_anchor = "import com.nuvio.app.core.diagnostics.SentryInitializer\n"
    import_line = "import com.nuvio.app.core.logging.AppLogging\n"
else:
    entry = ROOT / "composeApp/src/androidMain/kotlin/com/nuvio/app/MainActivity.kt"
    anchor = "        SentryInitializer.start(application)\n"
    install = ("        PlatformLogFile.initialize(applicationContext)\n"
               "        AppLogging.install()\n")
    import_anchor = "import com.nuvio.app.core.diagnostics.SentryInitializer\n"
    import_line = ("import com.nuvio.app.core.logging.AppLogging\n"
                   "import com.nuvio.app.core.logging.PlatformLogFile\n")

text, crlf = read(entry)
if "AppLogging.install()" in text:
    print("Logging install already present: " + entry.name)
else:
    text = replace_once(text, anchor, anchor + install, "the logging install anchor in " + entry.name)
    if import_line.splitlines()[0] not in text:
        text = replace_once(text, import_anchor, import_anchor + import_line, "the Sentry import in " + entry.name)
    write(entry, text, crlf)
    print("Edited " + entry.name)
