<div align="center">

  <img src="https://raw.githubusercontent.com/NuvioMedia/NuvioDesktop/b1e00724c55e65f8f325d4d9d52eb6827872a7c2/composeApp/src/commonMain/composeResources/drawable/app_logo_wordmark.png" alt="Nuvio Live TV" width="300" />
  <br />
  <br />

  [![Latest release][release-shield]][release-url]
  [![Build][build-shield]][build-url]
  [![License][license-shield]][license-url]

  <p>
    <strong>Nuvio Live TV</strong> — a patch-based distribution that adds a Live TV guide to Nuvio.
    <br />
    Windows desktop. Android phones and iPhones/iPads are built in [`NuvioMobile`](https://github.com/dslayer6989/NuvioMobile), and Android TV in [`NuvioTV`](https://github.com/dslayer6989/NuvioTV).
  </p>

</div>

## ⚠️ Alpha Software — Testers Only

Nuvio Live TV is in alpha and is intended only for testers. It is not suitable for daily use.

Expect breaking changes with every update. Features, settings, stored data, and compatibility may change or stop working without notice. Do not rely on this build as your primary media app, and report any issues you encounter during testing.

## What this repository is

This is **not** a fork of Nuvio's source code. It is a small distribution layer that adds a Live TV feature to the official Nuvio apps.

The repository contains three patch files and one build workflow. Nothing else ships.

| File | What it does |
| --- | --- |
| `windows-live-tv.patch` | Adds Live TV to the official Nuvio Desktop app |
| `.github/workflows/live-tv-build.yml` | Applies the patches to the official source and publishes the installers |

Each patch is applied to a **pinned commit** of the official Nuvio source, so a build is always reproducible:

| Target | Official source | Pinned commit |
| --- | --- | --- |
| Windows MSI | [`NuvioMedia/NuvioDesktop`](https://github.com/NuvioMedia/NuvioDesktop) | `e808a58` |

Because the apps are built from Nuvio's own source, everything Nuvio does — browsing, metadata, addons, playback, tracking — works exactly as it does upstream. Live TV is the only addition.

## What Live TV adds

- **A channel guide** with a time grid, channel rows, and program cells that show a progress bar for what is airing now.
- **Addon sources** — paste a Stremio addon manifest link (or a bare addon root) and its channels appear in the guide.
- **Favorites and recents** — mark channels as favorites and the guide remembers what you watched.
- **Source memory** — when a channel has several streams, the app remembers the last one that worked and tries it first next time.
- **Backup streams** — if a stream fails, the player advances to the next source automatically.
- **Profiles** — Live TV settings are stored per profile, alongside the rest of your Nuvio settings.

## Download and install

Get the newest build from the [Releases page][release-url]. Releases here contain the Windows installer. Android phones and iPhones/iPads: use the releases of [`dslayer6989/NuvioMobile`](https://github.com/dslayer6989/NuvioMobile). Android TV: use the releases of [`dslayer6989/NuvioTV`](https://github.com/dslayer6989/NuvioTV).

### Windows desktop

Download **`Nuvio-LiveTV-Windows.msi`** and double-click it.

It replaces the regular Nuvio desktop app and keeps your settings. If Nuvio later offers an app update, skip it: that update doesn't include Live TV.

## Getting started

1. Install the app for your device from the [Releases page][release-url].
2. Sign in with your Nuvio account.
3. Open the **Live TV** tab.
4. Paste your addon's manifest link.
5. Pick a channel and press play.

## Building it yourself

The build runs entirely in GitHub Actions. To produce a release:

1. Open the **Actions** tab.
2. Choose **Build Nuvio Live TV**.
3. Press **Run workflow**.

The workflow checks out the official Nuvio source at the pinned commit, applies the patches, builds the Windows installer, and publishes it as a release tagged `live-tv-build-N`.

You can also let a push to `Dev` or `main` start a build. Pushing a change to the patch file triggers it automatically.

## Updating the Live TV feature

The patches are generated, not hand-edited. The generator scripts live in `.github/livetv-tools/`:

| Script | What it edits |
| --- | --- |
| `edit_livetv.py` | The shared Live TV feature (guide, repository, playback, settings) |

To change the feature, edit the relevant script, then run **Regenerate Live TV patches** followed by **Publish regenerated Live TV patches**. Each regenerate run applies the edits, runs the Live TV tests, and only offers the new patch if the tests pass.

## Notes and limitations

- **Alpha.** The Live TV feature is under active development.
- **Android TV home screen rows.** App-provided home screen rows are best-effort. They appear on some launchers and not others, and Fire TV does not support them at all. The app never crashes if a launcher rejects them.
- **Samsung and LG TVs.** Tizen and webOS cannot install Android APKs, so this distribution does not support them. Use a streaming stick or cast from your phone instead.
- **Windows updates.** The Live TV installer replaces the official Nuvio desktop app. If Nuvio offers an in-app update, skip it, because that update does not include Live TV.

## Credits

Nuvio Live TV is built on the work of the Nuvio project. All app code, design and features come from [`NuvioMedia/NuvioDesktop`](https://github.com/NuvioMedia/NuvioDesktop). This repository only adds the Live TV layer.

Nuvio is licensed under the GNU General Public License v3.0.

[release-shield]: https://img.shields.io/github/v/release/dslayer6989/NuvioDesktop?style=for-the-badge&label=latest%20release
[release-url]: https://github.com/dslayer6989/NuvioDesktop/releases
[build-shield]: https://img.shields.io/github/actions/workflow/status/dslayer6989/NuvioDesktop/live-tv-build.yml?style=for-the-badge&label=build
[build-url]: https://github.com/dslayer6989/NuvioDesktop/actions/workflows/live-tv-build.yml
[license-shield]: https://img.shields.io/github/license/dslayer6989/NuvioDesktop.svg?style=for-the-badge
[license-url]: https://github.com/dslayer6989/NuvioDesktop/blob/main/LICENSE
