package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class VerifyPlatformDocsTask extends DefaultTask {
    private static final Pattern SECTION = Pattern.compile("\\bdata-section=\"([^\"]+)\"");
    private static final Pattern CODE_SAMPLE_IMPORTANT_STYLE = Pattern.compile(
        "\\.(?:docs-code|multi-language-(?:selector|sample))[^{}]*\\{[^}]*!important",
        Pattern.DOTALL
    );

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getIndexFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

    @Input
    @Optional
    public abstract Property<String> getProjectSlugs();

    @TaskAction
    public void verify() throws IOException {
        Path indexFile = getIndexFile().get().getAsFile().toPath();
        List<GuideProject> projects = GuideProject.selectBySlugs(
            GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath()),
            getProjectSlugs().getOrElse("")
        );
        getLogger().quiet("Verifying generated platform docs page: {}", indexFile);
        String html = Files.readString(indexFile, StandardCharsets.UTF_8);
        Path outputDirectory = indexFile.getParent();
        Path sidebarMenuFile = outputDirectory.resolve("platform-assets/sidebar-menu.html");
        Path sidebarMenuScriptFile = outputDirectory.resolve("platform-assets/sidebar-menu.js");
        require(Files.isRegularFile(sidebarMenuFile), "Missing lazy sidebar menu fragment.");
        require(Files.isRegularFile(sidebarMenuScriptFile), "Missing lazy sidebar menu script fallback.");
        String sidebarMenuHtml = Files.readString(sidebarMenuFile, StandardCharsets.UTF_8);
        int index = 0;
        for (GuideProject project : projects) {
            getLogger().quiet("[{}/{}] Verifying generated entries for {}.", ++index, projects.size(), project.displayName());
            require(html.contains("data-project=\"" + project.slug() + "\""), "Missing document for " + project.slug());
            require(
                html.contains("data-document-url=\"platform-assets/documents/" + project.slug() + ".html\""),
                "Missing lazy document URL for " + project.slug()
            );
            require(html.contains("data-project-card=\"" + project.slug() + "\""), "Missing overview card for " + project.slug());
            require(html.contains("href=\"#" + project.slug() + "\""), "Missing overview docs link for " + project.slug());
            require(sidebarMenuHtml.contains("data-project-nav=\"" + project.slug() + "\""), "Missing sidebar for " + project.slug());
            require(sidebarMenuHtml.contains("data-project-option=\"" + project.slug() + "\""), "Missing sidebar project option for " + project.slug());
            require(sidebarMenuHtml.contains("project-icon"), "Missing sidebar project icons.");
            if (project.slug().equals("core")) {
                require(
                    html.contains("platform-assets/icons/micronaut-sally.svg")
                        || sidebarMenuHtml.contains("platform-assets/icons/micronaut-sally.svg"),
                    "Micronaut Core does not use the Sally project icon."
                );
            }
            Set<String> sections = projectSections(sidebarMenuHtml, project.slug());
            require(!sections.isEmpty(), "Missing sidebar section links for " + project.slug());
            Path documentFile = outputDirectory.resolve("platform-assets/documents/" + project.slug() + ".html");
            Path documentScriptFile = outputDirectory.resolve("platform-assets/documents/" + project.slug() + ".js");
            require(Files.isRegularFile(documentFile), "Missing lazy document fragment for " + project.slug());
            require(Files.isRegularFile(documentScriptFile), "Missing lazy document script fallback for " + project.slug());
            String documentHtml = Files.readString(documentFile, StandardCharsets.UTF_8);
            require(!documentHtml.contains("intellij-platform-"), "Generated code highlighting still uses the IntelliJ Shiki theme for " + project.slug());
            require(!documentHtml.contains("highlightjs"), "Generated document still contains Highlight.js classes for " + project.slug());
            require(
                !documentHtml.contains(" hljs") && !documentHtml.contains("class=\"hljs"),
                "Generated document still contains old Highlight.js code classes for " + project.slug()
            );
            require(!documentHtml.contains("class=\"fa "), "Generated document still contains old Font Awesome icon classes for " + project.slug());
            for (String section : sections) {
                require(documentHtml.contains("id=\"" + section + "\""), "Missing content anchor " + section + " for " + project.slug());
            }
        }
        require(html.contains("data-sidebar-menu"), "Generated page does not contain the lazy sidebar menu host.");
        require(html.contains("data-menu-url=\"platform-assets/sidebar-menu.html\""), "Generated page does not reference the lazy sidebar menu fragment.");
        require(html.contains("data-menu-script-url=\"platform-assets/sidebar-menu.js\""), "Generated page does not reference the lazy sidebar menu script fallback.");
        require(!html.contains("<details class=\"project-section\""), "Generated page still embeds first-level project sidebar sections.");
        require(sidebarMenuHtml.contains("<details class=\"project-section\""), "Generated sidebar menu does not contain first-level project sections.");
        require(!sidebarMenuHtml.contains("class=\"toc-children\""), "Generated sidebar menu still contains nested TOC subsections.");
        require(!html.contains("project-filter") && !sidebarMenuHtml.contains("project-filter"), "Generated page still contains the sidebar project filter.");
        require(html.contains("data-overview"), "Generated page does not contain the platform overview.");
        require(html.contains("data-project-category=\"most-popular\""), "Generated overview does not put Most Popular first.");
        require(html.contains("class=\"project-card-short-description\""), "Generated overview cards do not contain short project descriptions.");
        require(html.contains("class=\"project-card-long-description\""), "Generated overview cards do not contain long project descriptions.");
        require(html.contains("class=\"project-card-footer\""), "Generated overview cards do not contain a footer.");
        require(html.contains("data-overview-doc-link"), "Generated overview cards do not link back to the in-page docs.");
        require(html.contains("class=\"document-skeleton\""), "Generated document loading state does not use a skeleton.");
        require(html.contains("data-page-index"), "Generated page does not contain the right-side page index.");
        require(html.contains("In this section"), "Generated page index does not label the current page sections.");
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
        require(html.contains("data-reference-panel"), "Generated page does not contain the in-page reference panel.");
        require(html.contains("role=\"dialog\""), "Generated reference panel does not use dialog semantics.");
        require(html.contains("data-reference-overlay"), "Generated reference panel does not contain an overlay.");
        require(html.contains("data-reference-open"), "Generated document metadata does not contain reference open controls.");
        require(html.contains("data-reference-frame"), "Generated page does not contain the reference iframe.");
        require(html.contains("<body class=\"body js"), "Generated page does not use Micronaut guide body font hooks.");
        require(html.contains("id=\"docs\""), "Generated page does not use the docs body id.");
        require(html.contains("href=\"platform-assets/site.css\""), "Generated page does not reference the external stylesheet.");
        require(html.contains("rel=\"icon\" href=\"platform-assets/icons/micronaut-sally.svg\""), "Generated page does not reference the Micronaut favicon.");
        require(html.contains("src=\"platform-assets/site.js\""), "Generated page does not reference the external script.");
        require(!html.contains("href=\"guide-assets/css/"), "Generated page imports classpath Micronaut guide CSS.");
        require(!html.contains("href=\"guide-assets/style/"), "Generated page imports old Micronaut guide template CSS.");
        require(!html.contains("guide-assets/"), "Generated page still references old Micronaut guide template assets.");
        require(!html.contains("multi-language-sample.js"), "Generated page still depends on the old Micronaut multi-language script.");
        require(!html.contains("highlight.pack.js"), "Generated page still runs Highlight.js in the browser.");
        require(!html.contains("initHighlightingOnLoad"), "Generated page still initializes runtime syntax highlighting.");

        require(Files.isRegularFile(outputDirectory.resolve("platform-assets/logos/micronaut-horizontal-black.svg")), "Missing black Micronaut logo asset.");
        require(Files.isRegularFile(outputDirectory.resolve("platform-assets/logos/micronaut-horizontal-white.svg")), "Missing white Micronaut logo asset.");
        require(Files.isRegularFile(outputDirectory.resolve("platform-assets/icons/micronaut-sally.svg")), "Missing Micronaut Sally project icon asset.");
        String siteScript = Files.readString(outputDirectory.resolve("platform-assets/site.js"), StandardCharsets.UTF_8);
        String siteCss = Files.readString(outputDirectory.resolve("platform-assets/site.css"), StandardCharsets.UTF_8);
        require(siteScript.contains("window.addEventListener(\"scroll\", queueScrollSpy"), "Generated script does not highlight sections while scrolling.");
        require(siteScript.contains("activeSectionFromScroll"), "Generated script does not include scroll spy section detection.");
        require(siteScript.contains("renderPageIndex"), "Generated script does not build the right-side page index.");
        require(siteScript.contains("refreshPageIndex"), "Generated script does not sync the page index active section.");
        require(siteScript.contains("const pageIndexItems = {"), "Generated script does not include the static TOC-backed page index model.");
        require(!siteScript.contains("numberedHeading"), "Generated script still infers page index entries from heading text.");
        require(siteScript.contains("loadProjectDocument"), "Generated script does not lazy-load project documents.");
        require(siteScript.contains("loadSidebarMenu"), "Generated script does not lazy-load the sidebar menu.");
        require(siteScript.contains("openReference"), "Generated script does not support opening project references in context.");
        require(siteScript.contains("updateReferenceProject"), "Generated script does not keep project references aligned with the active project.");
        require(siteScript.contains("const codeLanguageIcons = {"), "Generated script does not include the static code language icon registry.");
        require(siteScript.contains("\"java\":{\"viewBox\""), "Generated script does not include the Java code snippet icon.");
        require(siteScript.contains("\"kotlin\":{\"viewBox\""), "Generated script does not include the Kotlin code snippet icon.");
        require(siteScript.contains("\"groovy\":{\"viewBox\""), "Generated script does not include the Groovy code snippet icon.");
        require(siteScript.contains("\"maven\":{\"viewBox\""), "Generated script does not include the Maven dependency snippet icon.");
        require(siteScript.contains("\"properties\":{\"viewBox\""), "Generated script does not include the properties snippet icon.");
        require(!siteScript.contains("highlightGuideCode"), "Generated script still contains runtime syntax highlighting.");
        require(!siteScript.contains("docs-code-language-hint"), "Generated script still adds code sample language hints.");
        require(!siteCss.contains("docs-code-language-hint"), "Generated stylesheet still styles code sample language hints.");
        require(!siteCss.contains(".fa"), "Generated stylesheet still targets old Font Awesome guide icons.");
        require(!siteCss.contains("pre.highlight"), "Generated stylesheet still targets old Highlight.js pre classes.");
        require(!CODE_SAMPLE_IMPORTANT_STYLE.matcher(siteCss).find(), "Generated code sample styles still rely on !important.");
        verifyNoSeparateGeneratedGuidePages(outputDirectory, projects);
        getLogger().quiet("Verified generated platform docs page for {} projects.", projects.size());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    static Set<String> projectSections(String html, String slug) {
        Set<String> sections = new LinkedHashSet<>();
        String marker = "data-project-nav=\"" + slug + "\"";
        int markerIndex = html.indexOf(marker);
        if (markerIndex < 0) {
            return sections;
        }
        int start = html.lastIndexOf("<details class=\"project-section\"", markerIndex);
        if (start < 0) {
            start = markerIndex;
        }
        int end = html.indexOf("<details class=\"project-section\"", markerIndex + marker.length());
        if (end < 0) {
            end = html.length();
        }
        Matcher matcher = SECTION.matcher(html.substring(start, end));
        while (matcher.find()) {
            sections.add(matcher.group(1));
        }
        return sections;
    }

    private static void verifyNoSeparateGeneratedGuidePages(Path outputDirectory, List<GuideProject> projects) throws IOException {
        for (GuideProject project : projects) {
            Path generatedDocs = outputDirectory.resolve("assets").resolve(project.slug()).resolve("docs");
            if (!Files.isDirectory(generatedDocs)) {
                continue;
            }
            require(!Files.exists(generatedDocs.resolve("index.html")), "Generated docs copied a separate project index for " + project.slug());
            require(!Files.exists(generatedDocs.resolve("css")), "Generated docs copied old guide CSS for " + project.slug());
            require(!Files.exists(generatedDocs.resolve("js")), "Generated docs copied old guide JavaScript for " + project.slug());
            require(!Files.exists(generatedDocs.resolve("style")), "Generated docs copied old guide template fragments for " + project.slug());
            require(!Files.exists(generatedDocs.resolve("fonts")), "Generated docs copied old guide fonts for " + project.slug());
            Path guideDirectory = generatedDocs.resolve("guide");
            if (Files.isDirectory(guideDirectory)) {
                try (var stream = Files.walk(guideDirectory)) {
                    for (Path path : stream.filter(Files::isRegularFile).toList()) {
                        String relativePath = guideDirectory.relativize(path).toString().replace('\\', '/');
                        require(
                            relativePath.equals("configurationreference.html"),
                            "Generated docs copied a separate guide page for " + project.slug() + ": " + relativePath
                        );
                    }
                }
                Path configurationReference = guideDirectory.resolve("configurationreference.html");
                if (Files.isRegularFile(configurationReference)) {
                    String referenceHtml = Files.readString(configurationReference, StandardCharsets.UTF_8);
                    require(!referenceHtml.contains("../css/"), "Configuration reference still imports old guide CSS for " + project.slug());
                    require(!referenceHtml.contains("../js/"), "Configuration reference still imports old guide JavaScript for " + project.slug());
                    require(!referenceHtml.contains("initHighlightingOnLoad"), "Configuration reference still initializes old guide highlighting for " + project.slug());
                    require(!referenceHtml.contains("clipboard.js"), "Configuration reference still imports old clipboard integration for " + project.slug());
                }
            }
        }
    }
}
