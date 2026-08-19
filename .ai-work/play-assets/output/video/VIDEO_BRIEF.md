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
- Primary Message: 100% Offline Digital Signage Player & Menu Board
- Blocking Issues: None.

## 2. Product Analysis

LocalSignage is a local-first, offline digital signage player for Android TV, tablets, and media boxes, featuring an instant drag-and-drop web console hosted directly on the device over local Wi-Fi.

## 3. Verified Product Facts

| Claim ID | Claim | Status | Advertisable | Evidence | Notes |
|---|---|---|---|---|---|
| PF-001 | The Android device hosts a local browser control console. | VERIFIED | true | `SignageService.kt:46-58`; `KtorSignageServer.kt:84-100` | Same-network browser control. |
| PF-002 | Uploaded image/video media can play directly or become a playlist. | VERIFIED | true | `KtorSignageServer.kt:400-449` | Reachable upload-to-playback chain. |
| PF-003 | Image, video, live stream, web/HTML, and text content modes are implemented. | VERIFIED | true | `SignagePlaybackController.kt:345-414`; `web_console.html:44` | Broad content support. |
| PF-004 | Local devices are discovered through NSD and UDP fallback. | VERIFIED | true | `LocalDeviceDiscovery.kt:24-121` | LAN scope discovery. |
| PF-005 | Paired devices can receive assigned playlists. | VERIFIED | true | `KtorSignageServer.kt:189-230`; `KtorSignageServer.kt:752-770` | Local network fleet synchronization. |
| PF-006 | Playback uses a foreground service and boot/package-update receiver. | VERIFIED | true | `SignageService.kt:46-75`; `SignageBootReceiver.kt:8-18` | Auto-start and recovery. |
| PF-008 | Local media is stored and played from device files with restored playback state. | VERIFIED | true | `SignageStore.kt:672-710`; `SignagePlaybackController.kt:270-307` | 100% offline local playback. |

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

- Product Category: 100% Offline Android Digital Signage Player & Menu Board
- Platform: Android TV, Tablets, and TV Boxes
- Primary Audience: Restaurants, cafes, retail stores, offices, and venue operators seeking zero-cloud-cost signage
- Primary User Problem: Costly cloud CMS subscriptions and black screens during internet outages
- Primary Value Proposition: 100% offline reliability, instant local browser control, zero cloud subscriptions
- Primary Marketing Message: 100% Offline Digital Signage Player & Menu Board on Android
- Primary Differentiator: 100% local-file storage with local Wi-Fi web browser control
- Supporting Features: 4K video loops, image slideshows, scrolling text marquees, HTML widgets, and fleet sync

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

Turn any Android TV or tablet into an offline digital signage player and dynamic menu board with local Wi-Fi control.

## 12. Storyboard

### Scene 01 | 00:00-00:04

- Purpose: Establish product category and core hook.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: Smooth tracking shot in a modern cafe focusing on a wall-mounted 4K Android TV displaying a vibrant food menu board and BRAND-ICON-01.
- Demo Data: Gourmet Cafe menu board
- Text Overlay: 100% Offline Digital Signage & Menu Board
- Text Position: top left
- Visual Focus: 4K Android TV display and BRAND-ICON-01
- Camera / Crop: full 1920x1080 canvas
- Transition: smooth cinematic glide
- Positioning Relationship: Opens with primary value proposition and brand mark.
- Product Feature Evidence: PF-001; PF-003; PF-008
- Recording Clip ID: N/A

### Scene 02 | 00:04-00:08

- Purpose: Showcase instant driver-free local Wi-Fi web browser control.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: Manager drags a promo file on a laptop browser; the wall TV screen updates instantaneously over local Wi-Fi.
- Demo Data: LocalSignage Web Console drag-and-drop
- Text Overlay: Control From Any Web Browser · No PC Software Needed
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
- Visible Result: Dynamic split showcase of 4K video loops, high-res photos, and scrolling text marquees.
- Demo Data: Fashion 4K video, seasonal poster, text ticker
- Text Overlay: 4K Videos, Images & Live Scrolling Banners
- Text Position: top center
- Visual Focus: split layout with dynamic text and video
- Camera / Crop: full 1920x1080 canvas
- Transition: smooth multi-layer transition
- Positioning Relationship: Demonstrates multi-format capability.
- Product Feature Evidence: PF-002; PF-003
- Recording Clip ID: N/A

### Scene 04 | 00:12-00:16

- Purpose: Communicate 100% offline continuous playback reliability.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: Wi-Fi disconnected icon fades out while playback continues uninterrupted with a glowing 100% Local Storage shield.
- Demo Data: 100% Local Storage shield
- Text Overlay: Zero Cloud Subscriptions · Never Goes Black
- Text Position: center left
- Visual Focus: uninterrupted playback and offline badge
- Camera / Crop: full 1920x1080 canvas
- Transition: pulse and continuous loop
- Positioning Relationship: Delivers the core offline differentiator.
- Product Feature Evidence: PF-006; PF-008
- Recording Clip ID: N/A

### Scene 05 | 00:16-00:20

- Purpose: Show automated playlist looping and recovery.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: 3D perspective timeline showing automated playlist progression, slide timers, and seamless auto-looping.
- Demo Data: 3D playlist timeline
- Text Overlay: Smart Playlists & Commercial Auto-Recovery
- Text Position: top left
- Visual Focus: 3D timeline carousel
- Camera / Crop: full 1920x1080 canvas
- Transition: horizontal slot advance and loop return
- Positioning Relationship: Connects automated reliability to commercial use.
- Product Feature Evidence: PF-002; PF-005; PF-006
- Recording Clip ID: N/A

### Scene 06 | 00:20-00:24

- Purpose: Multi-screen fleet synchronization and brand recall.
- Real App Screen: N/A
- Starting State: N/A
- User Action: N/A
- Visible Result: Wide panoramic view of three synchronized Android TV screens across a restaurant, concluding with BRAND-ICON-01.
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
[00:00-00:04] Smooth tracking shot in a modern boutique cafe focusing on a wall-mounted 4K Android TV lighting up with a delicious digital menu board and BRAND-ICON-01. Overlay: "100% Offline Digital Signage & Menu Board".
[00:04-00:08] Over-the-shoulder shot of a manager dragging a promo file on a laptop browser; the wall screen updates instantaneously over local Wi-Fi. Overlay: "Control From Any Web Browser · No PC Software Needed".
[00:08-00:12] Dynamic showcase of 4K video loops, high-res photos, and scrolling text marquees. Overlay: "4K Videos, Images & Live Scrolling Banners".
[00:12-00:16] Network indicator shows offline mode, but playback continues uninterrupted with zero buffering. Overlay: "Zero Cloud Subscriptions · Never Goes Black".
[00:16-00:20] 3D floating playlist timeline showing seamless loops and auto-resume. Overlay: "Smart Playlists & Commercial Auto-Recovery".
[00:20-00:24] Wide interior shot of three synchronized displays across a store, ending with a clean brand title card featuring BRAND-ICON-01 and LocalSignage. Overlay: "Multi-Screen Fleet Synchronization · LocalSignage".
Style: Photorealistic 8k commercial video, warm natural lighting, fluid camera movement, crisp text typography, professional retail tech presentation.
```
