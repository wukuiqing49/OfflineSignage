# Local Signage Internationalization Specification

## Source Language And Fallback

English is the source language for product, billing, help, legal and Web-console copy. Simplified Chinese (`zh-CN`) is the first fully published translation. The first selectable fallback locales are `ja-JP`, `ko-KR`, `es`, `de`, `fr`, `pt-BR`, and `ru`. Any locale without a reviewed translation falls back to English.

Do not present a selectable language until its complete product copy and required legal documents have been reviewed.

## Android Application

- User-facing copy belongs in `feature/feature_app/src/main/res/values/strings.xml` with matching locale-qualified `values-*` files.
- In-app legal documents belong in `raw/`; matching locale files use Android resource qualifiers such as `raw-zh-rCN/`.
- Help pages use string resources. Do not embed help or legal copy in Kotlin.
- Product names, plan names, trial status, subscription terms and cancellation language must retain the same meaning in every published locale.

## Web Console

- The console uses BCP-47 locale identifiers. Current published locales are `en` and `zh-CN`.
- The browser locale selects a default, while the language picker persists an explicit override in `localStorage`.
- UI text must call `tr(key)` and dates must use the selected locale. API values, device names and error codes remain data and are not translated by the browser.
- New locales add a complete message pack and translated HTML templates. The resolver must fall back to English for a missing key.

## Legal And Store Content

- Privacy, terms, subscription and data-deletion documents are separate files per published locale.
- A legal translation requires a reviewed effective date and a version identifier before release.
- Google Play listing text, screenshots and subscription descriptions must match the language and commercial claims of the application.

## Release Checks

1. Check Android string keys, placeholders and raw resource encoding.
2. Check each Web message key and language-picker value.
3. Test long text, font scale, RTL only after an RTL locale is published, and date formatting in each locale.
4. Verify every published legal-document link opens the locale-appropriate document or the declared English fallback.
