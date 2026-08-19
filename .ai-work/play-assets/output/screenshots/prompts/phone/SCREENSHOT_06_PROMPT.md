# PHONE_SCREENSHOT_06_PROMPT

- Source Brief: `SCREENSHOT_BRIEF.md`
- Asset Type: Phone Screenshot 06
- Generation: Derived from the validated project brief

---

## 🎯 Target Specifications (Google Play Official Requirements)
- **Exact Target Canvas**: `1080x1920 px (or 1080x2400 px)`
- **Aspect Ratio**: `9:16 portrait`
- **File Format**: `24-bit PNG or JPEG` (Opaque background, strictly **NO ALPHA TRANSPARENCY**)
- **Color Space**: `sRGB` (Recommended)

---

## 🤖 Pure Visual 3D Background Prompt (Gemini / Imagen 3 / ChatGPT)

```text
Device Type: Phone. Locale: en-US. Canvas: 1080x1920. Output: opaque PNG or JPEG.

A commercial 3D render of a modern multi-screen restaurant and store installation. Three ultra-thin screens hang in perfect alignment, playing synchronized digital menu boards and promotional videos in harmony, controlled by BRAND-ICON-01 via local Wi-Fi. Clean uncluttered upper section for overlay text. Expansive commercial interior photography and 3D architectural render, cinematic lighting, 8k resolution.
```

---

## 🎨 Midjourney v6.1 Prompt (Raw Photo Style)

```text
/imagine prompt: A commercial 3D render of a modern multi-screen restaurant and store installation. Three ultra-thin screens hang in perfect alignment, playing synchronized digital menu boards and promotional videos in harmony, controlled by BRAND-ICON-01 via local Wi-Fi. Clean uncluttered upper section for overlay text. Expansive commercial interior photography and 3D architectural render, cinematic lighting, 8k resolution. --ar 9:16 --v 6.1 --style raw
```

---

## 📐 Figma / PS Copy Overlay Card (Ready to Copy-Paste)
- **Main Headline (EN)**: `Multi-Screen Fleet Synchronization` (Font: Inter / Roboto Bold, ~64-72pt)
- **Supporting Text (EN)**: `Broadcast promotions or control individual displays over Wi-Fi.` (Font: Inter / Roboto Regular, ~32-36pt)
- **Figma Layout Tip**: Paste the AI background image into Figma, create a text box at the top clean margin, and align text centrally with 48px padding.

---

## 💡 DALL-E 3 & Flux Optimization Note
- Generate at 1024x1792 (9:16 Portrait), then fit to 1080x1920 px.
- **Compliance Reminder**: Before uploading to Google Play Console, verify that the image is exported as an opaque 24-bit PNG or JPEG with zero transparent pixels.
