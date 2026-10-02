"""Adds Live TV backlog items 1, 6 and 7 to a Nuvio source tree that already has a Live TV patch applied.

Usage: python3 edit_livetv.py <root of the Nuvio checkout>
Safe to run twice. Stops with an error message if the code it expects is not there.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(sys.argv[1])
FEATURES = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/features"
REPO_FILE = FEATURES / "livetv/LiveTvRepository.kt"
SEARCH_FILE = FEATURES / "settings/SettingsSearch.kt"
STRINGS_FILE = ROOT / "composeApp/src/commonMain/composeResources/values/strings_live_tv.xml"

REQUIRED_STRINGS = [
    "live_tv_settings_page_title",
    "live_tv_settings_entry_description",
    "live_tv_settings_section_addon",
    "live_tv_settings_section_lists",
    "live_tv_settings_refresh",
    "live_tv_settings_remove",
    "live_tv_settings_clear_favorites",
    "live_tv_settings_clear_recents",
]


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


def replace_once(text, old, new, what):
    count = text.count(old)
    if count != 1:
        fail("expected exactly one match for " + what + " but found " + str(count))
    return text.replace(old, new)


# ---------------------------------------------------------------- items 1 and 7: LiveTvRepository.kt
OLD_LOAD_IDS = """    private fun loadProfileIds(key: String, profileId: Int): List<String> =
        (LiveTvStorage.loadString(ProfileScopedKey.of(key, profileId)) ?: LiveTvStorage.loadString(key))
            ?.let { payload -> runCatching { json.decodeFromString(idListSerializer, payload) }.getOrNull() }
            .orEmpty()
"""
NEW_LOAD_IDS = """    private fun loadProfileIds(key: String, profileId: Int): List<String> =
        decodeProfileIds(
            scoped = LiveTvStorage.loadString(ProfileScopedKey.of(key, profileId)),
            legacy = LiveTvStorage.loadString(key),
        )
"""
OLD_LOAD_STRING = """    private fun loadProfileString(key: String, profileId: Int): String? =
        LiveTvStorage.loadString(ProfileScopedKey.of(key, profileId)) ?: LiveTvStorage.loadString(key)
"""
NEW_LOAD_STRING = """    private fun loadProfileString(key: String, profileId: Int): String? =
        resolveProfileValue(
            scoped = LiveTvStorage.loadString(ProfileScopedKey.of(key, profileId)),
            legacy = LiveTvStorage.loadString(key),
        )
"""
OLD_CLEAR_FAVORITES = """        _uiState.update {
            it.copy(
                favoriteIds = emptyList(),
                selectedGroupId = it.selectedGroupId.takeUnless { id -> id == LiveTvFavoritesGroupId },
            )
        }
"""
NEW_CLEAR_FAVORITES = """        _uiState.update { it.withoutFavorites() }
"""
OLD_CLEAR_RECENTS = """        _uiState.update {
            it.copy(
                recentIds = emptyList(),
                selectedGroupId = it.selectedGroupId.takeUnless { id -> id == LiveTvRecentGroupId },
            )
        }
"""
NEW_CLEAR_RECENTS = """        _uiState.update { it.withoutRecents() }
"""
HELPERS = """private val LiveTvIdListJson = Json { ignoreUnknownKeys = true }

/**
 * What a profile should see for a saved value: its own value when it has one, otherwise the
 * device-wide value older builds saved. An empty string means "All channels" and reads as null,
 * even when an older device-wide value exists.
 */
internal fun resolveProfileValue(scoped: String?, legacy: String?): String? =
    (scoped ?: legacy)?.ifEmpty { null }

/** A profile's saved channel list. A list the profile emptied itself does not fall back to the old one. */
internal fun decodeProfileIds(scoped: String?, legacy: String?): List<String> =
    resolveProfileValue(scoped, legacy)
        ?.let { payload ->
            runCatching {
                LiveTvIdListJson.decodeFromString(ListSerializer(String.serializer()), payload)
            }.getOrNull()
        }
        .orEmpty()

/** This state with the favorites emptied and, if the Favorites chip was selected, back on All. */
internal fun LiveTvUiState.withoutFavorites(): LiveTvUiState = copy(
    favoriteIds = emptyList(),
    selectedGroupId = selectedGroupId.takeUnless { it == LiveTvFavoritesGroupId },
)

/** This state with the recents emptied and, if the Recent chip was selected, back on All. */
internal fun LiveTvUiState.withoutRecents(): LiveTvUiState = copy(
    recentIds = emptyList(),
    selectedGroupId = selectedGroupId.takeUnless { it == LiveTvRecentGroupId },
)

"""

text, crlf = read(REPO_FILE)
if "resolveProfileValue" in text:
    print("Repository edits already applied: " + REPO_FILE.name)
else:
    text = replace_once(text, OLD_LOAD_IDS, NEW_LOAD_IDS, "loadProfileIds")
    text = replace_once(text, OLD_LOAD_STRING, NEW_LOAD_STRING, "loadProfileString")
    text = replace_once(text, OLD_CLEAR_FAVORITES, NEW_CLEAR_FAVORITES, "the clearFavorites update block")
    text = replace_once(text, OLD_CLEAR_RECENTS, NEW_CLEAR_RECENTS, "the clearRecents update block")
    text = replace_once(text, "object LiveTvRepository {\n", HELPERS + "object LiveTvRepository {\n", "object LiveTvRepository")
    write(REPO_FILE, text, crlf)
    print("Edited " + REPO_FILE.name)

# ---------------------------------------------------------------- item 6: settings search entries
text, crlf = read(SEARCH_FILE)
if 'key = "live-tv"' in text:
    print("Settings search entries already present: " + SEARCH_FILE.name)
else:
    xml = STRINGS_FILE.read_text(encoding="utf-8")
    names = set(re.findall(r'<string name="([^"]+)"', xml))
    missing = [name for name in REQUIRED_STRINGS if name not in names]
    if missing:
        fail("strings_live_tv.xml is missing: " + ", ".join(missing))

    block = [
        "    val liveTvPage = stringResource(Res.string.live_tv_settings_page_title)",
        "    addPage(",
        "        page = SettingsPage.LiveTv,",
        '        key = "live-tv",',
        "        title = liveTvPage,",
        "        description = stringResource(Res.string.live_tv_settings_entry_description),",
        "        icon = Icons.Rounded.LiveTv,",
        "    )",
    ]
    rows = [
        ("live-tv-refresh", "live_tv_settings_refresh", "live_tv_settings_section_addon"),
        ("live-tv-remove", "live_tv_settings_remove", "live_tv_settings_section_addon"),
        ("live-tv-clear-favorites", "live_tv_settings_clear_favorites", "live_tv_settings_section_lists"),
        ("live-tv-clear-recents", "live_tv_settings_clear_recents", "live_tv_settings_section_lists"),
    ]
    for key, title_key, section_key in rows:
        block += [
            "    addRow(",
            "        page = SettingsPage.LiveTv,",
            '        key = "' + key + '",',
            "        title = stringResource(Res.string." + title_key + "),",
            "        pageLabel = liveTvPage,",
            "        section = stringResource(Res.string." + section_key + "),",
            "        icon = Icons.Rounded.LiveTv,",
            "    )",
        ]
    inserted = "\n".join(block) + "\n\n"

    match = re.search(r"^[ \t]*addPage\(\s*page = SettingsPage\.Homescreen,", text, re.M)
    if match is None:
        fail("could not find the Home layout entry to insert before in " + SEARCH_FILE.name)
    text = text[: match.start()] + inserted + text[match.start():]

    icon_import = "import androidx.compose.material.icons.rounded.LiveTv"
    if icon_import not in text:
        found = list(re.finditer(r"^import androidx\.compose\.material\.icons\..*$", text, re.M))
        if not found:
            fail("could not find the icon imports in " + SEARCH_FILE.name)
        last = found[-1]
        text = text[: last.end()] + "\n" + icon_import + text[last.end():]
    write(SEARCH_FILE, text, crlf)
    print("Edited " + SEARCH_FILE.name)
