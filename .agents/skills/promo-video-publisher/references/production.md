# Real recordings and media production

## Source and capability check

Identify supplied media, duration, frame size/orientation, audio tracks, existing text and the intended delivery. Inspect representative frames and every action needed for claims and timing; sample more densely around transitions. Audio-track presence does not establish that narration exists. If playback, transcription or rendering is unavailable, state the exact gap and continue independent work without inventing observations.

Use tools available in the environment; do not require one operating system or media service. A reliable local media pipeline may be used when available. AI image generation is optional for appropriate artwork, never a substitute for real product UI in a requested recording. Preserve user originals and use copies for edits.

Record only the requested real workflow. Distinguish failed operations, visible entry points and completed outcomes; edits must not turn failure into success or pretend separate sessions were one continuous transaction. Protect private evidence, notifications, credentials and signatures; use demo data where appropriate. Do not modify product code, clear data or upgrade the app simply to obtain footage.

## Device interaction

Honor the requested recording or publishing device. Discover connected devices and identify the target; for Android, specify the serial on every ADB operation when multiple devices are present. Do not assume other device families support ADB.

Use current UI structure or a fresh screenshot for actions. After transitions, wait for the current screen and observe again. A failed UI dump does not validate old coordinates. Check field focus and use reliable text entry; read back full text, including punctuation and line breaks. IME composition or autocorrection can alter English, so verify the resulting text rather than assuming a successful command inserted it correctly.

Record temporary orientation, language and input-method changes and restore them after use when appropriate. End only media processes started for this task.

## Text and audio

- Speech captions: transcribe the actual narration, translate when required and check names, numbers and claims. Align captions to speech.
- Explanatory overlays: for narration-free recordings, add concise English explanations at meaningful actions or results. The user does not need to speak or record English. These are authored explanations, not a speech transcript.
- AI narration: only when the user chooses it. Use an available authorized voice service; do not impersonate the user. Align captions to the generated audio after checking pronunciation and timing. If the capability is unavailable, deliver the script and clearly mark narration unfinished.
- Existing text: reuse accurate, readable material. Do not add duplicate overlays. Respect an explicit request for raw footage or no text and record that choice.
- Background music: default to a suitable licensed track for new promotional videos. Use [music](music.md) for selection, intended rights, source offsets, mixing, listening and platform verification. Narration-free does not mean music-free; honor an explicit silent preference and preserve meaningful source audio.

Explain meaningful steps rather than every tap. Use short sentences, normally one or two lines, with enough dwell time to read. The number of cards depends on the content; do not impose a fixed count. Do not write claims from a product description and assign them arbitrarily across footage.

## One editing timeline

Copy [the timeline template](../assets/timeline-template.json) when cuts or timed text are needed. It is an editable example, not an executable renderer or proof that the sample timing is correct. Replace example material with observed sources.

All times are seconds. Source times refer to the original media; output times refer to the finished video. Each segment names a source and a source interval, output interval, playback rate and observed action. A text cue has a stable ID, kind, output interval, language, text, associated segment and style. Use the same final timeline for captions, explanatory overlays and exports.

For simple cuts, output duration equals source duration divided by playback rate. If footage is trimmed, reordered or retimed, update output intervals and cue associations before export. Keep fades, freezes or other nontrivial effects explicit in the chosen editor's configuration rather than silently violating the timing model.

Validate positive durations, source bounds, valid references, chronological placement and cue intervals within the final duration and their associated footage. Intentional overlapping layers must be explicit. Check timing against actual playback; valid numbers alone do not establish synchronization.

Export speech captions as SRT/VTT when useful. Explanatory text may also have a timed text export, clearly labeled as explanation. SRT alone does not preserve layout, fonts or background panels: retain the style configuration and reproducible renderer/editor settings for burned-in overlays.

## Layout, editing and export

Keep text readable on a phone and clear of platform controls, product buttons, evidence annotations and report results. Establish safe areas from the target layout and current platform preview, not one universal pixel margin. If space is insufficient, scale the complete recording into a layout with a text area, then verify that the product UI remains legible. Do not crop away essential operations.

Shorten waiting or repetitive input where useful, but preserve context and success evidence. Do not lengthen the opening merely to display a cover. Where a platform only accepts a cover frame, prepare and verify a suitable frame if needed, retaining the original.

Keep a clean edited master and the intended delivery version, plus a music-free version when using platform-specific music. Use a verified burned-in version when the chosen client cannot import captions. Check automatic captions for errors or duplication. Follow the music reference for audio rights and actual listening; record any explicit silent choice or unfinished music placement.

Check current destination requirements when necessary: container, codecs, dimensions, aspect ratio, frame rate, duration and audio. Do not treat a prior project's export settings as universal platform limits.

## Download conversion assets

When store presentation or QR conversion is requested, follow [publishing](publishing.md#qr-and-store-presentation) for identity, official badge selection and final-video QR decoding. Preserve the original recording/AI master and create a distinct delivery revision; do not obscure essential product evidence with conversion overlays.

## Verification and archive

Check media metadata, decode the entire export, and visually inspect every text cue plus the opening, key results, transitions and ending. Check spelling, contrast, timing, clipping, duplicate captions and CTA accuracy. A successful encode is not visual acceptance.

Save sources or source references, timeline, text exports, needed render configuration, clean master, delivery versions and evidence in the campaign directory. Use subfolders such as raw, videos, covers, copy, subtitles, sources, scripts, verification and published only as needed. Name delivery files clearly enough to distinguish platform and revision. Do not automatically commit large media or configure Git LFS; follow the repository and requested delivery scope.
