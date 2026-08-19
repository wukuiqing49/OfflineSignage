# TV_SCREENSHOT_05_PROMPT

- Source Brief: `SCREENSHOT_BRIEF.md`
- Asset Type: TV Screenshot 05
- Generation: Derived from the validated project brief

---

## 🎯 Target Specifications (Google Play Official Requirements)
- **Exact Target Canvas**: `1920x1080 px`
- **Aspect Ratio**: `16:9 landscape`
- **File Format**: `24-bit PNG or JPEG` (Opaque background, strictly **NO ALPHA TRANSPARENCY**)
- **Color Space**: `sRGB` (Recommended)

---

## 🤖 Pure Visual 3D Background Prompt (Gemini / Imagen 3 / ChatGPT)

```text
Device Type: TV. Locale: en-US. Canvas: 1920x1080. Output: opaque PNG or JPEG.

A commercial 3D render of a floating carousel timeline of high-resolution playlist cards (promo video, special offer poster, breakfast menu) smoothly transitioning into a commercial screen with BRAND-ICON-01. Floating timer and loop icons indicate automated scheduling. Clean uncluttered upper space for overlay text. Futuristic yet clean commercial 3D UI render, smooth motion trail effect, studio depth of field.
```

---

## 🎨 Midjourney v6.1 Prompt (Raw Photo Style)

```text
/imagine prompt: A commercial 3D render of a floating carousel timeline of high-resolution playlist cards (promo video, special offer poster, breakfast menu) smoothly transitioning into a commercial screen with BRAND-ICON-01. Floating timer and loop icons indicate automated scheduling. Clean uncluttered upper space for overlay text. Futuristic yet clean commercial 3D UI render, smooth motion trail effect, studio depth of field. --ar 16:9 --v 6.1 --style raw
```

---

## 📐 Figma / PS Copy Overlay Card (Ready to Copy-Paste)
- **Main Headline (EN)**: `Smart Playlists & Automatic Looping` (Font: Inter / Roboto Bold, ~64-72pt)
- **Supporting Text (EN)**: `Auto-start on boot, custom slide timers, and failover recovery.` (Font: Inter / Roboto Regular, ~32-36pt)
- **Figma Layout Tip**: Paste the AI background image into Figma, create a text box at the top clean margin, and align text centrally with 48px padding.

---

## 💡 DALL-E 3 & Flux Optimization Note
- Generate at 1792x1024 (16:9 Landscape), then fit to 1920x1080 px.
- **Compliance Reminder**: Before uploading to Google Play Console, verify that the image is exported as an opaque 24-bit PNG or JPEG with zero transparent pixels.
