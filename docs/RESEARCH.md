# Open-source comparison

Research snapshot: 2026-09-22. Findings concern the inspected versions, not a permanent claim about future upstream licenses or capabilities.

| Project | Relevance | Decision |
| --- | --- | --- |
| [JiangZi0721/shakeguard](https://github.com/JiangZi0721/shakeguard) | Closest cross-application return approach; source/target tracking and retry logic | No explicit license found in the inspected repository. Do not copy its source. |
| [Android-Touch-Helper](https://github.com/zfdang/Android-Touch-Helper) | Accessibility service lifecycle, launcher/HOME/IME discovery, ad skip tooling; MIT | Adapt the small reusable lifecycle/discovery shell with attribution. Independently implement the return state machine. |
| [GKD](https://github.com/gkd-kit/gkd) | Rule-driven accessibility automation | A full selector/rule engine is larger than the required return prototype; no source incorporated. |
| AutoSkip / AdClose | Adjacent skipping or elevated-hook approaches | Not selected for the no-root accessibility-first implementation; no source incorporated. |
| [huhai66/ShakeGuard](https://github.com/huhai66/ShakeGuard) | Adjacent skip-click implementation | No explicit license found during inspection; no source incorporated. |

The design uses package/window observation rather than click detection: a click alone must not grant permission for an ad redirect. The user selects protected sources and allowed destinations. A short protection window and an immediate pause control limit interference with intended navigation.

Upstream source identity and all adapted portions are documented in `../THIRD_PARTY_NOTICES.md` with the original MIT text. No third-party ad rules or subscription data are bundled.
