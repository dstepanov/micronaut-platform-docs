package io.micronaut.docs;

import io.micronaut.docs.asciidoc.AsciiDocEngine;
import io.micronaut.docs.internal.FileResourceChecker;
import io.micronaut.docs.internal.UserGuideNode;
import io.micronaut.docs.internal.YamlTocStrategy;
import org.asciidoctor.Attributes;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.SafeMode;
import org.radeox.engine.context.BaseInitialRenderContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ModernGuideRenderer {
    private static final Pattern LISTING_BLOCK = Pattern.compile(
        "<div class=\"listingblock([^\"]*)\">\\s*(?:<div class=\"title\">(.*?)</div>\\s*)?<div class=\"content\">\\s*(<pre[^>]*>\\s*<code([^>]*)>.*?</code>\\s*</pre>)\\s*</div>\\s*</div>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern LITERAL_INLINE_CODE_BLOCK = Pattern.compile(
        "<div class=\"literalblock\">\\s*<div class=\"content\">\\s*<pre>\\s*`([^`\\r\\n]+)`\\s*</pre>\\s*</div>\\s*</div>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern PARAGRAPH_START = Pattern.compile("<div class=\"paragraph\">\\s*<p>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAGRAPH_END = Pattern.compile("\\s*</div>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_DATA_LANGUAGE = Pattern.compile("\\bdata-lang=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_LANGUAGE_CLASS = Pattern.compile("\\blanguage-([a-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEPENDENCY_XML = Pattern.compile("&lt;/?(?:dependency|dependencyManagement|annotationProcessorPaths|groupId|artifactId)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEPENDENCY_GRADLE = Pattern.compile("\\b(?:api|implementation|compileOnly|runtimeOnly|annotationProcessor|testImplementation|ksp|kapt)\\s*\\(", Pattern.CASE_INSENSITIVE);

    private final Path projectDirectory;
    private final GuideProject project;
    private final String platformVersion;

    ModernGuideRenderer(Path projectDirectory, GuideProject project, String platformVersion) {
        this.projectDirectory = projectDirectory;
        this.project = project;
        this.platformVersion = platformVersion == null ? "" : platformVersion;
    }

    String render() throws IOException {
        Path submoduleDirectory = projectDirectory.resolve(project.submodulePath()).toAbsolutePath().normalize();
        Path sourceDocs = submoduleDirectory.resolve("src/main/docs");
        Path guideSource = sourceDocs.resolve("guide");
        Path tocFile = guideSource.resolve("toc.yml");
        if (!Files.isDirectory(sourceDocs)) {
            throw new IOException("Missing docs source directory for " + project.displayName() + ": " + sourceDocs);
        }
        if (!Files.isRegularFile(tocFile)) {
            throw new IOException("Missing TOC YAML for " + project.displayName() + ": " + tocFile);
        }

        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", submoduleDirectory.toString());
        try {
            Map<String, Object> attributes = renderAttributes(submoduleDirectory, sourceDocs);
            BaseInitialRenderContext context = renderContext(sourceDocs);
            AsciiDocEngine engine = new AsciiDocEngine(context);
            engine.setEngineProperties(engineProperties(attributes));
            putEngineAttributes(engine, attributes);
            context.setRenderEngine(engine);

            UserGuideNode guide = new YamlTocStrategy(new FileResourceChecker(guideSource.toFile()), ".adoc").generateToc(tocFile.toFile());
            StringBuilder content = new StringBuilder(1024 * 64);
            content.append("""
                <!doctype html>
                <html lang="en">
                <head><title>%s %s</title></head>
                <body>
                <div class="docs-content">
                <div class="project">
                    <h1>%s</h1>
                    <p><strong>Version:</strong> %s</p>
                </div>
                """.formatted(
                html(project.displayName()),
                html(platformVersion),
                html(project.displayName()),
                html(platformVersion)
            ));
            try {
                List<UserGuideNode> chapters = children(guide);
                for (int i = 0; i < chapters.size(); i++) {
                    appendGuideNode(content, engine, context, attributes, submoduleDirectory, guideSource, chapters.get(i), 0, Integer.toString(i + 1));
                }
            } catch (RuntimeException e) {
                throw new IOException("Failed to render " + project.displayName() + " guide with the Micronaut docs engine.", e);
            }
            content.append("""
                </div>
                </body>
                </html>
                """);
            return content.toString();
        } finally {
            if (previousUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", previousUserDir);
            }
        }
    }

    private Map<String, Object> renderAttributes(Path submoduleDirectory, Path sourceDocs) throws IOException {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.putAll(readProjectProperties(submoduleDirectory.resolve("gradle.properties")));
        attributes.put("title", project.displayName());
        attributes.put("version", platformVersion);
        attributes.put("safe", "UNSAFE");
        attributes.put("imagesdir", "../img");
        attributes.put("icons", "font");
        attributes.put("source-highlighter", "highlightjs");
        attributes.put("sourcedir", submoduleDirectory.toString());
        attributes.put("sourceDir", submoduleDirectory.toString());
        attributes.put("includedir", trailingSlash(submoduleDirectory.resolve("build/working/01-includes")));
        attributes.put("testsuitejava", submoduleDirectory.resolve("test-suite/src/test/java/io/micronaut/docs").toString());
        attributes.put("testsuitegroovy", submoduleDirectory.resolve("test-suite-groovy/src/test/groovy/io/micronaut/docs").toString());
        attributes.put("testsuitekotlin", submoduleDirectory.resolve("test-suite-kotlin/src/test/kotlin/io/micronaut/docs").toString());
        attributes.put("sourceRepo", sourceDocsEditUrl());
        attributes.put("docdir", sourceDocs.toString());
        return attributes;
    }

    private static Map<String, Object> readProjectProperties(Path file) throws IOException {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return attributes;
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        }
        for (String name : properties.stringPropertyNames()) {
            attributes.put(name, properties.getProperty(name));
        }
        return attributes;
    }

    private BaseInitialRenderContext renderContext(Path sourceDocs) {
        BaseInitialRenderContext context = new BaseInitialRenderContext();
        context.set("contextPath", "..");
        context.set("base.dir", sourceDocs.toString());
        context.set("apiBasePath", projectDirectory.resolve(project.generatedDocsPath()).toAbsolutePath().normalize().toString());
        context.set("apiContextPath", "..");
        context.set("resourcesContextPath", "..");
        return context;
    }

    private static Properties engineProperties(Map<String, Object> attributes) {
        Properties properties = new Properties();
        attributes.forEach((key, value) -> properties.setProperty(key, String.valueOf(value)));
        return properties;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void putEngineAttributes(AsciiDocEngine engine, Map<String, Object> attributes) {
        engine.getAttributes().putAll(attributes);
    }

    private void appendGuideNode(
        StringBuilder content,
        AsciiDocEngine engine,
        BaseInitialRenderContext context,
        Map<String, Object> attributes,
        Path baseDir,
        Path guideSource,
        UserGuideNode node,
        int level,
        String sectionNumber
    ) throws IOException {
        Path sourceFile = guideSource.resolve(node.getFile()).normalize();
        if (!sourceFile.startsWith(guideSource) || !Files.isRegularFile(sourceFile)) {
            throw new IOException("Missing guide source file for " + project.displayName() + ": " + sourceFile);
        }
        content.append(sectionHeading(node, level, sectionNumber))
            .append(contributeButton(node.getFile()));

        context.set("sourceFile", sourceFile.toFile());
        content.append(renderAsciiDoc(engine, Files.readString(sourceFile, StandardCharsets.UTF_8), attributes, baseDir)).append('\n');

        List<UserGuideNode> childNodes = children(node);
        for (int i = 0; i < childNodes.size(); i++) {
            appendGuideNode(content, engine, context, attributes, baseDir, guideSource, childNodes.get(i), level + 1, sectionNumber + "." + (i + 1));
        }
    }

    private static String renderAsciiDoc(AsciiDocEngine engine, String source, Map<String, Object> attributes, Path baseDir) {
        AttributesBuilder attributesBuilder = Attributes.builder();
        attributes.forEach(attributesBuilder::attribute);
        OptionsBuilder optionsBuilder = Options.builder()
            .standalone(false)
            .baseDir(baseDir.toFile())
            .attributes(attributesBuilder.build());
        Object safe = attributes.get("safe");
        if (safe != null) {
            optionsBuilder.safe(SafeMode.valueOf(String.valueOf(safe)));
        }
        String html = engine.getAsciidoctor().convert(source, optionsBuilder.build());
        return unwrapCodeBlockParagraphs(modernizeLiteralInlineCodeBlocks(modernizeCodeBlocks(html)));
    }

    private static String modernizeCodeBlocks(String html) {
        Matcher matcher = LISTING_BLOCK.matcher(html);
        StringBuilder result = new StringBuilder(html.length() + 256);
        String previousMultiLanguageTitle = null;
        while (matcher.find()) {
            String classes = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (classes.contains("docs-code-block")) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String title = matcher.group(2);
            String pre = matcher.group(3);
            boolean multiLanguageSample = hasClass(classes, "multi-language-sample");
            boolean includeTitle = title != null && !title.isBlank();
            if (multiLanguageSample) {
                includeTitle = includeTitle && !title.equals(previousMultiLanguageTitle);
                previousMultiLanguageTitle = title;
            } else {
                previousMultiLanguageTitle = null;
            }
            String replacement = codeBlock(classes, title, includeTitle, pre);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String modernizeLiteralInlineCodeBlocks(String html) {
        Matcher matcher = LITERAL_INLINE_CODE_BLOCK.matcher(html);
        StringBuilder result = new StringBuilder(html.length() + 256);
        while (matcher.find()) {
            String code = matcher.group(1).trim();
            String languageAttributes = looksLikeJava(code) ? " class=\"language-java\" data-lang=\"java\"" : "";
            String replacement = codeBlock("", null, false, "<pre><code" + languageAttributes + ">" + code + "</code></pre>");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean looksLikeJava(String code) {
        return code.startsWith("@")
            || code.contains(" public ")
            || code.startsWith("public ")
            || code.startsWith("protected ")
            || code.startsWith("private ")
            || code.startsWith("class ")
            || code.startsWith("interface ")
            || code.startsWith("enum ")
            || code.startsWith("record ");
    }

    private static String unwrapCodeBlockParagraphs(String html) {
        Matcher matcher = PARAGRAPH_START.matcher(html);
        StringBuilder result = new StringBuilder(html.length());
        int position = 0;
        while (matcher.find(position)) {
            int paragraphStart = matcher.start();
            int contentStart = matcher.end();
            int paragraphEnd = html.indexOf("</p>", contentStart);
            if (paragraphEnd < 0) {
                break;
            }
            int wrapperEnd = paragraphWrapperEnd(html, paragraphEnd + "</p>".length());
            if (wrapperEnd < 0) {
                result.append(html, position, matcher.end());
                position = matcher.end();
                continue;
            }

            String paragraphContent = html.substring(contentStart, paragraphEnd);
            String trimmedContent = paragraphContent.trim();
            if (trimmedContent.startsWith("<div class=\"listingblock") && trimmedContent.contains("docs-code-block")) {
                result.append(html, position, paragraphStart)
                    .append(trimmedContent)
                    .append('\n');
                position = wrapperEnd;
            } else {
                result.append(html, position, matcher.end());
                position = matcher.end();
            }
        }
        result.append(html, position, html.length());
        return result.toString();
    }

    private static int paragraphWrapperEnd(String html, int start) {
        Matcher matcher = PARAGRAPH_END.matcher(html);
        matcher.region(start, html.length());
        if (!matcher.lookingAt()) {
            return -1;
        }
        return matcher.end();
    }

    private static String codeBlock(String classes, String title, boolean includeTitle, String pre) {
        String blockClasses = classes == null || classes.isBlank()
            ? "listingblock docs-code-block"
            : "listingblock " + classes + " docs-code-block";
        if (isDependencySnippet(pre)) {
            blockClasses += " docs-code-dependency-snippet";
        }
        String outsideTitleHtml = title == null || title.isBlank() || !includeTitle
            ? ""
            : "<div class=\"docs-code-title\">" + title + "</div>";
        String copyButton = "<button class=\"docs-code-copy\" type=\"button\" aria-label=\"Copy code\" title=\"Copy code\" data-copy-code>" + copyIcon() + "</button>";
        return """
            %s
            <div class="%s" data-code-block>
            %s
            <div class="content docs-code-content">
            %s
            </div>
            </div>
            """.formatted(outsideTitleHtml, blockClasses, copyButton, pre);
    }

    private static boolean isDependencySnippet(String pre) {
        String language = codeLanguage(pre);
        if (language.equals("gradle") || language.equals("maven")) {
            return true;
        }
        return switch (language) {
            case "xml", "text", "plaintext" -> DEPENDENCY_XML.matcher(pre).find();
            case "groovy", "kotlin", "java" -> DEPENDENCY_GRADLE.matcher(pre).find();
            default -> DEPENDENCY_XML.matcher(pre).find() || DEPENDENCY_GRADLE.matcher(pre).find();
        };
    }

    private static String codeLanguage(String pre) {
        Matcher dataLanguage = CODE_DATA_LANGUAGE.matcher(pre);
        if (dataLanguage.find()) {
            return normalizeLanguage(dataLanguage.group(1));
        }
        Matcher languageClass = CODE_LANGUAGE_CLASS.matcher(pre);
        if (languageClass.find()) {
            return normalizeLanguage(languageClass.group(1));
        }
        return "";
    }

    private static String normalizeLanguage(String language) {
        String normalized = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "gradle-groovy", "gradle-kotlin" -> "gradle";
            case "pom" -> "maven";
            case "props", "property" -> "properties";
            case "txt" -> "text";
            default -> normalized;
        };
    }

    private static boolean hasClass(String classes, String cssClass) {
        if (classes == null || classes.isBlank()) {
            return false;
        }
        for (String className : classes.split("\\s+")) {
            if (className.equals(cssClass)) {
                return true;
            }
        }
        return false;
    }

    private static String sectionHeading(UserGuideNode node, int level, String sectionNumber) {
        int headingLevel = level == 0 ? 1 : 2;
        String id = attribute(node.getName());
        return "<h" + headingLevel + " id=\"" + id + "\"><a class=\"anchor\" href=\"#" + id + "\"></a>"
            + html(sectionNumber) + " " + html(node.getTitle()) + "</h" + headingLevel + ">\n\n";
    }

    private String contributeButton(String sourcePath) {
        return """
            <div class="contribute-btn">
                <button type="button" class="btn btn-default" onclick="window.location.href=&quot;%s/guide/%s&quot;">
                    <svg class="button-icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
                        <path d="M12 20h9"></path>
                        <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"></path>
                    </svg>
                    <span>Improve this doc</span>
                </button>
            </div>

            """.formatted(attribute(sourceDocsEditUrl()), attribute(sourcePath));
    }

    private String sourceDocsEditUrl() {
        String branch = project.branch().isBlank() ? "HEAD" : project.branch();
        return project.repositoryUrl().replaceAll("\\.git$", "") + "/edit/" + branch + "/src/main/docs";
    }

    private static List<UserGuideNode> children(UserGuideNode node) {
        if (node == null || node.getChildren() == null) {
            return List.of();
        }
        List<UserGuideNode> result = new ArrayList<>();
        for (Object child : node.getChildren()) {
            result.add((UserGuideNode) child);
        }
        return result;
    }

    private static String copyIcon() {
        return """
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"></rect><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"></path></svg><span class="visually-hidden">Copy code</span>\
            """;
    }

    private static String trailingSlash(Path path) {
        return path.toString() + File.separator;
    }

    private static String html(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static String attribute(String value) {
        return html(value).replace("\"", "&quot;");
    }
}
