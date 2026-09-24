# PHONE_SCREENSHOT_01_PROMPT

- Source Brief: `SCREENSHOT_BRIEF.md`
- Asset Type: Phone Screenshot 01
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

A commercial render of a compatible Android display playing a local image/video menu playlist in a modern cafe. Leave clean space for headline text and use restrained green accents. Do not imply compatibility with a specific unverified TV or box model.
```

---

## 🎨 Midjourney v6.1 Prompt (Raw Photo Style)

```text
/imagine prompt: A commercial render of a compatible Android display playing a local image/video menu playlist in a modern cafe. Leave clean space for headline text and use restrained green accents. Do not imply compatibility with a specific unverified TV or box model. --ar 9:16 --v 6.1 --style raw
```

---

## 📐 Figma / PS Copy Overlay Card (Ready to Copy-Paste)
- **Main Headline (EN)**: `Digital Signage, Managed Locally` (Font: Inter / Roboto Bold, ~64-72pt)
- **Supporting Text (EN)**: `Play local media and control it from your browser.` (Font: Inter / Roboto Regular, ~32-36pt)
- **Figma Layout Tip**: Paste the AI background image into Figma, create a text box at the top clean margin, and align text centrally with 48px padding.

---

## 💡 DALL-E 3 & Flux Optimization Note
- Generate at 1024x1792 (9:16 Portrait), then fit to 1080x1920 px.
- **Compliance Reminder**: Before uploading to Google Play Console, verify that the image is exported as an opaque 24-bit PNG or JPEG with zero transparent pixels.
