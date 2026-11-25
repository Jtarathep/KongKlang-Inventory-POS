# KONGKLANG Mobile

The UI in `lib/` is Flutter Web. The Android app in `android-preview/` is a WebView wrapper that opens the hosted `/mobile/` route, so it shares the existing Spring session, store scope, and permissions.

## Build the mobile UI

Run from this directory:

```sh
flutter pub get
flutter test
flutter analyze
flutter build web --release --base-href /mobile/ --no-web-resources-cdn
```

Copy the contents of `build/web/` into `../src/main/resources/static/mobile/`. Deploy the Spring app to publish UI changes to Android users.

## Android wrapper

Open `android-preview/` as an Android Gradle project. Its `MainActivity` starts at the configured KONGKLANG server address and retains the address on that device. The server handles login and store authorization. Do not put database credentials or business data in this wrapper.

The Flutter scaffold's `android/` platform folder is not used for the distributed wrapper.
