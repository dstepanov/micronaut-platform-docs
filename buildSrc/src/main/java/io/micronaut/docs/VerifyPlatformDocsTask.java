package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class VerifyPlatformDocsTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getIndexFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

    @TaskAction
    public void verify() throws IOException {
        Path indexFile = getIndexFile().get().getAsFile().toPath();
        List<GuideProject> projects = GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath());
        getLogger().quiet("Verifying generated platform docs page: {}", indexFile);
        String html = Files.readString(indexFile, StandardCharsets.UTF_8);
        int index = 0;
        for (GuideProject project : projects) {
            getLogger().quiet("[{}/{}] Verifying generated entries for {}.", ++index, projects.size(), project.displayName());
            require(html.contains("data-project=\"" + project.slug() + "\""), "Missing document for " + project.slug());
            require(html.contains("data-project-nav=\"" + project.slug() + "\""), "Missing sidebar for " + project.slug());
            require(html.contains("data-project-option=\"" + project.slug() + "\""), "Missing sidebar project option for " + project.slug());
            require(html.contains("id=\"" + project.slug() + "-introduction\""), "Missing prefixed introduction anchor for " + project.slug());
        }
        require(html.contains("<details class=\"toc-section\""), "Generated sidebar does not contain collapsible sections.");
        require(html.contains("<details class=\"project-section\""), "Generated sidebar does not contain first-level project sections.");
        require(html.contains("data-platform"), "Generated top bar does not display platform information.");
        require(html.contains("Micronaut Platform Docs"), "Generated top bar does not use the platform docs title.");
        require(!html.contains("sidebar-brand-version"), "Generated sidebar still contains the top-left version hint.");
        require(!html.contains("sidebar-brand-caret"), "Generated sidebar still contains the brand caret.");
        require(!html.contains("<span>Micronaut Platform</span>"), "Generated top bar still contains the Micronaut Platform label.");
        require(html.contains("class=\"sidebar-collapse\""), "Generated top bar does not contain a menu collapse control.");
        require(html.contains("id=\"theme-switcher\""), "Generated top bar does not contain the official-docs style theme switcher.");
        require(html.contains("class=\"theme-icon theme-icon-moon\""), "Generated theme switcher does not contain a dark-theme icon.");
        require(html.contains("class=\"theme-icon theme-icon-sun\""), "Generated theme switcher does not contain a light-theme icon.");
        require(html.contains("platform-assets/logos/micronaut-horizontal-black.svg"), "Generated sidebar does not reference the black Micronaut logo.");
        require(html.contains("platform-assets/logos/micronaut-horizontal-white.svg"), "Generated sidebar does not reference the white Micronaut logo.");
        require(!html.contains("<span>Documentation</span>"), "Generated document metadata still contains documentation links.");
        require(html.contains("<span>GitHub repository</span>"), "Generated document metadata does not label GitHub repository links.");
        require(html.contains("lucide-github"), "Generated document metadata does not use the Lucide GitHub icon.");
        require(html.contains("lucide-git-branch"), "Generated branch metadata does not use the Lucide git branch icon.");
        require(html.contains("<body class=\"body js\" id=\"docs\""), "Generated page does not use Micronaut guide body font hooks.");
        require(html.contains("href=\"platform-assets/site.css\""), "Generated page does not reference the external stylesheet.");
        require(html.contains("src=\"platform-assets/site.js\""), "Generated page does not reference the external script.");
        require(html.contains("href=\"guide-assets/css/main.css\""), "Generated page does not reference the classpath Micronaut guide stylesheet.");
        require(html.contains("href=\"guide-assets/css/custom.css\""), "Generated page does not reference the classpath Micronaut custom guide stylesheet.");
        require(html.contains("href=\"guide-assets/css/highlight/agate.css\""), "Generated page does not reference the classpath Micronaut guide highlight stylesheet.");
        require(html.contains("href=\"guide-assets/css/multi-language-sample.css\""), "Generated page does not reference the classpath Micronaut multi-language stylesheet.");
        require(html.contains("src=\"guide-assets/js/highlight.pack.js\""), "Generated page does not reference the classpath Micronaut highlight script.");
        require(html.contains("src=\"guide-assets/js/multi-language-sample.js\""), "Generated page does not reference the classpath Micronaut multi-language script.");

        Path outputDirectory = indexFile.getParent();
        require(Files.isRegularFile(outputDirectory.resolve("guide-assets/css/main.css")), "Missing classpath Micronaut guide stylesheet asset.");
        require(Files.isRegularFile(outputDirectory.resolve("guide-assets/css/tools.css")), "Missing classpath Micronaut Font Awesome stylesheet asset.");
        require(Files.isRegularFile(outputDirectory.resolve("guide-assets/fonts/fontawesome-webfont.woff")), "Missing classpath Micronaut Font Awesome font asset.");
        require(Files.isRegularFile(outputDirectory.resolve("guide-assets/js/highlight.pack.js")), "Missing classpath Micronaut highlight script asset.");
        require(Files.isRegularFile(outputDirectory.resolve("guide-assets/js/multi-language-sample.js")), "Missing classpath Micronaut multi-language script asset.");
        require(Files.isRegularFile(outputDirectory.resolve("platform-assets/logos/micronaut-horizontal-black.svg")), "Missing black Micronaut logo asset.");
        require(Files.isRegularFile(outputDirectory.resolve("platform-assets/logos/micronaut-horizontal-white.svg")), "Missing white Micronaut logo asset.");
        String siteScript = Files.readString(outputDirectory.resolve("platform-assets/site.js"), StandardCharsets.UTF_8);
        require(siteScript.contains("window.addEventListener(\"scroll\", queueScrollSpy"), "Generated script does not highlight sections while scrolling.");
        require(siteScript.contains("activeSectionFromScroll"), "Generated script does not include scroll spy section detection.");
        getLogger().quiet("Verified generated platform docs page for {} projects.", projects.size());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
