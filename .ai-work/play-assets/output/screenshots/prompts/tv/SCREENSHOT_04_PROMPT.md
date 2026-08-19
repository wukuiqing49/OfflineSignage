# TV_SCREENSHOT_04_PROMPT

- Source Brief: `SCREENSHOT_BRIEF.md`
- Asset Type: TV Screenshot 04
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

A commercial 3D render of a free-standing commercial digital kiosk and wall display playing smoothly 24/7 in an architectural space. A subtle, elegant 3D holographic badge with an offline shield icon and "100% Local Storage" floats beside the screen with BRAND-ICON-01. Clean uncluttered upper section for overlay text. Sophisticated 3D render, sleek emerald green (#1A8754) highlights, confident corporate and retail setting, photorealistic.
```

---

## 🎨 Midjourney v6.1 Prompt (Raw Photo Style)

```text
/imagine prompt: A commercial 3D render of a free-standing commercial digital kiosk and wall display playing smoothly 24/7 in an architectural space. A subtle, elegant 3D holographic badge with an offline shield icon and "100% Local Storage" floats beside the screen with BRAND-ICON-01. Clean uncluttered upper section for overlay text. Sophisticated 3D render, sleek emerald green (#1A8754) highlights, confident corporate and retail setting, photorealistic. --ar 16:9 --v 6.1 --style raw
```

---

## 📐 Figma / PS Copy Overlay Card (Ready to Copy-Paste)
- **Main Headline (EN)**: `100% Offline · Zero Cloud Subscriptions` (Font: Inter / Roboto Bold, ~64-72pt)
- **Supporting Text (EN)**: `All media stored locally. Never goes black when internet drops.` (Font: Inter / Roboto Regular, ~32-36pt)
- **Figma Layout Tip**: Paste the AI background image into Figma, create a text box at the top clean margin, and align text centrally with 48px padding.

---

## 💡 DALL-E 3 & Flux Optimization Note
- Generate at 1792x1024 (16:9 Landscape), then fit to 1920x1080 px.
- **Compliance Reminder**: Before uploading to Google Play Console, verify that the image is exported as an opaque 24-bit PNG or JPEG with zero transparent pixels.
