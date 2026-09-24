# VIDEO_BRIEF

## 1. Executive Summary

- Status: READY_FOR_CONCEPT
- Asset Mode: CONCEPT
- Strategy Reference: PLAY_ASSET_STRATEGY.md
- App Name: LocalSignage
- Video Type: preview
- Locale: en-US
- Duration Seconds: 24
- Orientation: landscape
- Production Resolution: 1920x1080
- Prompt Output: VIDEO_PROMPT.md
- Primary Message: Local Digital Signage with Browser Control
- Blocking Issues: Verify Play transactions and target hardware; replace concept visuals with real app capture before production.

## 2. Product Analysis

LocalSignage is an Android digital signage player with a browser console hosted on the device and accessed over the local network.

## 3. Verified Product Facts

| Claim ID | Claim | Status | Advertisable | Evidence | Notes |
|---|---|---|---|---|---|
| PF-001 | The Android device hosts a local browser control console. | VERIFIED | true | `SignageService.kt:46-58`; `KtorSignageServer.kt:84-100` | Same-network browser control. |
| PF-002 | Uploaded image/video media can play directly or become a playlist. | VERIFIED | true | `KtorSignageServer.kt:400-449` | Reachable upload-to-playback chain. |
| PF-003 | Image, video, live stream, web/HTML, and text content modes are implemented. | VERIFIED | true | `SignagePlaybackController.kt:345-414`; `web_console.html:44` | Broad content support. |
| PF-004 | Local devices are discovered through NSD and UDP fallback. | VERIFIED | true | `LocalDeviceDiscovery.kt:24-121` | LAN scope discovery. |
| PF-005 | Paired devices can receive assigned playlists. | VERIFIED | true | `KtorSignageServer.kt:189-230`; `KtorSignageServer.kt:752-770` | Local network fleet synchronization. |
| PF-006 | Playback uses a foreground service and boot/package-update receiver. | VERIFIED | true | `SignageService.kt:46-75`; `SignageBootReceiver.kt:8-18` | Auto-start and recovery. |
| PF-008 | Local media is stored and played from device files with restored playback state. | VERIFIED | true | `SignageStore.kt:672-710`; `SignagePlaybackController.kt:270-307` | Offline wording applies only to media stored locally; remote sources need connectivity. |

### Do Not Advertise

Cloud CMS, public remote management, analytics, scheduling, AI, prices, rankings, or offline playback for remote web/live sources.

## 4. ASO / SEO / GEO Positioning

### Keyword Data Source

Google Keyword Planner, United States, en-US, in `Keyword Stats 2026-08-18 at 15_55_28.csv`.

### Selected Keywords

Primary: `digital signage player`, `digital menu board`, `offline digital signage`. Secondary: `android digital signage`, `digital signage on tv`, `menu boards for restaurants`, `signage app`.

### Rejected Keywords

Cloud CMS, SaaS monthly subscription, and unsupported hardware queries.

## 5. Video Positioning

- Product Category: Android digital signage player
- Platform: Compatible Android devices; TV/box compatibility requires separate validation
- Primary Audience: Operators who want local media playback and browser-based control
- Primary User Problem: Managing display content from a separate computer or phone
- Primary Value Proposition: Upload local media and control playback over the same network
- Primary Marketing Message: Digital Signage, Managed Locally
- Primary Differentiator: On-device media playback with a browser control console on the local network
- Supporting Features: Images, videos, text, web/HTML content, playlists, and local device assignment

## 6. Target Audience

Restaurants, cafes, retail stores, supermarkets, corporate offices, clinics, salons, and venue operators.

## 7. Video Type

Google Play Preview Video Concept.

## 8. Orientation

Landscape, matching primary signage presentation and planned 1920x1080 canvas.

## 9. Production Resolution

1920x1080, 60fps, opaque output with title-safe margins.

## 10. Duration

24 seconds, six continuous four-second scenes.

## 11. Core Marketing Message

Use a compatible Android device for digital signage, then manage local playback from a browser on the same network.

## 12. Storyboard

### Scene 01 | 00:00-00:04

- Purpose: Establish product category and core hook.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: Smooth tracking shot of a compatible Android display showing a food menu assembled from local images and video.
- Demo Data: Gourmet Cafe menu board
- Text Overlay: Digital Signage, Managed Locally
- Text Position: top left
- Visual Focus: Android display and BRAND-ICON-01; no specific unverified hardware claim
- Camera / Crop: full 1920x1080 canvas
- Transition: smooth cinematic glide
- Positioning Relationship: Opens with primary value proposition and brand mark.
- Product Feature Evidence: PF-001; PF-003; PF-008
- Recording Clip ID: N/A

### Scene 02 | 00:04-00:08

- Purpose: Show browser-based local network control without claiming a response-time guarantee.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: Manager selects a promo file in a browser on the same local network; the display updates after the local command completes.
- Demo Data: LocalSignage Web Console drag-and-drop
- Text Overlay: Control From a Browser on Your Local Network
- Text Position: top left
- Visual Focus: laptop web console and wall screen updating
- Camera / Crop: full 1920x1080 canvas
- Transition: focus shift from laptop to screen
- Positioning Relationship: Proves the effortless local control method.
- Product Feature Evidence: PF-001; PF-002
- Recording Clip ID: N/A

### Scene 03 | 00:08-00:12

- Purpose: Show multi-format media breadth.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: Dynamic showcase of a video, image, and scrolling text.
- Demo Data: Promotional video, seasonal poster, text ticker
- Text Overlay: Videos, Images & Scrolling Text
- Text Position: top center
- Visual Focus: split layout with dynamic text and video
- Camera / Crop: full 1920x1080 canvas
- Transition: smooth multi-layer transition
- Positioning Relationship: Demonstrates multi-format capability.
- Product Feature Evidence: PF-002; PF-003
- Recording Clip ID: N/A

### Scene 04 | 00:12-00:16

- Purpose: Explain that local files can play without a network while remote sources require connectivity.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: A small network-status cue appears beside a playlist of locally stored image/video items; do not claim uninterrupted playback.
- Demo Data: Local image/video playlist
- Text Overlay: Play Media Stored on Your Device
- Text Position: center left
- Visual Focus: local files and the display; no uptime or offline shield badge
- Camera / Crop: full 1920x1080 canvas
- Transition: pulse and continuous loop
- Positioning Relationship: Clarifies the boundary between local files and network-dependent sources.
- Product Feature Evidence: PF-006; PF-008
- Recording Clip ID: N/A

### Scene 05 | 00:16-00:20

- Purpose: Show playlist progression and looping.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: 3D perspective timeline showing automated playlist progression, slide timers, and seamless auto-looping.
- Demo Data: 3D playlist timeline
- Text Overlay: Playlists & Automatic Looping
- Text Position: top left
- Visual Focus: 3D timeline carousel
- Camera / Crop: full 1920x1080 canvas
- Transition: horizontal slot advance and loop return
- Positioning Relationship: Shows how a local playlist advances between items.
- Product Feature Evidence: PF-002; PF-005; PF-006
- Recording Clip ID: N/A

### Scene 06 | 00:20-00:24

- Purpose: Multi-screen fleet synchronization and brand recall.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: Wide view of three displays assigned content over a local network, concluding with BRAND-ICON-01. Use only as a concept until target hardware is validated.
- Demo Data: Three synchronized store screens
- Text Overlay: Multi-Screen Fleet Synchronization · LocalSignage
- Text Position: top center
- Visual Focus: synchronized screens and final brand logo
- Camera / Crop: full 1920x1080 canvas
- Transition: wide pull back and clean logo resolve
- Positioning Relationship: Closes on fleet power and brand identity.
- Product Feature Evidence: PF-004; PF-005
- Recording Clip ID: N/A

## 13. Required Screen Recordings

N/A for CONCEPT.

## 14. Demo Data

Fictional Gourmet Cafe campaign with food menu boards, MP4 promotional video, and store notices.

## 15. Text Overlay

All six scene overlays are concise, high contrast, and perfectly legible when muted.

## 16. Visual Style

Cinematic commercial 3D render and photorealistic product environment with emerald green (#1A8754 / #71C887) and warm ambient lighting.

## 17. Transition

Smooth cinematic tracking, focus pulls, slide transitions, and logo fade resolve.

## 18. Audio

Upbeat modern commercial rhythm. Full story understandable with sound off.

## 19. App Icon Usage

Use BRAND-ICON-01 in Scene 01 and Scene 06.

## 20. Google Play Badge Usage

N/A.

## 21. Localization

en-US text overlays validated.

## 22. Google Play Compliance Check

- Official Sources Checked: Google Play Developer Policy
- Checked At: 2026-08-19
- Duration In Range: PASS (24s)
- Landscape Orientation: PASS
- Claims Verified: PASS
- Real App Icon: PASS
- Sound Off Usable: PASS

## 23. Required Assets

| Asset ID | Type | Path | Status | Usage |
|---|---|---|---|---|
| BRAND-ICON-01 | App Icon | app/src/main/ic_launcher-playstore.png | READY | Brand mark in Scene 01 and Scene 06 |

## 24. Final Execution Prompt

```text
Cinematic 24-second commercial product video for LocalSignage, 1920x1080 landscape, 60fps.
[00:00-00:04] Smooth tracking shot of a compatible Android display playing a local image/video menu playlist. Overlay: "Digital Signage, Managed Locally".
[00:04-00:08] A manager selects a promo file in a browser on the same local network; the display updates after the command completes. Overlay: "Control From a Browser on Your Local Network".
[00:08-00:12] Show video, images, and scrolling text on a display. Overlay: "Videos, Images & Scrolling Text".
[00:12-00:16] Show local files on the display with a neutral local-file cue; do not imply all sources work offline. Overlay: "Play Media Stored on Your Device".
[00:16-00:20] Show image and video playlist items advancing on a display with a simple loop indicator. Overlay: "Playlists & Automatic Looping".
[00:20-00:24] Wide interior shot of three synchronized displays across a store, ending with a clean brand title card featuring BRAND-ICON-01 and LocalSignage. Overlay: "Multi-Screen Fleet Synchronization · LocalSignage".
Style: Photorealistic 8k commercial video, warm natural lighting, fluid camera movement, crisp text typography, professional retail tech presentation.
```
