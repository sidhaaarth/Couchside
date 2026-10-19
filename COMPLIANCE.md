# Compliance

Requirements from Amazon's Fire TV developer documentation and the hackathon rules, and how Couchside meets each one.

| Requirement | How we met it |
|---|---|
| Fire OS 14 requires `targetSdkVersion 34` | `targetSdk 34` |
| Support Fire OS 7/8 (API 28/30) | `minSdk 28` |
| Fire OS has no Google services | No Google Play or Firebase dependencies |
| Fire TV honors `LEANBACK_LAUNCHER` | Declared, with a TV banner; touchscreen not required |
| Input via remote key events; every element reachable with the D-pad | All screens navigable with the D-pad |
| Clearly show which element has focus | Orange focus outline on cards and buttons |
| Back returns to the previous screen | Player → Details → Movie list |
| Design for 1080p; body text ≥ 14sp | 1080p layouts, 18sp+ text |
| Keep the device awake during playback | Screen-on flag only while playing |
| Request audio focus; pause or duck on loss; abandon when done | Media3 player handles audio focus |
| Pause when audio output disconnects | "Becoming noisy" handling on |
| Publish a media session (transport controls, Alexa) | Media3 `MediaSession` while playing |
| Alexa voice permission (Fire OS before 14) | Added to the manifest |
| Release playback resources in `onStop()`, not `onDestroy()` | Player and session released in `onStop()`, rebuilt in `onStart()` |
| Hackathon: Fire OS app in an approved framework | Native Android (Kotlin) |
| Hackathon: AWS services with documented integration | Bedrock (Nova), Lambda, IAM, documented in README and submission |

Sources: [Fire OS 14](https://developer.amazon.com/docs/fire-tv/fire-os-14.html) · [Differences from Android TV](https://developer.amazon.com/docs/fire-tv/differences-from-android-tv-development.html) · [Remote input](https://developer.amazon.com/docs/fire-tv/remote-input.html) · [Design & UX guidelines](https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html) · [Multimedia app requirements](https://developer.amazon.com/docs/fire-tv/multimedia-app-requirements.html) · [MediaSession integration](https://developer.amazon.com/docs/fire-tv/mediasession-api-integration.html)
