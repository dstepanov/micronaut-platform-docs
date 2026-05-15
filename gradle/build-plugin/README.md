This directory contains build-only jars that must be available before the
corresponding upstream artifact is generally consumable from normal repositories.

Current artifact:

- `micronaut-gradle-plugins-8.0.0-M18.jar`

`buildSrc/build.gradle.kts` adds the jar directly with `files(...)`. This keeps
CI self-contained without requiring a local Maven repository layout, `mavenLocal`,
or a prior publish from `/Users/denisstepanov/dev/micronaut-build` or
`micronaut-docs-build-plugins`.
