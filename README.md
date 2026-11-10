# Link Cleaner

A tiny Android app that removes tracking junk from links you share.

1. In any app, tap **Share** on a link and pick **Clean link**.
2. A small dialog shows the original link, the cleaned link, and what was removed.
3. Tap **Copy** (puts it on your clipboard), **Share** (send it straight on), or **Open**.

You can also open the app from your home screen and paste a link.

The app requests **no permissions** (not even internet). All cleaning happens on your phone.

## What it cleans

- **Tracking parameters**, such as `utm_*`, `fbclid`, `gclid`, `msclkid`, `igsh`, and Mailchimp/HubSpot tags. It also removes some site-specific ones, like YouTube/Spotify `si`, X/Twitter `s`/`t`, and TikTok share params.
- **Redirect wrappers.** For example, `google.com/url?q=…`, `l.facebook.com/l.php?u=…`, `youtube.com/redirect`, `out.reddit.com`, and `l.instagram.com` are turned into the real link they point to.
- **Shopping links.** These are shortened to the product only:
  - Amazon (any country) → `amazon.com/dp/ASIN`
  - eBay → `ebay.com/itm/ID`
  - AliExpress → `aliexpress.com/item/ID.html`
  - Walmart → `walmart.com/ip/ID`
  - World Market: Facebook/Instagram ad tags (`media_type`, `ppc`, `camp`) are removed

Parameters that actually matter, such as a YouTube video ID or timestamp, are kept. The rules live in
[`UrlCleaner.kt`](app/src/main/java/io/github/mhayden123/linkcleaner/UrlCleaner.kt).

## Getting the APK

Every push builds the app on GitHub Actions:

1. Open the repo's **Actions** tab, then the latest green **Build APK** run.
2. Under **Artifacts**, download `link-cleaner.apk`. You need to be signed in to GitHub.
3. On your phone, open the file and allow "Install unknown apps" for your browser or file manager when asked.

> The CI build is signed with a throwaway debug key that changes on every run. Installing a newer build
> over an older one may therefore fail with "App not installed". If that happens, uninstall the old one first.

## Building locally

Requires JDK 17+ and the Android SDK (Android Studio installs both).

```
./gradlew testDebugUnitTest   # run the link-cleaning tests
./gradlew assembleDebug       # APK at app/build/outputs/apk/debug/app-debug.apk
```
