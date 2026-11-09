<img src="docs/icon.png" width="96" align="right">

# OpenAndroid Store

An app store for open-source Android apps, in Material You style. It reads a public catalog
([OpenAndroid-Index](https://github.com/xenone-beta/OpenAndroid-Index)), downloads APKs from GitHub Releases
and installs them through the system installer.

## Features

- **Catalog** with search and categories, app pages with description, changelog, versions, permissions, source link
- **Install, update, update all, open, uninstall**
- **Screenshots**, favorites, support links (website, report a problem), a "signature verified" badge
- **Warning before an update that asks for new permissions**
- **Safety checks before every install:** SHA-256 of the file, package name and signing key must match the catalog;
  Android additionally refuses updates signed with another key
- **Update notifications** (background check every 6, 12 or 24 hours, or off)
- Works offline with the last saved catalog
- Material You wallpaper colors, light, dark and AMOLED themes, English and Russian
- No accounts, no ads, no analytics

## Build

Requires JDK 17 and Android SDK 35.

```
./gradlew testDebugUnitTest assembleDebug
```

### GitHub Actions

- `ci.yml` runs tests, lint and builds a debug APK.
- `release.yml` runs on tags like `v1.0.0` and publishes an APK. Add the secrets `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` to sign it with your own key
  (without them the debug key is used, which is fine for testing but should not be your long-term key).

## Catalog address

The default address is in `app/build.gradle.kts` (`DEFAULT_INDEX_URL`) and can be changed in
Settings → Catalog address (https only). Anyone can host their own catalog with the same format.

## Notes

- Installing needs the "Install unknown apps" permission for this app; the store asks for it on the first install.
- Silent updates without confirmation are only possible on Android 12+ and only for apps this store installed;
  otherwise the system shows its normal confirmation.

License: MIT.
