# Device test protocol

Do not substitute package equality for exact-page restoration. The prototype only certifies the former in its log. Record the device model, Android version and results below.

## Setup

Install the three debug APKs. Enable the accessibility service and global switch in the main app. Select **only** the fixture source for the first test and use a 30-second window. Keep fallback off. Return Home before each test, then open the fixture source. Close the keyboard before the jump.

| Case | Procedure | Required result |
| --- | --- | --- |
| Click | Press immediate jump | Attempts Back; confirms stable exact source if restored. |
| No click at jump time | Press delayed jump, wait one second | Same return policy as click. |
| Shake | Arm one shake, move device | Returns after sensor-triggered redirect; service itself does not monitor sensors. |
| Preserved state | Enter text, record instance ID, jump | Same text and instance ID if original Activity survives. |
| Scroll | Scroll to bottom and jump | Same scroll position on return. |
| New task | Use NEW_TASK button | Verify exact source; destination may briefly appear. |
| Consumed Back | Use Back-consuming target | Stops after capped attempts; no infinite return loop. |
| Relaunch fallback | Enable fallback, repeat consumed-Back case | At most one source launch; log says reopened, never exact-page restored. |
| Browser | Set a default browser, open browser test | Verify source recovery, not merely browser home. |
| Window expired | Set 5 seconds, wait >5 seconds then jump | No automatic Back. |
| Allowed pair | Allow fixture source→target then jump | No Back. |
| Pause | Pause protection then launch source | No Back during pause. |
| Internal page | Open same-app internal page | No automatic Back. |
| Manual Home | Press Home during return attempts | Stops; never returns out of launcher. |
| Lock | Lock during pending return | No action on lock screen; no stale retry after unlock. |
| Disable | Turn off service or switch | Pending actions cancelled; state label accurate. |
| Real applications | Test 2–3 selected apps independently | Record results individually; do not extrapolate universal compatibility. |

Multi-page target testing: with protection paused, open multiple target pages, then resume and repeat a source launch into its existing task. Confirm Back attempts stay bounded. These fixtures do not reproduce every OEM task-stack policy or ad SDK.

The two fixture apps do not request INTERNET permission or display real ads. The browser test opens `https://example.com` in the user's browser.
