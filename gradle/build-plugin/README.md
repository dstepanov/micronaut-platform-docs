This directory contains build-only jars that must be available before the
corresponding upstream artifact is generally consumable from normal repositories.

Current artifact:

- `micronaut-docs-build-plugins-8.0.0-SNAPSHOT.jar`

Retained temporarily:

- `micronaut-gradle-plugins-8.0.0-M18.jar`

`buildSrc/build.gradle.kts` adds the jar directly with `files(...)`. This keeps
CI self-contained without requiring a local Maven repository layout, `mavenLocal`,
or a prior publish from `/Users/denisstepanov/dev/micronaut-build` or
`micronaut-docs-build-plugins`.

The vendored jar supplies the Micronaut docs engine and `io.micronaut.docs.Renderer`
API used by the platform renderer. The platform site should render guide fragments
through that API and local templates/styles, not by importing the old generated guide
template CSS or JavaScript.

The retained `micronaut-gradle-plugins` jar is not used by `buildSrc`; keep it only
until the repository no longer needs that fallback artifact.
