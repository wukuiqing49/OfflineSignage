# TABLET_SCREENSHOT_05_PROMPT

- Source Brief: `SCREENSHOT_BRIEF.md`
- Asset Type: Tablet Screenshot 05
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

A commercial render of image and video playlist items advancing in order on a compatible Android display. Include a simple loop indicator, but do not depict scheduling, failover, or an invented app interface. Leave clear space for headline text.
```

---

## 🎨 Midjourney v6.1 Prompt (Raw Photo Style)

```text
/imagine prompt: A commercial render of image and video playlist items advancing in order on a compatible Android display. Include a simple loop indicator, but do not depict scheduling, failover, or an invented app interface. Leave clear space for headline text. --ar 16:10 --v 6.1 --style raw
```

---

## 📐 Figma / PS Copy Overlay Card (Ready to Copy-Paste)
- **Main Headline (EN)**: `Playlists & Automatic Looping` (Font: Inter / Roboto Bold, ~64-72pt)
- **Supporting Text (EN)**: `Play image and video items in sequence.` (Font: Inter / Roboto Regular, ~32-36pt)
- **Figma Layout Tip**: Paste the AI background image into Figma, create a text box at the top clean margin, and align text centrally with 48px padding.

---

## 💡 DALL-E 3 & Flux Optimization Note
- Generate at 1792x1024 (16:9 Landscape), then resize/fit to 2560x1600 px.
- **Compliance Reminder**: Before uploading to Google Play Console, verify that the image is exported as an opaque 24-bit PNG or JPEG with zero transparent pixels.
