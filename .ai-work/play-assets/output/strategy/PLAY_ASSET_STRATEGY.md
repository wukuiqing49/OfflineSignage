# PLAY_ASSET_STRATEGY

## 1. Executive Summary

- Status: CONCEPT_READY
- Asset Mode: CONCEPT
- App Name: LocalSignage
- Target Locale: en-US
- Target Device Types: Android phones, tablets, and TV displays
- Primary Marketing Message: 100% Offline Digital Signage Player & Dynamic Menu Board for Android.
- Blocking Issues: None.

## 2. Product Analysis

Local Signage is an Android digital signage player whose device also hosts a local browser control console. A phone or computer on the same network can send content and control playback. Images, videos, streams, web/HTML content, and text are supported. Locally uploaded media is stored on the Android device and can be restored from local state. The product is not a cloud CMS, hardware player, physical sign service, or menu-only application.

## 3. Verified Product Facts

| Claim ID | Claim | Status | Advertisable | Evidence | Notes |
|---|---|---|---|---|---|
| PF-001 | Local Signage runs a local browser control console on the Android device. | VERIFIED | true | `app/src/main/java/com/wkq/localsignage/SignageService.kt:46-58`; `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/server/KtorSignageServer.kt:84-100` | The foreground service starts the runtime and Ktor web console. |
| PF-002 | The browser console uploads image and video files and can play one item or create a playlist for multiple files. | VERIFIED | true | `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/server/KtorSignageServer.kt:400-449` | Upload, persistence, playlist creation, and playback command form a reachable chain. |
| PF-003 | The player supports image, video, live stream, web/HTML, and text content modes. | VERIFIED | true | `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/player/SignagePlaybackController.kt:345-414`; `feature/feature_app/src/main/res/raw/web_console.html:44` | Claims are limited to implemented content modes. |
| PF-004 | Devices can be discovered on a local network through NSD and UDP fallback. | VERIFIED | true | `app/src/main/java/com/wkq/localsignage/SignageService.kt:28-55`; `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/discovery/LocalDeviceDiscovery.kt:24-121` | Discovery runs with the signage service. |
| PF-005 | A paired device can receive an assigned playlist through the local control API. | VERIFIED | true | `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/server/KtorSignageServer.kt:189-230`; `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/server/KtorSignageServer.kt:752-770` | Device assignment, synchronization, and fleet playback routes are reachable. |
| PF-006 | Playback infrastructure runs in a foreground service and restarts after boot or app updates. | VERIFIED | true | `app/src/main/java/com/wkq/localsignage/SignageService.kt:46-75`; `app/src/main/java/com/wkq/localsignage/SignageBootReceiver.kt:8-18`; `app/src/main/AndroidManifest.xml:35-45` | Marketing wording must not promise recovery on every vendor-modified Android build. |
| PF-007 | Trial, subscription, and one-time purchase code paths exist. | UNVERIFIED | false | `feature/feature_app/src/main/java/com/wkq/localsignage/monetization/MonetizationRepository.kt:25-122`; `feature/feature_app/src/main/java/com/wkq/localsignage/monetization/EntitlementPolicy.kt:7-46` | Play Console product availability and prices were not verified. |
| PF-008 | Locally uploaded media is stored on the Android device and played from local files; playback state is restored when the player initializes. | VERIFIED | true | `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/storage/SignageStore.kt:672-710`; `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/player/SignagePlaybackController.kt:270-307`; `feature/feature_app/src/main/java/com/wkq/localsignage/feature/app/player/SignagePlaybackController.kt:397-405` | Offline wording applies to local content, not remote web pages or live streams. |

### Do Not Advertise

- Google Play prices, subscription availability, or lifetime purchase availability until Play Console is verified.
- Cloud CMS, public internet management, analytics, scheduling, enterprise device management, or AI.
- Android TV/TV Box compatibility until device and manifest validation is complete.
- Absolute offline claims for remote web pages and live streams.
- Absolute privacy or security claims.

## 4. ASO / SEO / GEO Positioning

The category entry point is `digital signage player`, not the broader physical-sign term `signage` and not the SaaS-heavy phrase `digital signage software`. The Android and app terms explain platform and acquisition intent. `offline digital signage`, `network digital signage player`, and `html5 digital signage` describe verified differentiators but carry lower Web search volume. Google Keyword Planner data is used as Web demand evidence and vocabulary guidance, not as Google Play search-volume evidence.

## 5. Selected Keywords

| Keyword | Classification | Source | Metrics | Product Fact | Reason |
|---|---|---|---|---|---|
| digital signage player | SELECTED_PRIMARY | Keyword Stats 2026-08-18 at 16_34_41.csv | Avg monthly searches 5000; competition high; index 99 | PF-002; PF-003; PF-008 | Exact product shape: media playback, playlists, and local content. |
| signage app | SELECTED_SECONDARY | Keyword Stats 2026-08-18 at 16_34_41.csv | Avg monthly searches 5000; competition medium; index 49 | PF-001; PF-003 | Strong app-discovery intent supported by browser control and multi-format playback. |
| digital signage app | SELECTED_SECONDARY | Keyword Stats 2026-08-18 at 16_34_41.csv | Avg monthly searches 500; competition medium; index 38 | PF-001; PF-003 | More precise App-category phrase than the broad signage term. |
| digital signage media player | SELECTED_SECONDARY | Keyword Stats 2026-08-18 at 16_34_41.csv | Avg monthly searches 500; competition medium; index 56 | PF-002; PF-003 | Directly supports the content-format story. |
| android digital signage | SELECTED_SECONDARY | Keyword Stats 2026-08-18 at 16_34_41.csv | Avg monthly searches 500; competition medium; index 64 | PF-002; PF-003; PF-006 | Establishes the verified Android platform. |
| offline digital signage | SELECTED_SECONDARY | Keyword Stats 2026-08-18 at 16_34_41.csv | Avg monthly searches 50; competition low; index 0 | PF-008 | Differentiates local-file playback without claiming remote content is offline. |
| network digital signage player | SELECTED_SECONDARY | Keyword Stats 2026-08-18 at 16_34_41.csv | Avg monthly searches 50; competition low; index 21 | PF-001; PF-004; PF-005; PF-008 | Maps to LAN control, discovery, assignment, and local playback. |
| html5 digital signage | SELECTED_SECONDARY | Keyword Stats 2026-08-18 at 16_34_41.csv | Avg monthly searches 50; competition low; index 0 | PF-003; PF-008 | Supports the styled local HTML content story. |

## 6. Rejected Keywords

- `digital signage`: too broad; mixes software, hardware, displays, services, and physical signs.
- `digital signage software`: may imply a full cloud CMS that the current product does not provide.
- `wifi signage`: attractive volume but unresolved hardware and physical-sign intent.
- Brand and competitor terms: rejected because they do not describe Local Signage generically.
- LED/LCD, monitor, box, stick, screen-stand, and physical-sign terms: hardware or service intent.
- Menu, restaurant, cafe, and food-board terms: adjacent scenario only, not the product category.
- Free, price, cost, and discount terms: no verified current-market pricing evidence.

## 7. Listing Asset Positioning

- Product Category: Android digital signage player
- Platform: Android phones and tablets
- Primary Audience: Small operators who need a dedicated local display without deploying a cloud CMS
- Primary User Problem: Reusing an Android device as a reliable display while controlling content from another device on the same network
- Primary Value Proposition: Send and play signage content locally from a browser
- Primary Marketing Message: Turn an Android device into a locally controlled digital signage player.
- Primary Differentiator: Local-file playback plus browser control on the local network
- Supporting Features: Text, images, video, live streams, web/HTML, playlists, device discovery, and per-device playlist assignment

## 8. Cross-Asset Message Map

| Asset | User Question | Primary Message | Feature Evidence | Avoid Repeating |
|---|---|---|---|---|
| Feature Graphic | What is this app? | Digital signage player for Android | PF-002; PF-003; PF-006 | Do not list every format or show a workflow. |
| Screenshots | What can it really do? | Browser control, content formats, local playback, playlists, and LAN devices | PF-001; PF-002; PF-003; PF-004; PF-005; PF-008 | Do not repeat one headline across all panels. |
| Preview Video | How does the workflow work? | Prepare content, send locally, play, and keep the display running | PF-001; PF-002; PF-003; PF-006; PF-008 | Do not copy the Feature Graphic composition. |

## 9. Shared Visual Style

- Color Direction: Use verified UI greens `#1B6B3A` and `#71C887`, off-white `#F4F7F5`, charcoal `#17211B`, and preserve the blue colors inside BRAND-ICON-01 without recoloring.
- Background Direction: Clean off-white or charcoal fields with flat color blocks, restrained network lines, and no decorative gradient blobs.
- Typography Direction: Clear industrial sans serif, compact headings, regular letter spacing, and strong small-size readability.
- Device Frame Style: none in CONCEPT; Production may use only immutable real screenshots after capture.
- Icon / Logo Treatment: Use BRAND-ICON-01 at original proportions with clear space; do not redraw, recolor, crop, or add badges.

## 10. Demo Data System

Use one reproducible fictional campaign named `Weekend Update`: a `Store Hours` text notice, one rights-cleared product image, one short MP4 promotion, and one local HTML status board. The Concept package may name these content types but must not invent App screens. Production capture must prepare the same files locally, exclude personal information, and record source licenses.

## 11. Localization

The first package is en-US. All external headlines and supporting text are English. Concept panels contain no App UI. Production must capture the real en-US interface and must not translate screenshot pixels. Other locales require separate real captures and localized external text.

## 12. Shared Prohibited Claims

- No rankings, awards, download counts, discounts, or pricing.
- No fake UI, device frames, buttons, navigation, dashboards, or invented screen data in CONCEPT.
- No claim that remote web pages or live streams work offline.
- No Cloud CMS, public remote management, scheduling, analytics, AI, or enterprise-control claims.
- No Android TV compatibility claim until verified.
- No modification of the real App icon.

## 13. Required Assets

| Asset ID | Type | Path | Locale | Status | Usage |
|---|---|---|---|---|---|
| BRAND-ICON-01 | App Icon | app/src/main/ic_launcher-playstore.png | neutral | READY | Feature Graphic, Concept screenshot panels, and video opening/ending mark |

## 14. Official Sources Checked

- Sources: https://support.google.com/googleplay/android-developer/answer/9866151 ; https://play.google.com/intl/en_us/badges/
- Checked At: 2026-08-18; browser unavailable in current environment
- Current Policy Status: UNVERIFIED_CURRENT_POLICY

## 15. Blocking Issues

- Before Production, verify current Google Play image, screenshot, Preview Video, and brand-asset requirements online.
- Capture real en-US App screenshots and a real workflow recording before producing submission-ready screenshot/video assets.
- Confirm target device listing groups in Play Console.
