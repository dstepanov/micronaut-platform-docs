package io.micronaut.docs;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the HTML fragments emitted by the documentation engine, filters and macros.
 *
 * <p>The renderer is intentionally separate from parsing so downstream projects can keep the
 * existing macro semantics while replacing the generated HTML.</p>
 */
final class PlatformGuideHtmlRenderer extends DefaultRenderer {
    private static final Pattern CALLOUT_MARKER = Pattern.compile("<([1-9][0-9]*)>");
    private static final Pattern DEPENDENCY_XML = Pattern.compile("&lt;/?(?:dependency|dependencyManagement|annotationProcessorPaths|groupId|artifactId)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEPENDENCY_GRADLE = Pattern.compile("\\b(?:api|implementation|compileOnly|runtimeOnly|annotationProcessor|testImplementation|ksp|kapt)\\s*\\(", Pattern.CASE_INSENSITIVE);

    @Override
    public String renderBuildDependency(BuildDependency dependency) {
        return renderDependency(new Dependency(
            "gradle",
            dependency.groupId(),
            dependency.artifactId(),
            dependency.version(),
            dependency.classifier(),
            dependency.gradleScope(),
            dependency.multilanguageCssClass(),
            dependency.title()
        ), true) + renderDependency(new Dependency(
            "maven",
            dependency.groupId(),
            dependency.artifactId(),
            dependency.version(),
            dependency.classifier(),
            dependency.mavenScope(),
            dependency.multilanguageCssClass(),
            ""
        ), true);
    }

    @Override
    public String renderGradleDependency(Dependency dependency) {
        return renderDependency(dependency, true);
    }

    @Override
    public String renderMavenDependency(Dependency dependency) {
        return renderDependency(dependency, true);
    }

    @Override
    public String renderConfigurationProperties(ConfigurationProperties configurationProperties) {
        StringBuilder html = new StringBuilder();
        String title = configurationProperties.title();
        boolean first = true;
        for (CodeSample sample : configurationProperties.samples()) {
            html.append(codeBlock(
                sample.blockCssClass("multi-language-sample"),
                first ? html(title) : null,
                first && title != null && !title.isBlank(),
                sample
            ));
            first = false;
        }
        return html.toString();
    }

    @Override
    public String renderLanguageSnippet(LanguageSnippet languageSnippet) {
        StringBuilder html = new StringBuilder();
        String title = languageSnippet.title();
        boolean first = true;
        for (CodeSample sample : languageSnippet.samples()) {
            html.append(codeBlock(
                sample.blockCssClass("multi-language-sample"),
                first ? html(title) : null,
                first && title != null && !title.isBlank(),
                sample
            ));
            first = false;
        }
        return html.toString();
    }

    @Override
    public String renderNote(String content) {
        return admonition("note", "Note", content);
    }

    @Override
    public String renderWarning(String content) {
        return admonition("warning", "Warning", content);
    }

    @Override
    public String renderHidden(String content) {
        return "<div class=\"hidden-block\" hidden>" + content + "</div>";
    }

    @Override
    public String renderBlockQuote(String content) {
        return "<div class=\"literalblock\"><div class=\"content\"><pre>" + content + "</pre></div></div>\n\n";
    }

    @Override
    public String renderCode(String content) {
        if (content.contains("class=\"code\"")) {
            return "@" + content + "@";
        }
        return "<code>" + content + "</code>";
    }

    private String renderDependency(Dependency dependency, boolean dependencySnippet) {
        String build = normalizeLanguage(dependency.build());
        String highlighterLanguage = "maven".equals(build) ? "xml" : "kotlin";
        String source = "maven".equals(build) ? mavenDependency(dependency) : gradleDependency(dependency);
        String classes = dependency.multilanguageCssClass();
        if (dependencySnippet) {
            classes = (classes == null || classes.isBlank() ? "" : classes + " ")
                + "docs-code-dependency-snippet docs-snippet-card-dependency";
        }
        return codeBlock(classes, html(dependency.title()), dependency.title() != null && !dependency.title().isBlank(), build, pre(build, highlighterLanguage, codeHtml(source)));
    }

    private static String gradleDependency(Dependency dependency) {
        StringBuilder code = new StringBuilder();
        code.append(dependency.scope())
            .append("(\"")
            .append(dependency.groupId())
            .append(":")
            .append(dependency.artifactId());
        if (dependency.version() != null || dependency.classifier() != null) {
            code.append(":");
        }
        if (dependency.version() != null) {
            code.append(dependency.version());
        }
        if (dependency.classifier() != null) {
            code.append(":").append(dependency.classifier());
        }
        return code.append("\")").toString();
    }

    private static String mavenDependency(Dependency dependency) {
        if ("annotationProcessor".equals(dependency.scope())) {
            return annotationProcessorDependency(dependency);
        }
        StringBuilder code = new StringBuilder()
            .append("<dependency>\n")
            .append("    <groupId>").append(dependency.groupId()).append("</groupId>\n")
            .append("    <artifactId>").append(dependency.artifactId()).append("</artifactId>");
        if (dependency.version() != null) {
            code.append("\n    <version>").append(dependency.version()).append("</version>");
        }
        if (!"compile".equals(dependency.scope())) {
            code.append("\n    <scope>").append(dependency.scope()).append("</scope>");
        }
        if (dependency.classifier() != null) {
            code.append("\n    <classifier>").append(dependency.classifier()).append("</classifier>");
        }
        return code.append("\n</dependency>").toString();
    }

    private static String annotationProcessorDependency(Dependency dependency) {
        StringBuilder code = new StringBuilder()
            .append("<annotationProcessorPaths>\n")
            .append("    <path>\n")
            .append("        <groupId>").append(dependency.groupId()).append("</groupId>\n")
            .append("        <artifactId>").append(dependency.artifactId()).append("</artifactId>");
        if (dependency.version() != null) {
            code.append("\n        <version>").append(dependency.version()).append("</version>");
        }
        if (dependency.classifier() != null) {
            code.append("\n        <classifier>").append(dependency.classifier()).append("</classifier>");
        }
        return code.append("\n    </path>\n</annotationProcessorPaths>").toString();
    }

    private static String codeBlock(String classes, String title, boolean includeTitle, CodeSample sample) {
        return codeBlock(
            classes,
            title,
            includeTitle,
            sample.language(),
            pre(sample.language(), sample.highlighterLanguage(), codeHtml(sample.source()))
        );
    }

    private static String codeBlock(String classes, String title, boolean includeTitle, String language, String pre) {
        String normalizedLanguage = normalizeLanguage(language);
        String blockClasses = classes == null || classes.isBlank()
            ? "listingblock docs-snippet-card docs-code-block"
            : "listingblock " + classes + " docs-snippet-card docs-code-block";
        if (normalizedLanguage.equals("properties") && !hasClass(blockClasses, "docs-code-properties-snippet")) {
            blockClasses += " docs-code-properties-snippet docs-snippet-card-properties";
        }
        if (isDependencySnippet(normalizedLanguage, pre) && !hasClass(blockClasses, "docs-code-dependency-snippet")) {
            blockClasses += " docs-code-dependency-snippet docs-snippet-card-dependency";
        }
        String dataLanguage = normalizedLanguage.isBlank() ? "" : " data-lang=\"" + attribute(normalizedLanguage) + "\"";
        String outsideTitleHtml = title == null || title.isBlank() || !includeTitle
            ? ""
            : "<div class=\"docs-snippet-card-title docs-code-title\">" + title + "</div>";
        String copyButton = "<button class=\"docs-snippet-card-action docs-code-copy\" type=\"button\" aria-label=\"Copy code\" title=\"Copy code\" data-copy-code>" + copyIcon() + "</button>";
        return """
            %s
            <div class="%s" data-code-block%s>
            %s
            <div class="content docs-snippet-card-content docs-code-content">
            %s
            </div>
            </div>
            """.formatted(outsideTitleHtml, blockClasses, dataLanguage, copyButton, pre);
    }

    private static String pre(String dataLanguage, String highlighterLanguage, String codeHtml) {
        String normalizedDataLanguage = normalizeLanguage(dataLanguage);
        String normalizedHighlighterLanguage = normalizeLanguage(highlighterLanguage);
        String languageClass = normalizedHighlighterLanguage.isBlank() ? "" : " class=\"language-" + attribute(normalizedHighlighterLanguage) + "\"";
        String dataLanguageAttribute = normalizedDataLanguage.isBlank() ? "" : " data-lang=\"" + attribute(normalizedDataLanguage) + "\"";
        return "<pre class=\"highlightjs highlight\"><code" + languageClass + dataLanguageAttribute + ">" + codeHtml + "</code></pre>";
    }

    private static boolean isDependencySnippet(String language, String pre) {
        if (language.equals("gradle") || language.equals("maven")) {
            return true;
        }
        return switch (language) {
            case "xml", "text", "plaintext" -> DEPENDENCY_XML.matcher(pre).find();
            case "groovy", "kotlin", "java" -> DEPENDENCY_GRADLE.matcher(pre).find();
            default -> DEPENDENCY_XML.matcher(pre).find() || DEPENDENCY_GRADLE.matcher(pre).find();
        };
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

    private static String admonition(String type, String title, String content) {
        return """
            <div class="admonitionblock docs-admonition-card %s">
            <table class="docs-admonition-card-layout">
            <tbody>
            <tr>
            <td class="icon docs-admonition-card-icon"><i class="fa icon-%s" title="%s"></i></td>
            <td class="content docs-admonition-card-content">%s</td>
            </tr>
            </tbody>
            </table>
            </div>
            """.formatted(attribute(type), attribute(type), attribute(title), content);
    }

    private static String copyIcon() {
        return """
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"></rect><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"></path></svg><span class="visually-hidden">Copy code</span>\
            """;
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

    private static String codeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        Matcher matcher = CALLOUT_MARKER.matcher(value);
        StringBuilder result = new StringBuilder(value.length() + 32);
        int position = 0;
        while (matcher.find()) {
            result.append(html(value.substring(position, matcher.start())))
                .append("<i class=\"conum\" data-value=\"")
                .append(attribute(matcher.group(1)))
                .append("\"></i>");
            position = matcher.end();
        }
        result.append(html(value.substring(position)));
        return result.toString();
    }

    private static String attribute(String value) {
        return html(value).replace("\"", "&quot;");
    }
}
