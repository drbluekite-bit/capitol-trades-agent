# Adam Way

A personal Android app for driving a long multi-stop journey — up to 18
addresses — through Google Maps, even though Google Maps' own address
picker tops out at around 10. Black background, white "AW" logo.

## What it does

1. **Add addresses** to a queue (up to 18) by typing one address into a
   single field, exactly as you would into Google Maps' own search bar
   (e.g. "24 Sandybrook Close, Bury BL8 3LB") — **matching addresses show
   up as you type**, the same way Maps' own search bar suggests results,
   so you can tap the complete, correctly-formatted line (postcode
   included) instead of typing the whole thing out. You can also
   photograph an envelope, door plaque, etc. and let on-device text
   recognition fill in that field, or tap **Speak address** and say it
   out loud. A what3words address is optional, as an alternative or
   extra. The Queue screen and the home screen both have a **Clear
   queue** action for starting over.
2. **Begin Journey** sends the first 5 addresses to Google Maps as a single
   multi-stop route.
3. Each time you tap **Arrived – Next Stop** in Adam Way (after leaving
   the stop you were just at), the 6th address is added and Google Maps is
   relaunched with the updated route — then the 7th, and so on — until the
   final 5 addresses are loaded, at which point Maps runs the rest of the
   route on its own.
4. A "Open next stop in Waze" shortcut and automatic What3Words handoff
   are available per stop as alternatives.
5. **Adam Way remembers addresses you've used before.** As you start
   typing a new one, matching past addresses show up under "From memory" —
   tap one to fill in the form instantly. That memory is separate from the
   current queue, so it survives Clear Queue.
6. Each address in the queue has an info (ⓘ) button that opens its **notes
   and photos** — a free-text note plus any photos you've taken against it
   (parking instructions, a gate code, which door to use, etc.). These are
   attached to the address in memory, not just this one trip, so they're
   still there next time it comes up.
7. **Quick add** chips on the Queue screen let you add a favorite
   destination in one tap, without opening the form at all — currently
   just "Tesco Extra, Barnsley". It's added to the end of the queue like
   any other stop, so add it last (or reorder it down) if you want it as
   your final destination.
8. **Every stop listed in the journey screen shows its full address line**
   (including the postcode, if you included one when typing/picking it) —
   there's no separate short label that drops detail; what you typed or
   picked is exactly what's shown at every stage, overview included.

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
  which is free and doesn't require any API key or billing account.
- **Adam Way never geocodes a postal address itself — Maps or Waze does,
  exactly as if you'd typed it into their own search bar.** This went
  through a few iterations that all tried to resolve a typed address to
  coordinates inside the app first: first handing Maps free text framed
  from separate house-number/street/postcode fields (which could mis-join
  into the wrong query), then geocoding with OpenStreetMap's free
  Nominatim search API and trusting a tapped suggestion's coordinate for
  navigation (which once sent a Rotherham address to a street in Mongolia,
  and another time resolved a "confirmed" pick to a postcode's centroid
  rather than the real house — Nominatim's free UK data often isn't mapped
  at house-level). All of that in-app geocoding is gone. Address entry is
  a single free-text field; whatever you type, speak, or scan goes
  straight into it, and that exact text is handed to Google Maps or Waze
  as a destination/waypoint when you start the journey — their own
  geocoders (which have full, commercial-grade UK address coverage)
  resolve it, the same as if you'd typed it directly into either app.
  Nominatim is still used for one thing, safely: a live "matching
  addresses" dropdown as you type, purely as **text** autocomplete (no
  different from a keyboard's predictive text) — tapping a suggestion
  only fills the field with a complete, well-formatted line; its
  coordinate is never looked at, let alone trusted, by anything in this
  app.
- **what3words is optional and needs your own free API key.** A
  `///three.word.address` means nothing to Google Maps's geocoder — so if
  you want a what3words-only stop included in the *same* multi-stop
  Maps route as your other addresses, Adam Way converts it to a
  latitude/longitude first via the what3words API (`api.what3words.com`),
  which requires a free personal API key from
  [developer.what3words.com](https://developer.what3words.com). Add it in
  Settings. Without a key, a what3words-only stop instead opens directly
  in the What3Words app when it's your next stop.
- **Choosing Google Maps or Waze per stop.** The whole multi-stop journey
  always goes through Google Maps, since Waze's own deep link scheme
  doesn't support multiple waypoints at all — there's no way to hand it an
  18-stop route. But every leg of the journey also has an "Open next stop
  in Waze" button right alongside the automatic Maps launch, so you can
  use Waze for whichever single stop you actually want it for.

## Privacy

- The address queue, address memory, and all notes live in a local SQLite
  database on your phone only (Room). `android:allowBackup="false"` and
  explicit backup-exclusion rules keep it out of cloud/device backups too.
- Note photos are taken with the phone's own camera app (via an
  `ACTION_IMAGE_CAPTURE` intent — Adam Way doesn't request the camera
  permission for this and never previews/records anything itself) and the
  result is saved straight into this app's private internal storage, never
  external/shared storage. Deleting a photo from an address's notes deletes
  that file immediately.
- Camera photos are decoded straight into memory, run through Google's
  **on-device, bundled** ML Kit text recognizer (the model ships inside
  the app — no network call, nothing sent to Google), and then discarded.
  Nothing is written to disk or shared.
- **Speak address** launches the phone's own system speech-to-text app
  (via `RecognizerIntent`) rather than Adam Way doing any recording or
  recognition itself — the same thing your keyboard's microphone button
  uses. Adam Way never requests the microphone permission and never
  touches the audio; it only receives the resulting text back. What that
  system app does with the recording (on-device vs. a cloud speech
  service) is up to your phone/Google Assistant settings, not this app.
- The what3words API key is stored in an Android Keystore-backed
  `EncryptedSharedPreferences` file.
- The only network calls the app ever makes: (1) searching
  `nominatim.openstreetmap.org` (OpenStreetMap's free service — see their
  [privacy policy](https://osmfoundation.org/wiki/Privacy_Policy)) as you
  type in the Add/Edit screen, purely for the text-only "matching
  addresses" suggestions — no coordinate it returns is ever used for
  navigation, (2) the optional `api.what3words.com` coordinate lookup,
  sent only when a queued address has a what3words value and only with
  your own key, and (3) handing a route off to Google Maps, Waze, or
  What3Words via an Android Intent when you tap a navigation button — at
  that point your route data goes directly from the OS to that app (and
  from there, to its own geocoder), not through any server of ours. Adam
  Way itself never geocodes a postal address for navigation purposes.
- No analytics, ads, crash reporting, or other third-party SDKs.

## Building it

The authoring sandbox has no Android SDK and no access to Google's Maven
repository, so it can't build locally there. CI (`.github/workflows/build-adam-way.yml`)
builds a debug APK on every push and publishes it as both a workflow
artifact and a GitHub Release — grab the latest from the repo's Releases
page. To build it yourself, open `AdamWay/` in Android Studio (Koala/
Ladybird or newer), which will fetch the Android Gradle Plugin, SDK
platform 34, and the other Google-hosted dependencies automatically, then
Build → Run.

Debug builds are signed with the fixed, checked-in `keystore/debug.keystore`
(a standard, non-secret debug-only key — never used for a release build)
rather than each machine's own auto-generated one, specifically so CI
builds from different runs — and different CI runners — can install over
each other as updates. **If you installed an APK from a CI build before
this keystore was pinned, Android will refuse to install over it** (it
looks like a different app once the signing key changes); uninstall the
old copy once, then every build from here on updates normally.

- **Language/UI:** Kotlin, Jetpack Compose, Material 3
- **Min/target SDK:** 26 / 34
- **Persistence:** Room (local only)
- **Camera/OCR:** CameraX + ML Kit Text Recognition (bundled/on-device model)
- **Networking:** OkHttp + kotlinx.serialization — the optional what3words
  lookup, and OpenStreetMap Nominatim for text-only address-suggestion
  autocomplete; postal addresses are never geocoded for navigation by the
  app itself

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
