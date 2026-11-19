# CinemaOS (Android TV App)

CinemaOS for Android TV is a free app for watching movies, TV shows and anime on your television, designed for remote-control navigation. It ships with built-in sources, so there is nothing to configure before you start watching.

## Features

- Movies, shows and anime with a TV-friendly home screen and website-style episode lists.
- Built-in sources with provider-priority autoplay and size-aware fallback on slow links.
- Player with tuned buffering, a gradient loader, skip intro/segments and CinemaOS player icons.
- CinemaOS account sync: sign in by scanning a QR code (or via the website) to share watch history and watchlist with [cinemaos.tech](https://cinemaos.me); anime progress syncs via Kitsu.
- In-app updates delivered from this repository's GitHub Releases.

## Downloads

For supported direct MP4, MKV and WebM sources, open the player’s Sources panel and choose **Download playing source**. Progress, cancel/delete actions and offline playback are available in **Settings → Downloads**. Android continues transfers in the background. HLS/DASH playlists and torrents are not supported by this download path.

## Install

1. Open the [Releases](../../releases) page of this repository.
2. Download the latest APK (arm64-v8a for most TV boxes and sticks; the universal APK works everywhere).
3. Sideload it on your Android TV, for example with a USB drive, a file manager app or `adb install`.

After the first install, the app notifies you when a new release is available.

## Building

APKs are built by the manual GitHub Actions workflow [`build-apk.yml`](.github/workflows/build-apk.yml):

1. Add a `TMDB_API_KEY` repository secret (Settings, Secrets and variables, Actions).
2. Open the Actions tab, choose **Build APK** and click **Run workflow** on the `cinemaos` branch.
3. Download the universal and per-ABI APKs from the workflow artifacts or release.

The app is built with Kotlin, Jetpack Compose, TV Material 3 and Media3.

## Credits

CinemaOS is based on the open-source [Nuvio](https://github.com/NuvioMedia/NuvioTV) project. Thanks to its authors and contributors.

## License

[GNU General Public License v3.0](./LICENSE). Original copyright and attribution notices are retained as required by the license.
