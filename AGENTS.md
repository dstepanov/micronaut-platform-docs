# Micronaut Platform Docs Agent Guide

This repository builds a single static Micronaut Platform documentation site from Micronaut project git submodules. It is not a normal Micronaut application or library module. The root project contains Gradle build logic, templates, static assets, generated metadata, and tests for the platform documentation site.

The site is generated from source repositories. Do not download or scrape the published documentation pages as the source of truth. Each project guide must come from the matching git submodule under `repos/`, aligned to the version declared by `repos/micronaut-platform`.

## Required Defaults

- Use Java 25 for documentation builds. `BuildGuideDocsTask` fails when `java.specification.version` is not `25`.
- Use the Gradle wrapper: `./gradlew`.
- Run non-test Gradle commands with `-q` unless you are diagnosing a failure that cannot be understood otherwise.
- Run Gradle test commands without `-q`; test output is needed.
- Prefer fast two-project runs while developing: `-PplatformDocs.projectSlugs=core,serde`.
- Do not run all 68 project docs locally unless the task explicitly needs a full build.
- Do not recursively check out every submodule in GitHub Actions. The workflow intentionally initializes only `micronaut-platform` during planning and only the selected guide shard during matrix jobs.
- Do not commit changes. Staging with `git add` is acceptable when requested, but commits are left to the user.
- Preserve unrelated local changes and untracked files. This repo often has generated or prototype files in the working tree.

## Repository Layout

```text
.github/workflows/platform-docs.yml
    GitHub Actions workflow for planning shards, building guide artifacts, rendering the final site, and publishing Pages.

buildSrc/
    Gradle plugin and task implementation for platform scanning, submodule sync, version alignment,
    guide building, static site rendering, verification, and browser-backed tests.

buildSrc/src/main/java/io/micronaut/docs/
    Main implementation package. Most behavior lives here rather than in root build scripts.

buildSrc/src/main/resources/io/micronaut/docs/
    Handlebars templates, CSS, JavaScript, Micronaut logos, icons, and bundled UI assets.

gradle/
    Version catalog, platform docs metadata properties, and vendored build-only jars.

gradle/build-plugin/
    Contains the Micronaut build plugin jar used by `buildSrc` for the docs engine and guide assets.
    It is referenced directly with `files(...)`; do not reintroduce `mavenLocal()` or a repo-local Maven layout.

repos/
    Git submodules. `repos/micronaut-platform` is the version source of truth.
    `repos/micronaut-*` entries are source repositories used to build guides and references.

build/site/
    Generated static website. This is disposable output.
```

## Key Generated And Curated Files

- `gradle/platform-doc-projects.properties`: generated manifest from `scanPlatformProjects`. It contains project identity, repository URL, branch, submodule path, platform version key, and published guide URL. It should not contain repository creation dates.
- `gradle/platform-doc-repositories.properties`: generated repository metadata cache. It stores values such as `repositoryCreatedAt` so scans do not need repeated GitHub API calls. Unknown dates use `9999-12-31T23:59:59Z`.
- `gradle/platform-doc-descriptions.properties`: curated short and long project descriptions for the overview cards. Keep descriptions concise and human-readable.
- `gradle/platform-doc-icons.properties`: curated icon mapping for sidebar and overview cards.
- `gradle/platform-doc-categories.properties`: curated category/grouping metadata. The first matching category wins; keep `Most Popular` first.
- `gradle/platform-doc-shards.properties`: preferred GitHub Actions guide-build shard plan.
- `gradle/build-plugin/micronaut-gradle-plugins-*.jar`: vendored Micronaut build plugin jar used directly by `buildSrc`; this keeps CI independent of a local publish from another checkout.
- `build/site/index.html`: generated single-page UI.
- `build/site/platform-assets/documents/*.html`: lazy-loaded project guide fragments.
- `build/site/platform-assets/documents/*.js`: script fallback for `file://` loading and HTTP fetch failures.
- `build/site/platform-assets/sidebar-menu.html`: generated lazy sidebar menu markup.
- `build/site/platform-assets/search-index.json`: generated static search index.
- `build/site/guide-assets/`: shared Micronaut guide theme assets copied from the docs plugin classpath.

## Main Gradle Tasks

- `syncPlatformSubmodule`: initializes `repos/micronaut-platform`.
- `scanPlatformProjects`: scans `repos/micronaut-platform/gradle/libs.versions.toml`, discovers docs projects, excludes `crac` and `guides`, writes `platform-doc-projects.properties`, and refreshes repository metadata cache.
- `writePlatformDocsShardMatrix`: creates the GitHub Actions matrix from `platform-doc-shards.properties`.
- `syncPlatformProjectSubmodules`: adds missing project submodules and checks out their platform-managed tags.
- `syncPlatformGuideShardSubmodules`: initializes and aligns only the projects selected by `platformDocs.guideShardIndex` / `platformDocs.guideShardCount`.
- `alignPlatformVersions`: checks out every selected docs submodule at the platform-managed tag.
- `verifyPlatformAlignment`: verifies selected submodule HEADs match expected platform tags.
- `buildPlatformGuideDocs`: runs each selected submodule's `./gradlew -q -Dorg.gradle.vfs.watch=false docs`.
- `stagePlatformGuideDocsArtifact`: stages selected shard docs under `build/guide-docs-artifact`.
- `renderPlatformDocs`: renders the single-page site from existing built docs and source guides. It does not build submodule docs.
- `generatePlatformDocs`: builds selected guide docs and renders the site.
- `verifyPlatformDocs`: generates docs and verifies the generated site.
- `verifyRenderedPlatformDocs`: renders from existing/prebuilt docs and verifies the generated site.
- `check`: depends on alignment verification and generated docs verification.
- `cM`: Micronaut-style verification alias that depends on `check`.
- `spotlessCheck`: placeholder task; no formatting rules are configured yet.

## Common Commands

Fast local render for the two-project fixture:

```bash
./gradlew -q -PplatformDocs.projectSlugs=core,serde renderPlatformDocs
```

Fast verification used by the browser tests:

```bash
./gradlew :buildSrc:test
```

Compile build logic:

```bash
./gradlew -q :buildSrc:compileJava
```

Full two-project verification:

```bash
./gradlew -q -PplatformDocs.projectSlugs=core,serde cM
```

Spotless placeholder:

```bash
./gradlew -q spotlessCheck
```

Scan and update project metadata:

```bash
./gradlew -q scanPlatformProjects
```

Sync missing submodules and align tags:

```bash
./gradlew -q syncPlatformProjectSubmodules
./gradlew -q alignPlatformVersions
./gradlew -q verifyPlatformAlignment
```

Build a single GitHub Actions shard locally:

```bash
./gradlew -q -PplatformDocs.guideShardIndex=0 -PplatformDocs.guideShardCount=12 verifyPlatformAlignment stagePlatformGuideDocsArtifact
```

## Fast Development Workflow

Use this sequence for most UI, renderer, search, and test changes:

1. Edit `buildSrc` sources, templates, CSS, or JavaScript.
2. Compile build logic:

   ```bash
   ./gradlew -q :buildSrc:compileJava
   ```

3. Render only Core and Serialization:

   ```bash
   ./gradlew -q -PplatformDocs.projectSlugs=core,serde renderPlatformDocs
   ```

4. Run JUnit and Playwright tests:

   ```bash
   ./gradlew :buildSrc:test
   ```

5. Run the two-project verification alias:

   ```bash
   ./gradlew -q -PplatformDocs.projectSlugs=core,serde cM
   ```

6. Run Spotless placeholder:

   ```bash
   ./gradlew -q spotlessCheck
   ```

Only broaden to all projects when the change touches platform scanning, submodule selection, sharding, category assignment across every project, or full release output.

## Build Logic Ownership

Keep custom build behavior in `buildSrc`. Do not add custom task logic directly to root `build.gradle.kts` unless the change is only applying plugins or wiring existing convention behavior.

Important classes:

- `PlatformDocsPlugin`: registers all Gradle tasks and conventions.
- `ScanPlatformProjectsTask`: scans the platform catalog and writes the manifest/cache files.
- `RepositoryMetadataCache`: manages cached repository metadata and GitHub-created-at fallback behavior.
- `SyncPlatformProjectSubmodulesTask`: adds and aligns all missing docs submodules.
- `SyncPlatformGuideShardSubmodulesTask`: adds and aligns shard-selected docs submodules for CI.
- `AlignPlatformVersionsTask`: checks out submodules at expected version tags.
- `VerifyPlatformAlignmentTask`: validates selected submodule versions.
- `BuildGuideDocsTask`: invokes submodule docs builds with Java 25.
- `StageGuideDocsArtifactTask`: stages generated docs output and pre-rendered platform guide fragments for shard artifact upload.
- `GeneratePlatformDocsTask`: renders the site, copies guide assets, builds sidebar/menu/search/reference assets, and writes lazy document fragments.
- `ModernGuideRenderer`: renders guide HTML directly from `src/main/docs/guide/toc.yml` and `.adoc` sources with the Micronaut docs engine.
- `VerifyPlatformDocsTask`: checks generated site integrity.
- `PlatformDocsSearchTest`: browser-backed tests for search, lazy loading, file URL fallback, sidebar behavior, and code highlighting.

## Rendering Model

The renderer should use project sources, not downloaded docs:

- Each project guide source is `repos/<project>/src/main/docs/guide/toc.yml`.
- `ModernGuideRenderer` walks the YAML TOC and renders each referenced `.adoc` source through the Micronaut docs engine.
- The renderer sets Asciidoctor attributes from the submodule `gradle.properties` and platform version data.
- The renderer temporarily sets `user.dir` to the submodule directory because legacy snippet macros depend on it.
- Includes use the submodule root as the Asciidoctor base directory.
- The generated `build/docs` output is still required because API docs and configuration reference pages are copied from submodule builds.
- GitHub Actions shard jobs render each guide fragment from source while the selected submodules are initialized, then stage it as `repos/<project>/build/platform-docs/guide.html`.
- The final render job reads staged `build/platform-docs/guide.html` fragments when present, so it does not need to initialize all guide source submodules after downloading shard artifacts.
- Shared guide assets are copied from `grails-doc-files.jar` on the classpath, not from a downloaded documentation site.
- Local UI assets are copied from `buildSrc/src/main/resources/io/micronaut/docs/assets`.

The platform site is static:

- `index.html` contains the application shell, overview page, top bar, project card placeholders, document article wrappers, reference sheet, and global assets.
- Project guide content is written as separate lazy fragments under `platform-assets/documents`.
- The sidebar menu is generated as HTML and loaded separately.
- JavaScript data fallback files are generated so `file://` usage works despite browser CORS restrictions.
- Search uses a generated JSON index plus a generated script fallback.
- Reference pages are opened in a sheet-style iframe so the user keeps platform docs context.

## Styling Pipeline

The focused design cookbook lives in `DESIGN_COOKBOOK.md`. Any change that affects visible design must update that file in the same patch.

The platform docs page has two different sources of markup and one owner for visible styling:

- Guide body markup comes from the Micronaut docs engine. `ModernGuideRenderer` reads each project's `src/main/docs/guide/toc.yml`, renders the referenced `.adoc` files, and keeps the semantic HTML that the docs engine emits: headings, paragraphs, lists, tables, admonitions, source blocks, callouts, and multi-language selectors.
- The application shell markup comes from Handlebars templates in `buildSrc/src/main/resources/io/micronaut/docs/templates`. This includes the sidebar, top bar, overview cards, document metadata buttons, search UI, reference sheet, lazy document hosts, and project/category grouping.
- The platform page must style both sources with `buildSrc/src/main/resources/io/micronaut/docs/assets/site.css`. Generated `index.html` should import only `platform-assets/site.css` as a stylesheet.
- Do not import CSS from `guide-assets/css/*`, `guide-assets/style/*`, generated Javadocs, generated configuration reference pages, or the old Micronaut docs template into the main platform page. Those files can be copied as assets for iframes or compatibility scripts, but the platform shell must not depend on their styles.
- `VerifyPlatformDocsTask` enforces this by checking that generated `index.html` references `platform-assets/site.css` and does not contain `href="guide-assets/css/` or `href="guide-assets/style/`.

Style ownership:

- Global tokens live at the top of `site.css`: `--bg`, `--surface`, `--card-surface`, `--text`, `--muted`, `--line`, `--accent`, and related variables.
- The overview page and project detail documents should share `var(--bg)` so switching between the index and a project does not flash to a different background.
- Reusable chrome such as `.topbar`, `.sidebar`, `.document-meta`, `.reference-panel`, `.site-search`, and `.theme-toggle` uses the platform shell font variable and should not inherit old guide CSS.
- Rendered guide content is scoped under `.guide-document`. Add rules there when styling docs-engine output such as `.paragraph`, `.listingblock`, `.tableblock`, `.admonitionblock`, `.sect1`, `pre`, `code`, and generated heading levels.
- Overview cards use `.project-card*` classes. Sidebar entries use `.project-section`, `.toc-section`, `.toc-link`, and `.toc-*` classes. Do not style these through generic guide selectors.
- Reference iframe pages are the exception: `GeneratePlatformDocsTask.transformEmbeddedReferenceHtml` injects a small embedded style into copied API/configuration pages so the iframe hides old page chrome and highlights targeted configuration rows.

Code sample styling:

- `ModernGuideRenderer` rewrites Asciidoctor listing blocks into the platform structure: optional `.docs-code-title`, `.docs-code-block`, `.docs-code-copy`, `.docs-code-content`, and the original `<pre><code>`.
- Static syntax coloring is applied after rendering by the Gradle Node plugin task. `PlatformDocsPlugin` stages `buildSrc/src/main/resources/io/micronaut/docs/shiki`, runs `npm ci`, and executes `highlight.mjs` over `build/site/platform-assets/documents`.
- Shiki emits static token spans and inline CSS custom properties such as `--shiki-light` and `--shiki-dark`. Browser runtime syntax highlighting is intentionally not used.
- `site.css` applies those Shiki colors with `.guide-document .shiki` and `body.dark-mode .guide-document .shiki`; the theme switch changes which CSS variable is visible.
- Code block frames, copy buttons, tab bars, and language-tab icons are pure platform CSS in `site.css`.
- Language-tab icons are generated into `platform-assets/site.js` from vendored SVG resources. Prefer official or widely recognized open-source brand SVGs from `buildSrc/src/main/resources/io/micronaut/docs/assets/icons/brands` for Java/OpenJDK, Kotlin, Groovy, Gradle, Maven, YAML, JSON, TOML, GraphQL, Python, JavaScript, TypeScript, HTML, and shell. Use small project-owned stroke icons only for unbranded formats such as `properties`, `HOCON`, `SQL`, `XML`, `protobuf`, and plaintext.
- Single-language code samples must not show language hint badges or bottom-right labels. The only persistent action inside a standalone block is the copy button.
- Multi-language samples may show language tabs. The tab label/icon is UI for switching variants, not a hint badge on a single sample.
- The multi-language behavior still comes from the copied `guide-assets/js/multi-language-sample.js` compatibility script plus platform hydration in `site.js`; only the script is imported, not its old CSS.

## Practical UI Design Guide

Use this guide when changing rendered markup, templates, tests, or documentation fixtures that affect how the platform docs UI looks. The site should feel like a dense documentation application: clear hierarchy, compact controls, restrained cards, predictable navigation, and no marketing-page flourishes.

Rendered guide typography:

- Scope guide-body rules to `.guide-document`. Do not let shell selectors leak into rendered Asciidoctor content, and do not style shell chrome with broad `.guide-document` rules.
- Keep paragraphs as readable documentation text: 1rem body size, about 1.6 line height, normal weight, left aligned, and no forced justification. Use `overflow-wrap` only where content can genuinely overflow.
- Preserve heading hierarchy from the docs engine. `h1` and `h2` are the primary page/section anchors and are stronger than `h3` through `h6`; do not flatten all headings to the same size or color. Anchor links stay hidden until hover/focus so headings remain calm while still linkable.
- Keep section rhythm predictable. `.sect1 + .sect1` uses a subtle divider; block elements such as `.paragraph`, `.listingblock`, `.literalblock`, `.admonitionblock`, `.exampleblock`, `.sidebarblock`, `.ulist`, `.olist`, `.dlist`, `.qlist`, and `.hdlist` should keep consistent bottom spacing instead of ad hoc margins.
- Lists should read like prose, not navigation. Keep normal document line height, outside markers, modest indentation, and smaller spacing for nested lists. Definition terms should stay visibly stronger than descriptions.
- Inline code should be quiet and readable, with a small neutral background and monospace font. Do not make inline code look like buttons, badges, or syntax-highlighted blocks.
- Tables should remain documentation tables: full-width only when needed, horizontally scrollable if wide, legible cell padding, visible header/footer contrast, and row striping that works in both themes. Preserve Asciidoctor table classes such as `.tableblock`, `.grid-all`, `.frame-all`, `.halign-left`, and `.valign-top`.
- Admonitions must stay part of the document flow. Keep their icon/content table structure supported, content readable, and separators subtle. Do not convert admonitions into oversized cards or alert banners that dominate the guide.
- Example, sidebar, quote, callout, and contribution-button styles are compatibility affordances for generated Micronaut docs markup. Keep them scoped and functional even when modernizing the surrounding UI.

Platform shell components:

- The sidebar is the primary project navigation. It is built from `.sidebar`, `.sidebar-header`, `.sidebar-brand`, `.sidebar-content`, `.sidebar-group`, `.sidebar-category`, `.project-section`, `.toc-section`, `.toc-link`, `.toc-number`, and `.toc-title`.
- The top bar is the global command area. It contains `.sidebar-collapse`, `.topbar-title`, `.topbar-version`, `.site-search`, `.topbar-actions`, `.theme-toggle`, and the mobile `.sidebar-toggle`.
- Overview cards use `.project-category-list`, `.project-category`, `.project-card-grid`, `.project-card`, `.project-card-header`, `.project-card-icon`, `.project-card-short-description`, `.project-card-long-description`, `.project-card-footer`, `.project-card-links`, and `.project-card-version`.
- Document metadata controls live in `.document-meta` and `.document-meta-row`. Treat repository, version, and branch as compact badges; treat API and configuration reference controls as compact action buttons.
- Search uses `.site-search`, `.site-search-field`, `.site-search-results`, `.site-search-result`, `.site-search-result-kind`, and `.site-search-empty`. It is a lightweight command palette anchored in the top bar, not a full-page search route.
- The reference sheet uses `.reference-overlay`, `.reference-panel`, `.reference-header`, `.reference-heading`, `.reference-action`, and `.reference-frame`. It opens API and configuration pages in context, with expand, open-in-new-tab, and close controls.
- Loading and error states use `.guide-document-loading`, `.document-skeleton`, and `.guide-document-error`. Keep skeletons neutral and close to the shape of real document content.

Code snippets:

- Modern code blocks are `.listingblock.docs-code-block[data-code-block]` with `.docs-code-content`, a copy button, and optionally `.docs-code-title` or `.docs-code-toolbar`.
- Single-language snippets should show the code and copy action only. Do not add persistent language badges, bottom-right labels, legacy `copytoclipboard` controls, or decorative headers for plain snippets.
- If a block has a real title, render `.docs-code-title` outside the frame for standalone blocks. For multi-language samples, the shared title should appear once above the grouped tabs, not repeated inside every sample.
- Copy buttons are icon-only controls with accessible labels. Keep them in the top-right of standalone blocks or in the toolbar for tabbed blocks.
- Unknown or unqualified `[source]` snippets should remain plaintext. They must not be auto-detected as Bash, and they must not display `Text` as a visible badge.
- Shell-like snippets should be labeled `Shell` when a label is shown. Do not show `Bash` for `[source,bash]`, `[source,shell]`, `[source,sh]`, `[source,zsh]`, or `[source,console]`.
- Static Shiki output owns token colors through `--shiki-light` and `--shiki-dark` spans. Do not add Highlight.js, Prism, client-side Shiki, or any other runtime syntax highlighter.

Dependency snippet tabs:

- Dependency examples are usually multi-language samples for Gradle and Maven. Preserve tab behavior through `.multi-language-selector`, `.multi-language-sample`, `.docs-code-toolbar`, `.docs-code-tabs`, and `.docs-code-language`.
- Tabs are controls for switching equivalent dependency declarations, not badges. Use concise labels such as `Gradle`, `Gradle (Groovy)`, `Gradle (Kotlin)`, and `Maven`, with icons only where the platform tab renderer already supports them.
- Keep tab groups horizontally scrollable on small screens and avoid layouts where the copy button forces tab text to wrap awkwardly.
- The copied classpath script `guide-assets/js/multi-language-sample.js` may provide compatibility behavior, but visible styling belongs to `platform-assets/site.css` and hydration belongs to platform `site.js`.

Configuration and property snippets:

- Configuration examples should use real language labels when available: `YAML`, `Properties`, `TOML`, `HOCON`, `JSON`, `Groovy`, or `Java`. Avoid generic `Text` labels unless the snippet is truly plaintext.
- Property names in prose or tables should stay easy to scan. Inline property names belong in inline code; configuration reference pages belong in the reference panel rather than being duplicated into the main guide.
- Search results for configuration properties must open the configuration reference in the sheet and focus the generated `configuration-property-*` row anchor.
- Embedded configuration reference pages are allowed to receive small iframe-only styles from `GeneratePlatformDocsTask.transformEmbeddedReferenceHtml`; those styles must not become global platform CSS imports.

Sidebar rules:

- Categories group projects; category counts stay hidden. Keep `Most Popular` first because the first matching category wins.
- Project rows are first-level expandable items with icon, display name, and optional version. The project's TOC is nested under the project row and should not appear as a separate global tree.
- Text must remain readable in expanded, collapsed, and mobile states. Do not truncate project or TOC labels in a way that hides meaning; allow wrapping where needed.
- Selecting a project should reveal the project title and document metadata actions. On mobile, outside clicks after selection should close the sidebar.
- Section edit links use an icon-only `contribute-btn` that appears on heading hover or keyboard focus. Keep the text in `title` and `aria-label`, not visible heading chrome.

Top bar rules:

- Keep the top bar sticky, compact, and task-oriented. It should contain navigation collapse, breadcrumb/version context, global search, theme toggle, and the mobile sidebar button.
- The platform version is contextual metadata, not a marketing badge. Keep it small and stable so it does not compete with search.
- Search results should be keyboard and pointer usable, preserve visible focus, and scroll targets enough to orient the user without disorienting jumps.
- Icon buttons need titles, accessible labels, and stable square hit areas. Do not replace compact icon actions with wordy pill buttons unless the command is ambiguous without text.

Light and dark mode:

- Theme tokens live at the top of `site.css`. Extend token usage before adding one-off colors, and verify both `:root` and `body.dark-mode` values.
- The overview page and project documents share `var(--bg)` so switching views does not flash to a different page background.
- In dark mode the top bar and sidebar should read as black panels, while guide content, cards, tables, inline code, code frames, and reference panels stay legible.
- Shiki token colors must switch through `--shiki-light` and `--shiki-dark`; do not invert code blocks with filters or runtime class rewrites.
- Logos use bundled light/dark Micronaut assets. Do not fetch or hotlink logos from published docs sites.

Color palette:

- The primary palette is small and lives in `:root`: page `--bg #f7f9fb`, surfaces `--surface #ffffff`, cards `--card-surface #ffffff`, raised soft surface `--surface-strong #ecf4f1`, text `--text #172026`, muted text `--muted #5d6b78`, borders `--line #d8e1e7`, Micronaut green `--accent #00a676`, strong green `--accent-strong #00785a`, and link blue `--link #1565c0`.
- The dark palette intentionally separates the application chrome from content: top bar and sidebar use black panels, while page/content surfaces use neutral grays from `#222222` through `#484848`. Dark tokens are `--bg #333333`, `--surface #333333`, `--card-surface #262626`, `--surface-strong #222222`, `--text #dddddd`, `--muted #aaaaaa`, `--line #444444`, `--accent #ff9686`, `--accent-strong #f0bcb4`, and `--link #77aeff`.
- Guide typography keeps the Micronaut docs heading red `#ba3925` in light mode and a softened salmon `#f0bcb4` in dark mode. Major guide headings use black/white for stronger section orientation.
- Code snippets use their own neutral tokens rather than saturated blue-gray blocks. Light code frames use `--code-frame-bg #f7f8fa`, `--code-frame-border #e0e3e8`, and `--code-frame-separator #e5e7eb`; dark code frames use `--code-frame-bg #303030`, `--code-frame-border #484848`, and `--code-frame-separator rgba(255, 255, 255, 0.06)`.
- Dependency snippets are a slight elevation of normal code blocks, not a different visual system. Light dependency frames use `--code-dependency-bg #f6f7f8`, `--code-dependency-border #d9dee4`, and `--code-dependency-separator rgba(23, 32, 38, 0.055)`; dark dependency frames use `--code-dependency-bg #343434`, `--code-dependency-border #4d4d4d`, and `--code-dependency-separator rgba(255, 255, 255, 0.055)`.
- Admonitions intentionally avoid strong status colors. They use low-opacity neutral backgrounds and borders, with `--admonition-accent` only for the icon. Add warning/error color only when the content would be dangerous without stronger severity.
- Language and project icons may use brand colors from their official SVGs or known brand palettes. Do not let those colors control container surfaces, borders, headings, or guide typography.
- Older Asciidoctor compatibility colors such as table striping, example blocks, callout markers, and contribution buttons can remain as scoped legacy affordances, but new UI should prefer the token set above.
- When adding a new color, first decide whether it is a theme token, a scoped component token, a brand icon color, or a compatibility color. Avoid introducing naked hex values in component rules when an existing token communicates the same role.

Current designer evaluation:

- The product direction is right: the page now reads as a documentation application, not a landing page. The black/white sidebar shell, sticky command bar, categorized overview, lazy project menu, command-palette search, and sheet-style references create a coherent platform-docs experience.
- The strongest interaction is search. The top-bar field is visually prominent without becoming a hero element, result scopes are easy to understand, result rows have clear kind/project/title hierarchy, and mobile search uses the full available width. Keep this pattern as the model for future command surfaces.
- The strongest visual decision is the dark-mode separation between black chrome and gray content. At the audited desktop size, dark-mode token contrast was strong: `--text` on `--bg` was about 9.3:1, muted text on background about 5.4:1, link blue on background about 5.6:1, and code text on code surfaces above 10:1. This is accessible without feeling like a pure black reading plane.
- The main design weakness is document scale. Guide paragraphs are correct at `1rem / 1.6`, but desktop headings become very large because generated `h1` values use `em` against the guide base. In a reference product, the `h1` and `h2` scale should orient, not dominate. Prefer a tighter desktop scale before adding more visual decoration.
- The overview page works well when categories contain multiple cards. In small fixture builds, one-card categories create a narrow column with too much empty right-side space. If partial builds remain common, prefer a bounded overview content width, denser category grouping, or a layout that lets singleton categories sit in a two-column category/card composition.
- Overview cards have the right shadcn-adjacent vocabulary: 8px radius, quiet border, small icon tile, compact footer actions, and visible version metadata. The body copy should stay near 30 to 40 words; longer descriptions make cards feel like excerpts rather than navigation.
- The sidebar is visually calm and readable. Project rows have enough height for touch and version context, the arrow aligns with the title, and mobile off-canvas behavior feels natural. Keep the sidebar width around the current 384px because generated project names and TOC labels need room.
- The sidebar should remain the most trustworthy navigation inventory. In any generated fixture or partial build, verify that the overview card set and lazy sidebar menu expose the same projects.
- The top bar is appropriately utilitarian. The version belongs in the breadcrumb, the search belongs on the right, and the theme toggle is correctly icon-only. Avoid adding badges or project actions to the top bar; project metadata belongs in `.document-meta`.
- Document metadata badges are useful but should stay secondary. On wide desktop they can sit on the right of the project title area; below 1280px they should flow before content, as they do now. Do not let metadata create extra title top margin or horizontal overflow.
- Code snippets now fit the gray documentation palette better than blue-gray or IDE-style themes. Normal code blocks and dependency snippets should stay close in value; dependency blocks can be a slight elevation, not a separate color family. The tab separator should remain almost invisible.
- Code tabs should feel like inline controls, not a second toolbar system. The current Gradle/Maven tabs are readable, but selected tab text can be slightly stronger while unselected tabs remain medium weight. Do not reintroduce language badges for single-language snippets.
- Admonitions are moving in the right direction because they are quieter and left-aligned. They should feel like part of the article flow: neutral frame, one calm icon, readable text, no saturated warning panels unless severity genuinely requires it.
- The reference sheet has the right interaction model: about 60 percent width, blurred/de-emphasized page behind it, expand action, external link, and close. The iframe content still needs stronger embedded normalization so API and configuration pages feel less like pasted legacy pages. Watch for oversized `h1`, large blank regions, and missing configuration-table rhythm.
- Light mode is clean but slightly more generic than dark mode. The light background and card borders are professional, but repeated `#f4f4f5` hover states and many near-white surfaces can flatten hierarchy. Use `--surface-strong` more deliberately for empty states, category icons, and skeletons.
- Mobile behavior is structurally sound: top-bar title is removed, search becomes the primary control, cards become full width, and the sidebar opens as a sheet. The mobile overview text is readable, but the opening description consumes significant first-screen space; keep it as a short subtitle rather than a long product introduction.
- The `!important` rules are currently compatibility pressure points, mostly overriding old generated Asciidoctor/link/table/icon behavior. New platform components should not need `!important`; if a new rule requires it, first check selector scope, render order, or whether old guide markup is still leaking into the shell.
- The color inventory is acceptable for a docs product because many naked hex values are scoped compatibility colors or official icon brand colors. Future additions should trend toward fewer component-specific grays and more token reuse.
- Priority order for design cleanup: tighten desktop guide heading scale, normalize embedded reference pages, make singleton overview categories less sparse, reduce new naked color additions, then audit remaining `!important` uses by category.

Hard constraints:

- Generated `index.html` imports only `platform-assets/site.css` as the main page stylesheet. Never import `guide-assets/css/*`, `guide-assets/style/*`, generated Javadocs CSS, generated configuration reference CSS, or old Micronaut docs template CSS into the main shell.
- Keep the site static. Lazy document fragments, sidebar menu HTML, JSON indexes, and JavaScript fallback files are acceptable; runtime documentation rendering and downloaded published-docs HTML are not.
- Do not modify copied guide theme CSS to fix platform shell styling. Fix shell and rendered-guide appearance through platform templates, renderer output, and `site.css`.
- Do not introduce heavy runtime search, runtime syntax highlighting, remote UI libraries, or shadcn imports. The site may resemble shadcn-style layouts, but its components are locally owned.
- Preserve accessibility semantics: real buttons for actions, links for navigation, dialog semantics for the reference panel, visible focus states, useful `aria-label`s, and keyboard support for search, tabs, sidebar, and cards.

## Handlebars Rules

- Keep page rendering in Handlebars templates under `buildSrc/src/main/resources/io/micronaut/docs/templates`.
- Avoid moving large HTML strings into Java unless they are truly small embedded fragments.
- Use Handlebars partials for repeated markup.
- Recursive TOC rendering uses a Handlebars partial and requires `Handlebars.infiniteLoops(true)`.
- When adding fields to templates, update the Java model in `GeneratePlatformDocsTask` and tests that inspect the rendered site.

## Code Sample Rules

Code examples should look like modern documentation blocks:

- Every rendered listing block should use `docs-code-block`, `docs-code-toolbar`, `docs-code-content`, and a top-right copy button.
- Explicit shell snippets from `[source,bash]`, `[source,shell]`, `[source,sh]`, `[source,zsh]`, or `[source,console]` should display the label `Shell`, not `Bash`.
- Unknown or unqualified snippets, especially plain `[source]`, must not display `Bash` or `Text`.
- Unknown samples should be treated as plaintext so no compatibility script or highlighter infers Bash.
- Real language labels should stay useful: Java, Kotlin, Groovy, Gradle, Maven, XML, JSON, YAML, TOML, SQL, Properties, HTML.
- Do not reintroduce legacy `copytoclipboard` controls inside modern code blocks.
- Do not allow old paragraph wrappers such as `<p><div class="listingblock...">` to remain around code blocks.
- Highlighting is static Shiki output. If a lazy-loaded fragment lacks Shiki spans, inspect `highlightRenderedPlatformDocs` / `highlightGeneratedPlatformDocs` rather than adding browser-side highlighting.

When changing code sample behavior, update `PlatformDocsSearchTest.searchFixtureContainsTestProjectsAndRequiredIndexTerms` or the browser tests so regressions are caught.

## Search Rules

Search must stay fast and static:

- Do not add a heavy runtime search framework without first proving the generated static index is insufficient.
- The generated index must include compact API terms such as `applicationcontext`.
- The generated index must include compact configuration terms such as `nettydefaultallocatornumheaparenas`.
- Configuration reference results must navigate into the reference sheet and target the corresponding row anchor.
- Search UI should be usable with keyboard and pointer interactions.
- Search result focus should remain visible and centered enough to avoid disorienting jumps.

## Sidebar And Navigation Rules

- The sidebar shows project groups such as `Most Popular` and other curated categories.
- Category counts are intentionally hidden.
- Projects are first-level sidebar items. Expanding a project shows its TOC.
- The left sidebar shows only top-level guide sections. Numbered subsections are exposed through the right-side `In this section` rail generated from the same `toc.yml` model, not by parsing rendered heading text.
- Selecting a project should scroll enough that the project title and top action buttons are visible.
- On mobile, clicking outside an open sidebar after selecting a project should close the sidebar.
- Keep sidebar text readable; do not let labels be cut off.

## Reference Sheet Rules

- API and configuration reference links open in the sheet panel instead of replacing the platform docs page.
- The sheet should preserve context and use about 60 percent of the screen by default.
- The rest of the page should be visually de-emphasized while the sheet is open.
- The sheet must support expand/collapse.
- Configuration reference pages need embedded styling because the original page chrome is hidden in the iframe.
- Reference row anchors should be highlighted when opened from search.

## GitHub Actions Rules

The workflow should keep using matrix parallelism:

- The `plan` job scans the platform project set and writes the matrix.
- The `test-build-logic` job runs only fast tests that do not require generated docs output, so it must not build the six-doc layout before the matrix.
- The `build-guides` matrix initializes only each shard's guide submodules, verifies alignment, builds docs, and uploads staged docs artifacts.
- The `render` job downloads artifacts, merges generated guide output, renders `build/site`, verifies it, runs browser-backed site tests, and uploads both a debugging artifact and the GitHub Pages artifact.
- The deploy job should run only for `main`.
- Use the GitHub-provided token for GitHub API calls when available. Prefer `GITHUB_TOKEN` or `GH_TOKEN`; do not require a custom token locally.
- Avoid recursive submodule checkout in Actions because it wastes time and clones projects that no shard needs.

## Submodule And Version Rules

- `repos/micronaut-platform` should stay on the `5.0.x` branch unless the platform version set intentionally changes.
- Project submodules should be checked out at platform-managed tags, normally `v<version>`.
- If a tag is missing, fail with a clear message rather than silently using a branch.
- `micronaut-crac` and `micronaut-guides` are excluded from aggregation.
- Repository creation dates are used only for sorting; missing values should remain in `platform-doc-repositories.properties`, not in `platform-doc-projects.properties`.
- Do not delete submodules just to speed up Actions; the workflow controls which submodules are initialized.

## UI Design Rules

- The site intentionally resembles shadcn-style layouts without importing shadcn.
- Keep the experience application-like, not a landing page.
- Use restrained cards for module overview entries.
- Use icon buttons for compact actions.
- The top-left logo uses bundled Micronaut light/dark assets.
- The theme should default from the user's browser preference and still allow manual switching.
- In dark mode, the top and left panels should be black and sidebar text should stay readable.
- Avoid layouts where badges increase the title-to-top spacing or force horizontal overflow.
- On small screens, badges should wrap before touching text.

## Testing Notes

`PlatformDocsSearchTest` serves `build/site` with a small local HTTP server and uses Playwright. It covers:

- rendered Core and Serialization fixture integrity
- generated sidebar categories
- generated search index terms
- search navigation to guide content
- search navigation to configuration reference properties
- mobile sidebar close behavior
- sidebar project click loading
- overview card click loading
- `file://` fallback loading through script data
- HTTP fetch failure fallback loading through script data
- code block rendering, copy buttons, paragraph unwrap, shell label cleanup, and static Shiki tokenization

If Playwright cannot launch in the local macOS sandbox, tests may abort with a clear message. On CI/headless Linux they should run headless.

## Troubleshooting

- Missing `gradle/platform-doc-projects.properties`: run `./gradlew -q scanPlatformProjects`.
- Missing submodule: run `./gradlew -q syncPlatformProjectSubmodules`, or for CI shard work use `syncPlatformGuideShardSubmodules`.
- Alignment failure: inspect the submodule for local changes, then run `./gradlew -q alignPlatformVersions`.
- Missing generated guide HTML: run `./gradlew -q -PplatformDocs.projectSlugs=<slug> buildPlatformGuideDocs`.
- CI final render fails while rendering Core/source guides: ensure `stagePlatformGuideDocsArtifact` produced `repos/<project>/build/platform-docs/guide.html` in each shard artifact. The final render job should read those fragments instead of rendering from uninitialized submodule sources.
- `Documentation could not be loaded`: check both `platform-assets/documents/<slug>.html` and `<slug>.js`; the JavaScript fallback should cover `file://` and HTTP fetch failures.
- No search index available under `file://`: ensure `platform-assets/search-index.js` exists; browsers block JSON fetches from `file://`.
- Code lacks highlighting after lazy load: check that the Shiki tasks ran and that `platform-assets/documents/<slug>.html` contains `class="shiki"` with `--shiki-light` / `--shiki-dark` token styles.
- `Missing content anchor ...`: compare the sidebar TOC item generated from `toc.yml` with the section ids produced by `ModernGuideRenderer`.
- Missing repository dates: initialize the submodule or rerun scan with `GITHUB_TOKEN`/`GH_TOKEN`; unresolved repositories sort last using `9999-12-31T23:59:59Z`.

## Final Review Checklist

Before handing work back after source changes:

- `./gradlew -q :buildSrc:compileJava` passes.
- `./gradlew -q -PplatformDocs.projectSlugs=core,serde renderPlatformDocs` passes for renderer/UI/test fixture changes.
- `./gradlew :buildSrc:test` passes.
- `./gradlew -q -PplatformDocs.projectSlugs=core,serde cM` passes when verification behavior was touched.
- `./gradlew -q spotlessCheck` passes.
- The final response mentions any command that could not be run.
- The final response calls out unrelated untracked files if they remain in the working tree.
