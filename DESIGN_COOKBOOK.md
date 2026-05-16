# Micronaut Platform Docs Design Cookbook

This file is the design source of truth for the generated platform documentation UI. Update it whenever a change affects layout, typography, colors, cards, sidebar behavior, search, reference sheets, code snippets, admonitions, or any other visible design detail.

## Working Rule

- Every design change must include a matching note in this cookbook.
- Keep rules concrete: name the component, the intended behavior, and the CSS/template/rendering owner.
- Prefer updating existing rules over adding contradictory notes.
- Verify design changes with a focused render and a browser screenshot for at least one desktop viewport and one mobile viewport when layout can be affected.

## Design Direction

The site should feel like a compact developer documentation application: dense, readable, predictable, and quiet. It should not feel like a marketing landing page. The closest visual reference is shadcn-style application UI: simple surfaces, 8px radius cards, restrained borders, compact controls, strong focus states for controls, and minimal decoration.

## Style Ownership

- Main page styling belongs in `buildSrc/src/main/resources/io/micronaut/docs/assets/site.css`.
- Application shell markup belongs in Handlebars templates under `buildSrc/src/main/resources/io/micronaut/docs/templates`.
- Rendered guide markup comes from `ModernGuideRenderer` and `PlatformGuideHtmlRenderer` and must be styled through `.guide-document` selectors.
- Guide structure comes from `GuideToc`, the platform-owned `toc.yml` source index. Use it for section numbers, sidebar entries, page-index entries, search section entries, and renderer traversal instead of parsing generated HTML or depending on old Micronaut build internal TOC classes.
- Macro-specific guide HTML for snippets, dependencies, configuration samples, admonitions, and callout markers should be emitted directly by `PlatformGuideHtmlRenderer` through the docs plugin `Renderer` API. Do not style around a second pass that parses already-rendered HTML.
- Embedded API and configuration reference pages are styled only through iframe-injected CSS loaded from `buildSrc/src/main/resources/io/micronaut/docs/assets/embedded-reference.css` by `GeneratePlatformDocsTask`.
- Generated `index.html` must import only `platform-assets/site.css` for the platform shell. Do not import old guide CSS, Javadoc CSS, or configuration-reference CSS into the main page.

## Tokens And Palette

- Extend theme tokens before introducing new component colors.
- Light mode uses a neutral gray page background with near-white panels: `--bg #f5f5f5`, `--surface #fcfcfc`, `--card-surface #ffffff`, `--surface-strong #eeeeee`, `--text #172026`, `--muted #5d6b78`, `--line #e0e0e0`, `--accent #00a676`, `--link #1565c0`.
- Dark mode separates chrome from content: sidebar and top bar are black, while content surfaces use neutral grays. Keep dark reading surfaces gray rather than pure black.
- Code and dependency snippets use the same neutral code-frame palette, not blue-gray, IDE-themed, or dependency-specific frames. Light mode uses white or near-white snippet panels over the pale page; dark mode uses deeper neutral gray panels over the gray article surface.
- Admonitions should be calm and low saturation unless the content is genuinely dangerous.

## Overview Page

- The overview description is a compact subtitle, not a hero. It stays full width, readable, and short enough not to consume the first mobile screen.
- Categories are stacked sections. The category icon, title, and description must appear above that category's project cards.
- Do not use a desktop layout where category text becomes a left rail and cards appear to the right.
- Project cards should remain close to square on desktop, with a minimum grid track around `320px` so sparse categories do not render as tiny boxes. Keep name and short description in the header, concise long description in the body, and footer actions plus version metadata at the bottom.
- Card text must not be ellipsized. If content is too long, shorten the description instead of hiding text.
- Cards may use subtle elevation and borders, but no nested card structures.
- Category counts stay hidden.

## Sidebar

- The sidebar is the primary project inventory. Project rows are first-level expandable items.
- Categories group projects, with `Most Popular` first.
- The left sidebar shows only project rows and top-level guide sections. Numbered subsections such as `1.1`, `1.2`, and `2.3.1` belong in the right-side page index instead of the left menu.
- Project and TOC labels must remain readable. Avoid truncation that hides meaning; allow wrapping where needed.
- Submenu hover and active highlights should leave a visible right gutter in the sidebar. Do not let nested TOC highlights run flush to the right edge.
- Keep the expanded desktop sidebar compact, around 248px, so the article retains priority on laptop-sized screens. The mobile sidebar sheet should use the same approximate width unless a viewport constraint forces it narrower.
- When the desktop sidebar is collapsed, the article content may use a slightly wider reading measure than the expanded-sidebar state so the detail area visibly benefits from the freed space.
- Selecting a project should bring the project title and document actions into view without auto-scrolling the sidebar inventory. On mobile, outside clicks after a project selection should close the sidebar.

## Page Index

- Wide project documentation views include a right-side `In this section` rail. It is generated by `site.js` from the same `toc.yml` model that powers the sidebar after the lazy document fragment has loaded.
- The page index is a compact navigation aid, not a card. Keep it transparent, sticky below the top bar, and visually secondary to the article.
- It lists the current top-level section's numbered subsections, keeps their TOC numbers visible, and keeps the active heading synchronized with the scroll spy.
- When the active top-level section has no numbered subsections, the rail falls back to the project's top-level sections so deep links still have local orientation without moving the sidebar.
- Hide the page index on the overview, mobile/tablet widths, and while the reference sheet is open so it never competes with primary navigation or sheet content.

## Guide Actions

- Section edit links are icon-only actions. They stay hidden until the heading wrapper is hovered or focused, and the text belongs in `title` and `aria-label` rather than visible document copy.

## Top Bar

- The top bar is sticky and task-oriented: sidebar toggle, breadcrumb/version context, search, and theme toggle.
- On project pages, the breadcrumb includes the active project name for deep-link orientation. This is preferred over automatically scrolling the sidebar to the active project.
- Keep version metadata small. It should not compete with search. Project detail metadata should not duplicate the project version when the surrounding platform context already provides version orientation.
- Icon buttons need accessible labels and stable square hit areas.
- The light/dark theme toggle matches the search trigger height, stays transparent in both themes, and uses a darker monochrome icon so it reads as a utility control rather than a filled badge.

## Search

- Search is the primary command surface and should look like a compact shadcn-style command menu: a top-bar trigger opens a wide dialog centered horizontally in the page, placed around the upper-middle of the viewport, the page behind it is blurred, the real search input sits inside the dialog, scope tabs stay quiet, keyboard focus is visible, and there is no marketing copy.
- The placeholder names the actual searchable inventory: projects, API classes, configuration properties, and guide docs.
- The search trigger stays compact in the top bar, but the opened dialog is a larger command surface. Dialog input text, scope controls, result titles, details, icons, empty states, and prompt text should be scaled for the wider panel rather than inheriting compact topbar sizing.
- The open empty state should be useful, not blank. Show a short command heading and, in the All scope only, quiet `Class` and `Property` hint badges. Do not show hard-coded class names or property keys as suggestions.
- Results use a stable hierarchy: icon, optional kind badge, optional project/context, title, and detail. Use kind badges only for Javadoc classes and configuration properties in the All scope.
- Project results should be the simplest and strongest result rows: show the module name as the larger title, a short `Open documentation` detail, and no redundant `Project` label. Project-level documentation links use the project slug hash, such as `#data`, and open at the project title and metadata actions rather than the first guide section.
- Repository results should read as external source links: use the project name as the title, `GitHub repository` as plain context, and the repository path plus version as the detail.
- API class results come from generated Javadoc `type-search-index.js` files and open in the reference sheet so users keep platform-docs context.
- Keep API class entries out of the prefix-expanded term map; scan their compact title/package text in JavaScript so class search stays flexible without making the generated index unnecessarily large.
- Keep the search index static and generated at build time. Do not add client-side crawlers or runtime syntax/search libraries unless the generated index becomes too large for fast interaction.

## Guide Typography

- Scope guide body rules to `.guide-document`.
- Paragraphs use readable documentation rhythm: about `1rem` font size, `1.6` line height, normal weight, left aligned, and no forced justification.
- Headings should orient the reader without dominating the page. Desktop guide headings are intentionally tighter than the old generated docs scale.
- Preserve hierarchy: `h1` and `h2` are primary anchors, while `h3` through `h6` step down clearly.
- Keep anchor links hidden until hover/focus.
- Lists, definition lists, tables, examples, sidebars, quotes, and callouts should feel like article content, not navigation.
- Quote blocks use a quiet callout treatment: neutral panel, compact quote mark, normal document text, and readable dark-mode colors. Do not use oversized italic pull-quote styling for short explanatory quotes.
- Standalone generated hash anchors before configuration tables are hidden visually. They are structural anchors, not visible content.

## Code Snippets

- Single-language snippets show code plus copy action only. Do not add language badges, bottom-right labels, or decorative headers.
- Multi-language snippets use tabs as controls for equivalent variants.
- Language snippet samples arrive as resolved raw source from the docs plugin DTOs. The platform renderer owns source-to-HTML escaping and callout marker markup, while Shiki owns static token colors after rendering.
- Real snippet titles sit outside the code frame.
- Unknown `[source]` snippets remain plaintext and must not be labeled Bash.
- Shiki is static build-time highlighting only. Do not add runtime syntax highlighters.
- Code frames separate from the guide background with a neutral frame surface plus a quiet 1px outer border. The light border is visible but soft; the dark border should be nearly invisible and subordinate to the darker frame background. Snippet cards do not use shadows.
- Code, properties, and dependency snippets share the same shadcn-style card structure: `.docs-snippet-card` maps to Card, `.docs-snippet-card-header` to CardHeader, `.docs-snippet-card-title` to CardTitle, `.docs-snippet-card-action` to CardAction, `.docs-snippet-card-content` to CardContent, and `.docs-snippet-card-footer` to CardFooter. Keep these `docs-snippet-card-*` classes and `--snippet-card-*` tokens as the styling owners; treat `docs-code-*` classes as compatibility and behavior hooks for copying, tab hydration, and static highlighting.
- Dependency snippets and properties snippets share the same background, border, separator, action, and footer token set as normal code snippets in both light and dark mode. Gradle, Maven, and Properties tabs should not introduce a second color system or separate card palette.
- Tabs and code frames use the same neutral family. Keep a single split line between the tab row and code body: visible in light mode, almost invisible in dark mode. Tab labels should use compact system-sans typography around `12px / 16px`, regular weight, with only a modest selected-weight increase, and inactive labels must still meet readable contrast in both themes.
- Language icons in code tabs inherit the tab text color. Keep them gray and monochrome so tabs read as quiet controls, not a strip of brand badges.
- Code language icons are generated from SVG resources. Brand icons live under `assets/icons/brands`; project-owned generic format icons live under `assets/icons/languages`. Do not add fallback SVG path data to JavaScript templates.
- The Java language tab uses a filled Java-cup inspired icon with no wordmark instead of the OpenJDK mark. Keep the shape recognizable but subdued through the same gray tab color as the other languages.
- Filled brand glyphs such as Kotlin can be optically smaller than line icons so their visual weight matches Java and Properties in the tab row.
- The Properties language tab uses a small sliders-style line icon. It should suggest key/value configuration without looking like a document badge or a separate status color.
- Code titles stay outside the frame and share the same compact caption treatment as table captions, configuration-property table titles, and image titles: sans-serif, muted but readable, no border, no filled title bar. Keep the title close to the code frame it names, including multi-language samples where hidden selector markup may sit between the title and block.
- Code callout lists after snippets are snippet footers. They use `.docs-snippet-card-footer`, sit on a lighter quiet footer surface than the code body, keep equal vertical padding in both themes, center each marker/text row, and use subdued numbered markers rather than a separate alert or table treatment.

## Admonitions

- Admonitions stay in document flow and keep content left aligned.
- Renderer-owned admonitions use `.docs-admonition-card`, `.docs-admonition-card-layout`, `.docs-admonition-card-icon`, and `.docs-admonition-card-content` as component hooks while preserving the docs-engine table structure for compatibility.
- Use one calm icon, neutral background, and subtle accent color. Note, important, warning, and caution may have different icon accents, but the container stays low saturation.
- Light-mode tips use the most neutral callout treatment: a near-transparent dark tint, soft neutral 1px border, larger radius, compact padding, and smaller readable text. They should read like quiet inline guidance, not a green status panel.
- Avoid saturated status panels and colorful backgrounds unless severity demands it.

## Tables

- Tables use a rounded neutral frame with a quiet header background and subtle row striping.
- Table captions are document captions, not old Asciidoctor labels. They share the same compact caption treatment as code titles and image titles: sans-serif, non-italic, muted, left aligned, and wrapped normally.
- Configuration property names in table cells keep a small inline-code background so long keys remain scannable.

## Images

- Image titles belong to the same document caption group as code titles and table captions. Keep them compact, sans-serif, non-italic, muted, left aligned, and close to the image they describe.

## Reference Sheet

- The sheet should occupy about 60 percent of desktop width and blur/de-emphasize the page behind it.
- It must offer expand, open-in-new-tab, and close actions.
- API and configuration pages keep the platform context inside an iframe.
- Injected iframe styling should normalize font, headings, tables, inline code, pre blocks, links, and targeted rows without importing legacy CSS into the shell.

## Mobile Rules

- Mobile top bar prioritizes search and compact controls.
- Overview cards stack full width.
- Sidebar behaves like a sheet and should not obscure navigation after selecting a project.
- When the mobile sidebar is open, the sidebar toggle remains available on the overlay edge so users can close the sheet without accidentally selecting a project.
- Avoid first-screen crowding. The overview subtitle should remain concise.

## Verification Checklist

- Run a focused render, normally:

  ```bash
  ./gradlew -q -PplatformDocs.projectSlugs=core,serde,data,mcp,oracle-cloud,sourcegen renderPlatformDocs
  ```

- Check generated CSS if the browser seems stale:

  ```bash
  rg -n "project-category|guide-document h1|reference" build/site/platform-assets/site.css
  ```

- Use a cache-busting URL when visually checking local output, for example `index.html?v=<change>#platform`.
- Verify light and dark mode when changing colors.
- Verify mobile around `390px` width when changing layout, sidebar, top bar, overview, or cards.

## Current Decisions

- Category sections use the previous stacked structure: header and description first, project cards below.
- Desktop guide headings were reduced so generated docs do not overpower the reading view.
- Programmatic focus on the overview section should not draw a full-page browser outline.
- Embedded configuration reference pages use `embedded-reference.css` for injected platform-style typography and tables inside the reference sheet.
