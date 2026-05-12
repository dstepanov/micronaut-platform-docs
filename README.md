# Micronaut Platform Docs

This repository builds a single-page Micronaut Platform documentation site from Micronaut project git submodules.

The docs are not downloaded from the published documentation sites. Every guide is built from the corresponding source repository and then assembled into one static HTML page with a project sidebar.

`repos/micronaut-platform` is the source of truth for the version set. The root build scans `repos/micronaut-platform/gradle/libs.versions.toml`, writes `gradle/platform-doc-projects.properties`, sorts projects by GitHub repository creation date, and verifies that each documentation submodule is checked out at the matching release tag declared by the platform.

## Layout

```text
.github/workflows/
  platform-docs.yml  GitHub Actions workflow that builds and publishes the site
buildSrc/
  src/main/java/io/micronaut/docs/
                     Gradle task implementation for scanning, syncing, alignment,
                     guide builds, site generation, and verification
  src/main/resources/io/micronaut/docs/
                     Handlebars templates, CSS, JavaScript, and bundled UI assets
gradle/
  platform-doc-projects.properties
                     Generated project manifest used by the Gradle tasks
repos/
  micronaut-platform/  Version catalog source of truth, branch 5.0.x
  micronaut-*/         Guide sources discovered from the platform catalog
```

Generated output is written under `build/site`:

```text
build/site/
  index.html         Single-page platform documentation UI
  assets/            Generated guide output copied from each submodule
  guide-assets/      Shared Micronaut guide theme assets copied from the build plugin classpath
  platform-assets/   Static assets owned by this repository
```

## Requirements

- Java 25 is required to build Micronaut 5 documentation.
- All Gradle commands must use the wrapper: `./gradlew`.
- Non-test Gradle commands should use `-q`.
- Network access is required when syncing missing submodules, fetching tags, and resolving GitHub repository metadata.
- `GITHUB_TOKEN` or `GH_TOKEN` is optional locally, but recommended because `scanPlatformProjects` uses the GitHub API to resolve repository creation dates.
- `PLATFORM_DOCS_GUIDE_SHARD_INDEX` and `PLATFORM_DOCS_GUIDE_SHARD_COUNT`, or the matching Gradle properties `platformDocs.guideShardIndex` and `platformDocs.guideShardCount`, select a guide-build shard for GitHub Actions matrix jobs.

```bash
java -version
./gradlew -q tasks
```

## Local Workflow

Run the workflow in this order from the repository root.

1. Scan the Micronaut Platform catalog and generate the project manifest.

```bash
./gradlew -q scanPlatformProjects
```

This task reads `repos/micronaut-platform/gradle/libs.versions.toml`, discovers every Micronaut BOM project managed by the platform, resolves the matching GitHub repository, derives the documentation branch from the platform version, and writes `gradle/platform-doc-projects.properties`.

`micronaut-crac` and `micronaut-guides` are intentionally excluded from aggregation because they are not rendered into this platform docs page.

Repository ordering is based on `repositoryCreatedAt`. The task first asks the GitHub API for repository creation dates. If a repository cannot be resolved remotely, it falls back to the oldest local git root commit when the submodule is present. If neither source is available, it writes `9999-12-31T23:59:59Z` as an unknown-date sentinel so unresolved projects sort last.

2. Add missing documentation submodules.

```bash
./gradlew -q syncPlatformProjectSubmodules
```

This task reads the generated manifest, adds projects that are not already present under `repos/`, fetches tags in each submodule, and checks out the platform-managed `v<version>` tag. New submodule pointers are staged in the root repository.

3. Align every documentation submodule to the platform version set.

```bash
./gradlew -q alignPlatformVersions
```

This task reads the platform version catalog, fetches tags in each submodule, and checks out `v<version>` in detached HEAD mode. The version comes from the matching `managed-micronaut-*` entry in `repos/micronaut-platform/gradle/libs.versions.toml`.

4. Verify the submodule alignment.

```bash
./gradlew -q verifyPlatformAlignment
```

This task checks that each submodule HEAD has the expected platform tag.

5. Generate the platform documentation page.

```bash
./gradlew -q generatePlatformDocs
```

`generatePlatformDocs` depends on the alignment verifier and on `buildPlatformGuideDocs`. The guide build task invokes each discovered submodule's Gradle wrapper:

```text
repos/<project>/gradlew -q -Dorg.gradle.vfs.watch=false docs
```

The generator then:

- reads each generated `build/docs/guide/index.html`
- reads each project's `src/main/docs/guide/toc.yml`
- prefixes anchors so all guides can coexist in one page
- copies generated guide content to `build/site/assets/<project>/docs`
- copies shared Micronaut guide assets from `grails-doc-files.jar` to `build/site/guide-assets`
- copies local UI assets to `build/site/platform-assets`
- renders the shell and sidebar with Handlebars templates

Open the generated page at:

```text
build/site/index.html
```

For GitHub Actions, guide docs are built in independent shards and merged before rendering the final page. A single shard can be built and staged locally with:

```bash
./gradlew -q -PplatformDocs.guideShardIndex=0 -PplatformDocs.guideShardCount=8 verifyPlatformAlignment stagePlatformGuideDocsArtifact
```

The shard artifact is staged under `build/guide-docs-artifact` with paths that can be merged back into the repository root:

```text
build/guide-docs-artifact/repos/<project>/build/docs
```

## Verification

```bash
./gradlew -q check
./gradlew -q cM
./gradlew -q spotlessCheck
```

`check` verifies both platform alignment and the generated platform page.

`cM` is the project-level verification entry point and currently depends on `check`. `spotlessCheck` is available as a placeholder task so the repository follows the standard Micronaut command shape.

## Task Reference

| Task | Purpose | Mutates files |
| --- | --- | --- |
| `scanPlatformProjects` | Scans the platform catalog and writes the generated project manifest. | `gradle/platform-doc-projects.properties` |
| `syncPlatformProjectSubmodules` | Adds missing Micronaut project submodules and checks out platform-managed tags. | `.gitmodules`, `repos/*` |
| `alignPlatformVersions` | Checks out each project submodule at the platform-managed release tag. | `repos/*` git state |
| `verifyPlatformAlignment` | Verifies submodule HEAD tags match platform-managed versions. | No |
| `buildPlatformGuideDocs` | Runs each submodule's `docs` task with Java 25. | `repos/*/build/docs` |
| `stagePlatformGuideDocsArtifact` | Stages generated guide docs for a selected shard. | `build/guide-docs-artifact` |
| `renderPlatformDocs` | Renders the single-page site from existing generated guide docs. | `build/site` |
| `generatePlatformDocs` | Builds guide docs and renders the single-page platform site. | `build/site` |
| `verifyPlatformDocs` | Verifies generated HTML and required static assets. | No |
| `verifyRenderedPlatformDocs` | Verifies a site rendered from prebuilt guide docs. | No |
| `check` | Runs platform alignment and generated site verification. | Depends on generated docs |
| `cM` | Micronaut-style aggregate verification alias. | Depends on `check` |

## GitHub Pages

The `Platform Docs` GitHub Actions workflow lives at `.github/workflows/platform-docs.yml`. It runs on pushes to `main` and can also be started manually from the Actions tab.

The workflow uses GitHub Actions matrix parallelism rather than parallel Gradle workers in one runner:

- eight `build-guides` jobs run at the same time, one for each shard
- each shard checks out submodules, installs Java 25, verifies platform alignment, builds only its selected projects, and uploads `build/guide-docs-artifact`
- the `render` job downloads and merges all shard artifacts, renders `build/site`, verifies the rendered page, and uploads the GitHub Pages artifact

```bash
./gradlew -q verifyPlatformAlignment stagePlatformGuideDocsArtifact
./gradlew -q verifyRenderedPlatformDocs
```

The workflow uploads `build/site` twice:

- as `micronaut-platform-docs-site`, a downloadable artifact for debugging
- as the GitHub Pages artifact consumed by `actions/deploy-pages`

The deploy job runs only for `refs/heads/main`. Configure the repository's Pages source to use GitHub Actions.

## Updating The Platform Version

The platform checkout under `repos/micronaut-platform` should stay on the `5.0.x` branch unless the docs set is intentionally moved. After updating that checkout:

```bash
./gradlew -q scanPlatformProjects
./gradlew -q syncPlatformProjectSubmodules
./gradlew -q alignPlatformVersions
./gradlew -q generatePlatformDocs
```

Review changes to:

- `.gitmodules`
- `gradle/platform-doc-projects.properties`
- submodule pointers under `repos/*`
- generated output under `build/site` when validating locally

## UI Approach

The site is generated as plain static HTML, CSS, and JavaScript from Gradle. It provides:

- a project selector
- first-level expandable project sections in the sidebar
- the active Micronaut Platform checkout in the top bar
- a sticky sidebar
- collapsible table-of-contents sections
- prefixed anchors so all selected guides can coexist on one page

The HTML frame, sidebar project sections, TOC entries, and JavaScript data are rendered through Handlebars. The TOC model is parsed from each project's `toc.yml` using SnakeYAML and rendered with a single recursive partial; the renderer enables `Handlebars.infiniteLoops(true)` because Handlebars.java disables recursive partials by default. The root Gradle task still builds all guide content from the git submodules, copies their generated docs under `build/site/assets/<project>/docs`, and writes the platform UI assets under `build/site/platform-assets`. Shared guide CSS, JavaScript, fonts, and default images are copied from the Micronaut build plugin classpath instead of from any generated submodule docs directory.

## Troubleshooting

- If `buildPlatformGuideDocs` reports a missing submodule, run `./gradlew -q syncPlatformProjectSubmodules`.
- If alignment fails because a submodule has local changes, inspect the affected `repos/<project>` checkout before rerunning `alignPlatformVersions`.
- If many manifest entries have `repositoryCreatedAt=9999-12-31T23:59:59Z`, set `GITHUB_TOKEN` or `GH_TOKEN` and rerun `scanPlatformProjects`.
- If the generated site is missing a project, confirm that the project has `src/main/docs/guide/toc.yml` and that its submodule `docs` task produced `build/docs/guide/index.html`.
