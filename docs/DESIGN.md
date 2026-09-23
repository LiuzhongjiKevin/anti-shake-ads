# Design and implementation record

Approved goal: no-root Android prototype returning from shake-triggered and clicked cross-app ad redirects, built after reviewing existing projects.

Chosen reuse: MIT Android-Touch-Helper service shell and package discovery. New pure Java state machine, foreground-verifying controller, native settings UI and controlled test apps. Same-package internal advertising and universal page restoration are explicitly outside first-version guarantees.

Protection defaults: 10-second window; choices 5/10/20/30; maximum 3 Back attempts per recovery and 6 per source session; 120ms stable-source confirmation; at least 450ms between Back attempts; one optional relaunch per session, off by default. A click never confers permission. User-configured pair allowance and pause take precedence. Window events are sampled after 40ms; recoveries recheck at 100ms intervals. These are prototype parameters, not measured latency claims.

Read permission: Android requires `canRetrieveWindowContent` for the window/root metadata used by verification. The code only reads package names, window types and active/focused flags, not text, fields or screenshots. System UI, discovered Home/IME/settings/dialer packages, lock screen and unknown foreground cause cancellation. A source app's input-method or other system interaction can therefore cause a missed interception; this favors stopping over pressing Back on an unrelated interface.

Implementation sequence: executable failing core-test setup → state machine → 26 passing behavior cases → Android adapter/UI → two fixture apps → build, static analysis and review → signed debug distribution. Work directory was unexpectedly removed by the environment during development; source was restored and all core cases rerun, followed by a saved source checkpoint. Subsequent validation is against the restored source.

Public repository requested by the user: `anti-shake-ads`. No personal conversation history, signing keys, credentials or local environment paths belong in the repository.
