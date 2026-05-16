package io.micronaut.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GuideTocTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsNumberedEntriesWithoutSourceFilesForStagedArtifacts() throws IOException {
        Path tocFile = temporaryDirectory.resolve("toc.yml");
        Files.writeString(tocFile, """
            title: Test Guide
            introduction: Introduction
            configuration:
              title: Configuration
              properties: Properties
              nested:
                title: Nested Section
            """);

        GuideToc toc = GuideToc.read(tocFile);

        assertEquals("Test Guide", toc.title());
        assertEquals(
            List.of(
                new GuideToc.Entry(0, "1", "introduction", "Introduction", null),
                new GuideToc.Entry(0, "2", "configuration", "Configuration", null),
                new GuideToc.Entry(1, "2.1", "properties", "Properties", null),
                new GuideToc.Entry(1, "2.2", "nested", "Nested Section", null)
            ),
            toc.entries()
        );
    }

    @Test
    void resolvesSourceFilesLikeMicronautGuideToc() throws IOException {
        Path guideSource = temporaryDirectory.resolve("guide");
        Files.createDirectories(guideSource.resolve("configuration"));
        Files.writeString(guideSource.resolve("toc.yml"), """
            title: Test Guide
            introduction: Introduction
            configuration:
              title: Configuration
              properties: Properties
            """);
        Files.writeString(guideSource.resolve("introduction.adoc"), "Intro");
        Files.writeString(guideSource.resolve("configuration.adoc"), "Config");
        Files.writeString(guideSource.resolve("configuration/properties.adoc"), "Properties");

        GuideToc toc = GuideToc.readSource(guideSource);

        assertEquals(
            List.of(
                new GuideToc.Entry(0, "1", "introduction", "Introduction", "introduction.adoc"),
                new GuideToc.Entry(0, "2", "configuration", "Configuration", "configuration.adoc"),
                new GuideToc.Entry(1, "2.1", "properties", "Properties", "configuration/properties.adoc")
            ),
            toc.entries()
        );
    }
}
