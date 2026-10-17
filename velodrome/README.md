# Velodrome Ghost 🚴‍♂️👻

A simple Android app for racing a pace-setting **ghost rider** round a 250 m velodrome, using your
**Peloton bike + SmartSpin2k**.

- **Split screen**
  - **Left/top:** your view from the bike: banked boards, painted lines, the ghost's wheel ahead of you, and your power, cadence and speed.
  - **Right/bottom:** a top-down view of the whole track with you and the ghost on it, a live gap bar (metres and seconds) and, in the omnium, the points table.
  - Landscape (Peloton tablet, phone on its side) puts the views side by side. Portrait stacks them.
- **Race types**
  - **1 v 1 race:** first across the line wins. 3 laps (a match sprint) by default, 1–40 laps.
  - **Omnium points race:** a long, paced race with a sprint every *N* laps. Each sprint pays 5 pts to the winner and 3 to second. The finish counts double (10 / 6), and lapping the ghost is worth +20.
- **Ghost pacing**
  - Constant pace (% of the ghost's FTP), **or**
  - any **Zwift `.zwo` workout**: four track-style ones are built in, and **Import .zwo file…** adds your own.
  - *Ghost strength* scales the ghost's FTP relative to yours (50–150 %).
  - *Ghost attacks the sprints*: the ghost kicks for the line from 200 m out before every sprint and the finish.
  - *Head start*: give the ghost (or yourself) a few metres.
- **Real track physics.** Both riders use the same model (aero drag, rolling resistance, mass), and that includes **drafting**. Sit within a few metres of the ghost's wheel and you need about 35 % less aero power. You can save energy on its wheel and come round it in the last 200 m.
- **SmartSpin2k resistance**
  - **Track feel (sim):** flat-track simulation, and the trainer gets lighter while you're in the draft.
  - **ERG:** SmartSpin2k holds the workout's power, scaled to *your* FTP, so the `.zwo` sets your effort while the ghost rides it too.
  - **No control:** you set resistance with the knob.
- **Demo mode** with an on-screen power slider, for trying the app without the bike.
- **.fit export.** After a race (even an abandoned one) the results screen shows your **avg cadence,
  avg power, total work (kJ), total distance and calories**. **Save .fit file** or **Share .fit**
  then gives you a Garmin FIT activity file you can upload to Strava, Garmin Connect,
  TrainingPeaks or intervals.icu. See [Exported .fit files](#exported-fit-files).

## How it connects

```
Peloton bike ──serial cable──► SmartSpin2k ──Bluetooth (FTMS)──► Velodrome Ghost
                                    ▲                                   │
                                    └──── ERG / sim resistance ◄────────┘
```

SmartSpin2k reads the Peloton's power and cadence from the bike's data cable and re-broadcasts them
as a standard Bluetooth **Fitness Machine (FTMS)** trainer. The app connects to that, reads power,
cadence and heart rate (if available), and writes ERG targets or track-simulation settings back.
Any other FTMS trainer or Bluetooth power meter works too (Cycling Power Service is supported).

Your speed on the track comes from **your power** through the physics model, not from the Peloton's
own speed number, so you and the ghost are measured the same way.

Tips:
- Close Zwift, the SmartSpin2k web app, and anything else connected to SmartSpin2k's Bluetooth before scanning.
- Set your **FTP** and **weight** in the app. The ghost is scaled from your FTP.

## Installing

### Samsung Galaxy S22 Ultra (easiest)

1. Every push to this branch builds the app in GitHub Actions (`.github/workflows/velodrome-android.yml`).
2. On the phone, open the repo's **Releases** page, find **"Velodrome Ghost (latest build)"**
   (tag `velodrome-latest`) and download **`velodrome-ghost.apk`**. You can also download it from the
   *Artifacts* section of the latest Actions run.
3. Open the APK. Android will ask you to allow installs from your browser or file manager
   (*Settings → Apps → Special access → Install unknown apps*). Allow it, then tap **Install**.
4. Open **Velodrome Ghost** and grant the *Nearby devices* (Bluetooth) permission when asked.

New builds install over the old one, because every build is signed with the same key.

### Old Peloton tablet (sideloading, not guaranteed)

Peloton tablets run a locked-down Android (older ones Android 7.1, newer ones Android 10). The app
supports Android 7.0 and up. Getting a third-party app onto the tablet depends on its software
version, and Peloton has blocked some routes in updates. A common method:

1. On the tablet: **Settings → Device Settings → About tablet**, then tap **Build number** 7 times to turn on Developer options.
2. In **Developer options**, turn on **USB debugging**.
3. Connect a computer to the tablet's USB port and install [Android platform-tools](https://developer.android.com/tools/releases/platform-tools).
4. Run:
   ```bash
   adb install velodrome-ghost.apk
   adb shell am start -n com.velodromeghost.app/.MainActivity
   ```
   The Peloton launcher doesn't show third-party apps, so you may need that second command, or a
   launcher app, to open it.

If your tablet's software blocks this, use the S22 Ultra on a bar/stem mount. That works fully.
Sideloading onto Peloton hardware is at your own risk.

## Using `.zwo` files

Standard Zwift workout files work: `Warmup`, `Cooldown`, `Ramp`, `SteadyState`, `SolidState`,
`IntervalsT`, `FreeRide`, `MaxEffort`, plus `textevent` messages (shown as on-screen callouts).
Power values are fractions of FTP, as in Zwift.

- **Ghost:** rides the workout's power × the ghost's FTP. Its speed and cadence rise and fall with it.
- **You:** the workout's `Cadence` targets show on your HUD. In **ERG** mode SmartSpin2k also
  holds the workout's power (× your FTP).
- When the workout ends before the race does, the ghost holds the final power.

The workouts are timed in seconds, but sprints happen at fixed laps. For an omnium, keep
**Ghost attacks the sprints** on so the ghost always contests each sprint line, and use the
`.zwo` for the tempo in between.

Built-in workouts (`app/src/main/assets/workouts/`): *Match Sprint – Cat and Mouse*, *Omnium Points
Race – 40 laps*, *Kilo Time Trial – 4 laps*, *Tempo Race – Surges*. They're also good templates for
writing your own.

## Send to ChatGPT

When a race finishes, the app opens the **ChatGPT** app with a message ready to send. The message
holds a summary of the race and ride (result, avg power, avg cadence, total work, distance, kcal,
heart rate, your FTP) and a request to analyse your pacing. The per-second data comes as a
`.csv` file attached to the message. Review it and press send.

- To stop it opening automatically, switch off *Send ride to ChatGPT automatically* in setup. The
  **Send to ChatGPT** button stays on the results screen.
- If ChatGPT won't accept the file, the CSV data goes into the message text instead.
- If the ChatGPT app isn't installed, you get the normal share menu.
- Races in demo mode never open ChatGPT automatically.

OpenAI doesn't let other apps post into your ChatGPT account directly, so the last tap is always yours.

## Exported .fit files

The file holds a standard FIT *activity*: one record per second (power, cadence, heart rate,
speed, distance), plus a lap and a session summary marked as indoor cycling.

| Value | How it's worked out |
| --- | --- |
| Avg cadence | Average of your non-zero cadence readings (coasting isn't counted, as on Garmin) |
| Avg power | Total work ÷ ride time, zeros included |
| Total work ("total watts") | Your power added up over the whole ride, in kJ (1 W for 1 s = 1 J) |
| Total distance | Your distance round the track |
| Calories | Total work ÷ 4.184 ÷ 24 % efficiency, the usual bike-computer estimate (about 1 kcal per kJ) |

Ride time is race time, so pauses don't count. The countdown isn't recorded.

## Building it yourself

Requirements: JDK 17 and the Android SDK (Android Studio is easiest).

```bash
cd velodrome
./gradlew :core:test          # race engine / .zwo / Bluetooth protocol tests (plain JVM)
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug/
```

Project layout:

| Module | What's in it |
| --- | --- |
| `core/` | Pure Kotlin, unit tested: `.zwo` parser, rider physics and drafting, track geometry, race engine (1v1 and points race scoring), FTMS / Cycling Power packet parsing and control-point commands |
| `app/` | Android UI (Jetpack Compose): Bluetooth manager, setup screen, split-screen race view (pseudo-3D POV + overhead track), results |

The debug signing key in `app/debug.keystore` is a throwaway with the standard public `android`
passwords. It only exists so that CI builds can update each other. Don't use it for anything you
publish.
