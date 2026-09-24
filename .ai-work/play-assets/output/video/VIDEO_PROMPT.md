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

### 📍 Scene 01 (00:00 - 00:04) | Local Digital Signage
- **Text Overlay**: `Digital Signage, Managed Locally`
```text
A smooth cinematic push-in shot inside a modern minimalist cafe. The camera tracks toward a compatible Android display showing a food menu assembled from local images and video. Warm studio lighting, shallow depth of field, fluid motion, professional commercial tech aesthetic. --ar 16:9
```

### 📍 Scene 02 (00:04 - 00:08) | Instant Local Wi-Fi Web Control
- **Text Overlay**: `Control From a Browser on Your Local Network`
```text
Cinematic over-the-shoulder shot of a store manager operating a sleek laptop on a cafe table. On the laptop browser screen, the user drags and drops a new retail promotional video. The camera smoothly pulls focus to the background wall TV, which instantly updates its display seamlessly over local Wi-Fi without delay. Crisp tech interface, subtle emerald green (#1A8754) Wi-Fi glow, photorealistic commercial product video. --ar 16:9
```

### 📍 Scene 03 (00:08 - 00:12) | Multi-Format Media Support
- **Text Overlay**: `Videos, Images & Scrolling Text`
```text
Slow panning commercial shot of a digital signage display. The screen shows a video, a food promotion image, and a scrolling text banner. Use realistic playback and legible content in a retail boutique setting. --ar 16:9
```

### 📍 Scene 04 (00:12 - 00:16) | Local Media Playback
- **Text Overlay**: `Play Media Stored on Your Device`
```text
A product shot of a compatible Android display playing an image/video playlist from local files in a retail space. Show a small neutral local-file cue and natural network status; do not depict a shield, uninterrupted service, or a claim that remote content works offline. Sophisticated lighting, clean shadows, photorealistic. --ar 16:9
```

### 📍 Scene 05 (00:16 - 00:20) | Smart Playlists & Recovery
- **Text Overlay**: `Playlists & Automatic Looping`
```text
A commercial shot of image and video playlist items advancing on a compatible Android display. Use simple loop indicators without suggesting scheduling or failover guarantees. Restrained green accents, clean depth of field, premium tech commercial style. --ar 16:9
```

### 📍 Scene 06 (00:20 - 00:24) | Multi-Screen Sync & Brand Resolve
- **Text Overlay**: `Multi-Screen Fleet Synchronization · LocalSignage`
```text
A wide cinematic pull-back shot revealing a restaurant with multiple displays showing assigned promotional content. Keep the hardware generic and treat multi-display synchronization as a concept until target devices are validated. Resolve into a clean brand frame with the LocalSignage logo and restrained green accents. --ar 16:9
```

---

## 🤖 Full Combined Storyboard Prompt

```text
Cinematic 24-second commercial product video for LocalSignage, 1920x1080 landscape, 60fps.
[00:00-00:04] Smooth tracking shot of a compatible Android display playing a local image/video playlist in a cafe. Overlay: "Digital Signage, Managed Locally".
[00:04-00:08] A manager selects a promo file in a browser on the same local network; the display updates after the command completes. Overlay: "Control From a Browser on Your Local Network".
[00:08-00:12] Show video, images, and scrolling text on a display. Overlay: "Videos, Images & Scrolling Text".
[00:12-00:16] Show a playlist of local files on a display with a restrained local-file cue; avoid reliability guarantees or claims about remote sources. Overlay: "Play Media Stored on Your Device".
[00:16-00:20] Show image and video playlist items advancing on a display, with a simple loop indicator. Overlay: "Playlists & Automatic Looping".
[00:20-00:24] Wide interior shot of multiple displays with assigned content, ending with a clean brand title card featuring BRAND-ICON-01 and LocalSignage. Treat device compatibility as unverified concept art.
Style: Photorealistic 8k commercial video, warm natural lighting, fluid camera movement, crisp text typography, professional retail tech presentation.
```
