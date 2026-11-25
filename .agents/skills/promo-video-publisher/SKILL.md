---
name: promo-video-publisher
description: Create English promotional videos with suitable licensed background music, covers, captions and timed explanations from real product evidence or existing recordings, and publish and verify them when authorized. Use for apps, websites, desktop software, developer tools and services, including focused edits and existing-post updates.
---

# Promotional Video Publisher

Turn a real user problem into a visible product result and a clear next action. Support mobile and desktop recordings, existing videos, screenshots and supplied media without tying the workflow to a particular product, device or platform.

## Scope and language

Infer the product, audience, market, material, target accounts, primary action and task mode from the conversation and available evidence. Ask only for missing information that affects execution. Continue the same campaign using its saved state and existing authorization; for a new campaign, verify the target and scope instead of carrying over unrelated accounts or permissions.

Write promotional copy, on-screen text and reusable artifacts in clear, natural English by default. Communicate with the user in their language. An explicit language request overrides the default. Preserve official product names and UI terms; adapt phrasing to the target audience without unsupported claims.

| Mode | Required work |
| --- | --- |
| Analyze | Inspect evidence and provide a scoped strategy; no account mutations. |
| Create | Produce the requested assets and verify them locally. |
| Edit | Change the requested cover, text, subtitles or other asset; inspect only relevant dependencies. |
| Draft | Prepare the specified platform draft; do not publish it publicly. |
| Publish/update | Prepare or reuse verified assets, execute authorized account actions, then read back results. |
| Review results | Analyze available measurements and limitations; monitoring and experiments require their own scope. |

For a focused edit, do not automatically restart keyword research, create more videos, change other platforms or publish. For existing recordings, preserve useful material and do not require rerecording.

## Authorization

Use authorization already established in the current conversation. Preserve explicit platform exclusions on resumption; do not log in, test uploads or cross-post to an excluded platform. Publishing does not itself authorize paid promotion, private messages, profile changes, account switching, deleting/hiding posts or replacement uploads. Prepare a concrete proposed result before requesting any genuinely missing authorization. A request to edit this skill does not authorize online changes.

Choose a usable destination route for each platform, content type and account before preparing copy. Lead with the official product name and verified developer/publisher name, followed by the real destination URL where appropriate. An author comment/reply is optional unless requested, needed for credits or useful to the chosen route; preserve an established request for both placements without asking again. Execute only authorized account actions. Detailed identity, route selection and verification belong in [publishing](references/publishing.md).

## Workflow

1. Establish the task mode and evidence. Confirm official product and developer/publisher identity, supported systems, destination and the installed/demo version when relevant. Distinguish visible entry points from successfully demonstrated outcomes.
2. For strategy or full production, read [strategy](references/strategy.md). Choose one main user benefit and action. For a narrow edit, reuse still-applicable campaign facts.
3. For recordings, editing, text overlays or exports, read [production](references/production.md). New promotional videos default to suitable background music and concise English explanations at meaningful moments. Read [music](references/music.md) to select, license, mix and verify the actual track. Before applying music to multiple videos, record each video's observed content, pace and track/excerpt rationale; do not batch-copy a song or volume setting without checking its fit. Preserve an explicit silent preference or purposeful source audio. The user need not record narration. AI narration is optional and requires a user choice.
4. For drafts, publishing, existing-post edits or delivery verification, read [publishing](references/publishing.md) and only the relevant platform reference linked there. Confirm the actual upload version, copy, cover, text and audio before submitting. Apply an established music preference across requested platforms; record unsupported existing-post edits without silently replacing uploads.
5. Record evidence and remaining work using the [status template](assets/publishing-status-template.md). Resume from confirmed state; a submission or thumbnail alone is not proof of public availability.

## Delivery

Store generated campaign resources under the target project's `docs/promo/<campaign>/`, or that path in the current workspace for projectless work, unless the user specifies another location. Keep package-relative references. Preserve user originals; archive necessary temporary outputs, not unrelated files. Create only the directories and documents the requested mode needs.

Use the [timeline template](assets/timeline-template.json) for editing that needs timed text or cuts. The production reference defines its fields and export semantics. Preserve a clean master and the actual delivery version; distinguish speech captions, explanatory overlays and optional narration.

Report what was produced or saved, actual verification, links or artifact paths, and specific unfinished items. Separate publication, cover, text treatment, music and audible playback, author comment, destination reachability and measurement integration. Never claim exposure, conversion or public visibility from insufficient evidence.
