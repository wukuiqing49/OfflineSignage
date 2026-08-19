# VIDEO_PROMPT - Google Play Preview Video

- Source Brief: `VIDEO_BRIEF.md`
- Asset Type: Google Play Preview Video (YouTube Link)
- Generation: Derived from the validated project brief

---

## 🎯 Target Specifications (Google Play Official Requirements)
- **Canvas Resolution**: `1920x1080 px, 60fps (16:9 Landscape)`
- **Target Duration**: `24 Seconds` (6 Continuous 4-Second Scenes)
- **Distribution**: Upload to **YouTube** (Public / Unlisted), add URL to Google Play Console
- **Cover Thumbnail**: Automatically uses your **Feature Graphic (1024x500)**
- **Sound Policy**: Autoplay is **muted by default**; high-contrast text overlays are mandatory

---

## 🎬 Recommended Production Workflow: Image-to-Video
1. Generate 6 high-res 16:9 static frames using Screenshot prompts (`SCREENSHOT_01` to `06`).
2. Upload each first-frame into **Runway Gen-3 Alpha / Kling (可灵) / Sora / Luma**.
3. Paste the corresponding Scene Prompt below to generate 4-second dynamic clips.
4. Stitch clips in CapCut / Premiere, add text overlays and rights-cleared background music.

---

## 🚀 Shot-by-Shot Video Prompts (Runway Gen-3 / Kling / Sora)

### 📍 Scene 01 (00:00 - 00:04) | Hook: 100% Offline Digital Signage
- **Text Overlay**: `100% Offline Digital Signage & Menu Board`
```text
A smooth cinematic push-in shot inside a modern minimalist cafe. The camera slowly tracks forward toward a sleek wall-mounted ultra-thin 4K Android TV. The screen bursts to life with a vibrant, high-definition digital food menu board featuring gourmet burgers, specialty artisan coffee, and crisp price tags. Warm studio lighting, photorealistic 8k, shallow depth of field, fluid motion, professional commercial tech aesthetic. --ar 16:9
```

### 📍 Scene 02 (00:04 - 00:08) | Instant Local Wi-Fi Web Control
- **Text Overlay**: `Control From Any Web Browser · No PC Software Needed`
```text
Cinematic over-the-shoulder shot of a store manager operating a sleek laptop on a cafe table. On the laptop browser screen, the user drags and drops a new retail promotional video. The camera smoothly pulls focus to the background wall TV, which instantly updates its display seamlessly over local Wi-Fi without delay. Crisp tech interface, subtle emerald green (#1A8754) Wi-Fi glow, photorealistic commercial product video. --ar 16:9
```

### 📍 Scene 03 (00:08 - 00:12) | Multi-Format Media Support
- **Text Overlay**: `4K Videos, Images & Live Scrolling Banners`
```text
Slow panning commercial shot of a vibrant large-screen digital signage display. The screen shows a dynamic split-screen composition: a continuous looping 4K fashion promotional video playing smoothly on the left, an appetizing food special photo slideshow on the right, and a bright scrolling marquee ticker banner along the bottom. Vibrant colors, ultra-high resolution, zero screen glare, premium retail boutique setting. --ar 16:9
```

### 📍 Scene 04 (00:12 - 00:16) | 100% Offline Continuous Reliability
- **Text Overlay**: `Zero Cloud Subscriptions · Never Goes Black`
```text
A confident hero shot of a standalone digital signage totem kiosk and wall display playing smoothly 24/7 in an architectural retail space. An elegant, glowing 3D translucent shield badge with a local storage icon pulses softly next to the screen. Continuous seamless looping playback without buffering, highlighting 100% local device storage and zero cloud dependency. Sophisticated lighting, clean shadows, photorealistic. --ar 16:9
```

### 📍 Scene 05 (00:16 - 00:20) | Smart Playlists & Recovery
- **Text Overlay**: `Smart Playlists & Commercial Auto-Recovery`
```text
A futuristic commercial 3D shot showing a floating carousel of media playlist cards smoothly transitioning and feeding into an Android TV screen. Floating subtle timer icons and circular loop indicators show automated playlist sequencing and failover recovery. Emerald green accents, clean depth of field, sleek UI motion graphics, premium tech commercial style. --ar 16:9
```

### 📍 Scene 06 (00:20 - 00:24) | Multi-Screen Sync & Brand Resolve
- **Text Overlay**: `Multi-Screen Fleet Synchronization · LocalSignage`
```text
A wide cinematic pull-back shot revealing a modern multi-screen restaurant interior. Three ultra-thin Android TV screens mounted along the counter display synchronized, harmonized digital menu boards and advertisements in perfect rhythm. The scene smoothly resolves into a clean, minimalist brand closing frame with the LocalSignage logo and green emerald brand accents. Cinematic 8k lighting, high-end commercial finale. --ar 16:9
```

---

## 🤖 Full Combined Storyboard Prompt

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
