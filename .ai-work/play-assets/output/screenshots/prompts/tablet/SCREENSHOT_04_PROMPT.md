# TABLET_SCREENSHOT_04_PROMPT

- Source Brief: `SCREENSHOT_BRIEF.md`
- Asset Type: Tablet Screenshot 04
- Generation: Derived from the validated project brief

---

## 🎯 Target Specifications (Google Play Official Requirements)
- **Exact Target Canvas**: `2560x1600 px (or 1920x1200 px)`
- **Aspect Ratio**: `16:10 landscape`
- **File Format**: `24-bit PNG or JPEG` (Opaque background, strictly **NO ALPHA TRANSPARENCY**)
- **Color Space**: `sRGB` (Recommended)

---

## 🤖 Pure Visual 3D Background Prompt (Gemini / Imagen 3 / ChatGPT)

```text
Device Type: Tablet. Locale: en-US. Canvas: 2560x1600. Output: opaque PNG or JPEG.

A product render of a compatible Android display playing a local image/video playlist in a retail space. Include a small neutral local-file cue, but no uptime guarantee or offline shield. Leave clean space for text; use restrained emerald accents and realistic device proportions.
```

---

## 🎨 Midjourney v6.1 Prompt (Raw Photo Style)

```text
/imagine prompt: A product render of a compatible Android display playing a local image/video playlist in a retail space. Include a small neutral local-file cue, but no uptime guarantee or offline shield. Leave clean space for text; use restrained emerald accents and realistic device proportions. --ar 16:10 --v 6.1 --style raw
```

---

## 📐 Figma / PS Copy Overlay Card (Ready to Copy-Paste)
- **Main Headline (EN)**: `Play Media Stored on Your Device` (Font: Inter / Roboto Bold, ~64-72pt)
- **Supporting Text (EN)**: `Remote web pages and live streams need a network connection.` (Font: Inter / Roboto Regular, ~32-36pt)
- **Figma Layout Tip**: Paste the AI background image into Figma, create a text box at the top clean margin, and align text centrally with 48px padding.

---

## 💡 DALL-E 3 & Flux Optimization Note
- Generate at 1792x1024 (16:9 Landscape), then resize/fit to 2560x1600 px.
- **Compliance Reminder**: Before uploading to Google Play Console, verify that the image is exported as an opaque 24-bit PNG or JPEG with zero transparent pixels.
