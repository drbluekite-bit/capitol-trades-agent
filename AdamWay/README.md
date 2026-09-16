# Adam Way

A personal Android app for driving a long multi-stop journey — up to 18
addresses — through Google Maps, even though Google Maps' own address
picker tops out at around 10. Black background, white "AW" logo.

## What it does

1. **Add addresses** to a queue (up to 18), manually or by photographing
   one (house number/name, postcode, plaque, etc.) and letting on-device
   text recognition pre-fill the form for you to check and correct.
   Every field — house number, house name, postcode, what3words — is
   optional on its own; you just need at least one filled in per address.
2. **Begin Journey** sends the first 5 addresses to Google Maps as a single
   multi-stop route.
3. Each time you tap **Arrived – Next Stop** in Adam Way (after leaving
   the stop you were just at), the 6th address is added and Google Maps is
   relaunched with the updated route — then the 7th, and so on — until the
   final 5 addresses are loaded, at which point Maps runs the rest of the
   route on its own.
4. A "Open next stop in Waze" shortcut and automatic What3Words handoff
   are available per stop as alternatives.

## Why "Arrived – Next Stop" is a button, not automatic

Google Maps doesn't expose any API or broadcast that tells other apps when
you tap "Continue"/arrive at a stop inside its own turn-by-turn UI — that
happens entirely inside Google's app, which this app can't see into. So
instead of trying to observe Maps (which isn't possible with a public,
free API), Adam Way gives you your own "Arrived – Next Stop" button to tap
once you've left a stop; that's what pushes the next address into the
route. In practice this means one extra tap per stop versus the
"automatic" ideal, in exchange for not needing any paid Maps API, account,
or background service.

## Design decisions worth knowing about

- **No Google Maps/Directions API key, ever.** Routes are opened with a
  plain `https://www.google.com/maps/dir/?api=1&...` deep link (an Intent),
  which is free and doesn't require any API key or billing account. The
  trade-off is that Adam Way can't validate an address before sending it —
  Maps does its own geocoding when it opens.
- **what3words is optional and needs your own free API key.** A postcode
  or house address is passed to Maps as plain text and geocoded by Maps
  itself for free. A `///three.word.address`, though, means nothing to
  Google Maps — so if you want a what3words-only stop to be included in
  the *same* multi-stop Maps route as your other addresses, Adam Way
  converts it to a latitude/longitude first via the what3words API
  (`api.what3words.com`), which requires a free personal API key from
  [developer.what3words.com](https://developer.what3words.com). Add it in
  Settings. Without a key, a what3words-only stop instead opens directly
  in the What3Words app when it's your next stop.
- **Waze is single-stop only.** Waze's own deep link scheme doesn't support
  multiple waypoints, so it's offered as a per-stop shortcut, not for the
  whole journey.

## Privacy

- The address queue lives in a local SQLite database on your phone only
  (Room). `android:allowBackup="false"` and explicit backup-exclusion
  rules keep it out of cloud/device backups too.
- Camera photos are decoded straight into memory, run through Google's
  **on-device, bundled** ML Kit text recognizer (the model ships inside
  the app — no network call, nothing sent to Google), and then discarded.
  Nothing is written to disk or shared.
- The what3words API key is stored in an Android Keystore-backed
  `EncryptedSharedPreferences` file.
- The only network calls the app ever makes: (1) the optional
  `api.what3words.com` coordinate lookup, sent only when a queued address
  has a what3words value and only with your own key, and (2) handing a
  route off to Google Maps, Waze, or What3Words via an Android Intent when
  you tap a navigation button — at that point your route data goes
  directly from the OS to that app, not through any server of ours.
- No analytics, ads, crash reporting, or other third-party SDKs.

## Building it

This was written from a sandboxed environment with no Android SDK and no
access to Google's Maven repository (`dl.google.com` is blocked here), so
it has **not** been compiled in this session — open it in Android Studio
(Koala/Ladybird or newer) on your own machine, which will fetch the
Android Gradle Plugin, SDK platform 34, and the other Google-hosted
dependencies automatically, then Build → Run.

- **Language/UI:** Kotlin, Jetpack Compose, Material 3
- **Min/target SDK:** 26 / 34
- **Persistence:** Room (local only)
- **Camera/OCR:** CameraX + ML Kit Text Recognition (bundled/on-device model)
- **Networking:** OkHttp + kotlinx.serialization, used only for the
  optional what3words lookup

```
AdamWay/
  app/src/main/java/com/adamway/app/
    data/       Room entities, DAO, database, repositories, encrypted settings
    ocr/        Address text-parsing helpers for camera capture
    location/   what3words client + per-address location resolver
    maps/       Google Maps / Waze / What3Words intent builders
    journey/    The sliding-window logic that works around Maps' 10-stop cap
    ui/         Compose screens, theme, navigation
    viewmodel/  ViewModels wiring the above together
```

### First run checklist

1. Open `AdamWay/` in Android Studio and let it sync.
2. (Optional) Get a free what3words API key from
   developer.what3words.com and paste it into Adam Way → Settings if
   you plan to use `///` addresses.
3. Build & install on your phone. Grant the camera permission only if/when
   you use the "Scan with camera" option.
