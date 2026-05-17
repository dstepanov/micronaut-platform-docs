package io.micronaut.docs;

import io.micronaut.docs.asciidoc.AsciiDocEngine;
import org.radeox.engine.context.BaseInitialRenderContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ModernGuideRenderer {
    private static final Pattern INDENTED_INLINE_CODE_BLOCK = Pattern.compile("(?m)^\\s{4,}`([^`\\r\\n]+)`\\s*$");
    private static final Pattern SNIPPET_INDENT_TITLE_SEPARATOR = Pattern.compile(
        "(snippet::[^\\[]+\\[[^\\]]*?\\bindent\\s*=\\s*-?\\d+)\\s+(title\\s*=)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FALSE_INDENT_ATTRIBUTE = Pattern.compile(
        "(?m)^([ \\t]*(?:include|snippet)::[^\\r\\n\\[]+\\[[^\\r\\n\\]]*?\\bindent\\s*=\\s*)(?:\"false\"|'false'|false)(?=\\s*(?:,|\\]))",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARAGRAPH_START = Pattern.compile("<div class=\"paragraph\">\\s*<p>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAGRAPH_END = Pattern.compile("\\s*</div>", Pattern.CASE_INSENSITIVE);

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
            BaseInitialRenderContext context = renderContext(submoduleDirectory);
            AsciiDocEngine engine = new AsciiDocEngine(context);
            PlatformGuideHtmlRenderer renderer = new PlatformGuideHtmlRenderer();
            engine.setRenderer(renderer);
            engine.setEngineProperties(engineProperties(attributes));
            putEngineAttributes(engine, attributes);
            removeLegacyEngineAttributes(engine);
            context.setRenderEngine(engine);

            GuideToc guide = GuideToc.readSource(guideSource);
            StringBuilder content = new StringBuilder(1024 * 64);
            content.append("""
                <!doctype html>
                <html lang="en">
                <head><title>%s %s</title></head>
                <body>
                <div class="docs-content">
                <div class="project">
                    <h1>%s</h1>
                </div>
                """.formatted(
                html(project.displayName()),
                html(platformVersion),
                html(project.displayName())
            ));
            try {
                for (GuideToc.Node chapter : guide.children()) {
                    appendGuideNode(content, engine, context, guideSource, chapter);
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
        attributes.put("sourcedir", submoduleDirectory.toString());
        attributes.put("sourceDir", submoduleDirectory.toString());
        attributes.put("includedir", trailingSlash(submoduleDirectory.resolve("build/working/01-includes")));
        attributes.put("testsuitejava", submoduleDirectory.resolve("test-suite/src/test/java/io/micronaut/docs").toString());
        attributes.put("testsuitegroovy", submoduleDirectory.resolve("test-suite-groovy/src/test/groovy/io/micronaut/docs").toString());
        attributes.put("testsuitekotlin", submoduleDirectory.resolve("test-suite-kotlin/src/test/kotlin/io/micronaut/docs").toString());
        attributes.put("sourceRepo", sourceDocsEditUrl());
        attributes.put("docdir", submoduleDirectory.toString());
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

    private BaseInitialRenderContext renderContext(Path submoduleDirectory) {
        BaseInitialRenderContext context = new BaseInitialRenderContext();
        context.set("contextPath", "..");
        context.set("base.dir", submoduleDirectory.toString());
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

    private static void removeLegacyEngineAttributes(AsciiDocEngine engine) {
        engine.getAttributes().remove("icons");
        engine.getAttributes().remove("source-highlighter");
    }

    private void appendGuideNode(
        StringBuilder content,
        AsciiDocEngine engine,
        BaseInitialRenderContext context,
        Path guideSource,
        GuideToc.Node node
    ) throws IOException {
        if (node.file() == null || node.file().isBlank()) {
            throw new IOException("Missing guide source file for " + project.displayName() + " TOC section: " + node.id());
        }
        Path sourceFile = guideSource.resolve(node.file()).normalize();
        if (!sourceFile.startsWith(guideSource) || !Files.isRegularFile(sourceFile)) {
            throw new IOException("Missing guide source file for " + project.displayName() + ": " + sourceFile);
        }
        content.append(sectionHeading(node));

        context.set("sourceFile", sourceFile.toFile());
        try {
            content.append(renderAsciiDoc(engine, context, Files.readString(sourceFile, StandardCharsets.UTF_8))).append('\n');
        } catch (RuntimeException e) {
            throw new IOException("Failed to render " + project.displayName() + " guide source: " + sourceFile
                + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")", e);
        }

        for (GuideToc.Node childNode : node.children()) {
            appendGuideNode(content, engine, context, guideSource, childNode);
        }
    }

    private static String renderAsciiDoc(
        AsciiDocEngine engine,
        BaseInitialRenderContext context,
        String source
    ) {
        String html = engine.render(normalizeAsciiDocSource(source), context);
        return unwrapCodeBlockParagraphs(html);
    }

    static String normalizeAsciiDocSource(String source) {
        String normalized = normalizeFalseIndentAttributes(source);
        normalized = SNIPPET_INDENT_TITLE_SEPARATOR.matcher(normalized).replaceAll("$1, $2");
        Matcher matcher = INDENTED_INLINE_CODE_BLOCK.matcher(normalized);
        StringBuilder result = new StringBuilder(normalized.length());
        while (matcher.find()) {
            String code = matcher.group(1).trim();
            if (!looksLikeJava(code)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement("""

                [source,java]
                ----
                %s
                ----

                """.formatted(code)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String normalizeFalseIndentAttributes(String source) {
        Matcher matcher = FALSE_INDENT_ATTRIBUTE.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + "0"));
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

    private String sectionHeading(GuideToc.Node node) {
        int headingLevel = node.level() == 0 ? 1 : 2;
        String id = attribute(node.id());
        return """
            <div class="guide-section-heading">
                <h%s id="%s"><a class="anchor" href="#%s"></a>%s %s</h%s>
                <a class="contribute-btn" href="%s/guide/%s" title="Improve this doc" aria-label="Improve this doc">
                    <svg class="button-icon" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
                        <path d="M12 20h9"></path>
                        <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"></path>
                    </svg>
                </a>
            </div>

            """.formatted(
            headingLevel,
            id,
            id,
            html(node.number()),
            html(node.title()),
            headingLevel,
            attribute(sourceDocsEditUrl()),
            attribute(node.file())
        );
    }

    private String sourceDocsEditUrl() {
        String branch = project.branch().isBlank() ? "HEAD" : project.branch();
        return project.repositoryUrl().replaceAll("\\.git$", "") + "/edit/" + branch + "/src/main/docs";
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
