# Validation — 0.1.0-test

Initial validation date: 2026-09-22. Cloud integration update: 2026-09-23. This is an installable **debug prototype**, not a device-qualified release.

## Completed checks

- Build: `assembleDebug coreTest lintDebug --no-daemon --console=plain` completed with `BUILD SUCCESSFUL` for the main application and both fixtures.
- Core behavior tests: **26 / 26 passed**. Covers timed cross-package return, allowed targets, unprotected applications, bounded retries, source stability, unchanged protection deadline, Home/unknown-window/disable cancellation, clock rollback, repeated-bounce budget, and separate fallback outcomes.
- Android Lint: **0 errors** in every module. Main application has 2 warnings; source fixture 4; target fixture 2. Remaining warnings concern Chinese UI string concatenation, a missing custom source-fixture icon, and the target fixture’s Android 13 Back compatibility attribute ignored by older systems. The source-fixture report also retains a legacy backup-rule warning; its manifest disables backup. No error baseline or disabled lint check was used.
- All three APKs passed `apksigner verify --verbose` with APK Signature Scheme v2. These APKs use a local debug certificate.
- Packaged manifest inspection (`aapt dump badging` / `permissions`) confirms minimum API 26, target API 35, expected application IDs, and no requested INTERNET or QUERY_ALL_PACKAGES permission.
- Source inspection: every Back/relaunch action rechecks the focused active application; lock and screen-off cancel recovery. Outcome logging requires stable foreground source identity, and distinguishes relaunch from return. Backup and device-transfer exclusions are explicit.

## Environment

- JDK 17.0.13, Gradle 8.9, Android Gradle Plugin 8.7.3.
- Android platform 35 and Build Tools 35.0.0.
- A clean Gradle Wrapper distribution download timed out in the execution environment. The successful build used the already installed official Gradle 8.9 distribution. Wrapper files are included but a fresh Wrapper download is not claimed as verified.
- Initial local build had no connected Android device. A later GitHub Actions Android 10 emulator integration run is recorded below.

## Cloud emulator integration (2026-09-23)

- [GitHub Actions run 35819353058](https://github.com/LiuzhongjiKevin/anti-shake-ads/actions/runs/35819353058) completed successfully on an Android 10 emulator. The workflow installs the same three committed debug APKs and configures this app's settings and AccessibilityService inside the temporary test emulator.
- The system reported that the accessibility service was **bound**. Opening the fixture source from Home, then pressing its immediate simulated ad jump returned to the source with **one Back action**, an event-log duration of **462 ms**, and the same Android `ActivityRecord` token before and after.
- The delayed simulated jump (one second after pressing the test button) likewise returned with **one Back action**, an event-log duration of **321 ms**, and the same source `ActivityRecord` token.
- This headless integration run did **not** assert preservation of typed text, scroll position, or a real advertisement. The recorded duration measures the app's return chain after it starts recovery, not total time from a physical shake or user tap. The two fixture apps contain no real ads.
- Earlier Android 15 emulator attempts were inconclusive: setting the secure accessibility setting through ADB left the service binding or the automation incomplete. That result does not prove whether a user-enabled service works on Android 15.

## Not yet verified

Installation and UI rendering on a phone; vendor background restrictions; Back behavior across real apps such as 高德地图; exact text/scroll/page restoration; physical shake integration; split screen and work profiles. The source and target fixtures and the protocol in `TESTING.md` are provided for these checks.

The 26 tests exercise the pure state machine. They do **not** constitute 26 Android integration or real-ad tests. Foreground package equality does not prove restoration of the original page.

## Scope of the promise

The application attempts to return after a cross-app jump inside a user-selected protection window. It cannot reliably classify all ads, block motion sensors in other applications, or universally restore same-app advertisement pages. Legitimate external login/payment/share flows may also be returned unless paused or allowed.

Android backup behavior was checked against [Android's Auto Backup documentation](https://developer.android.com/identity/data/autobackup).
