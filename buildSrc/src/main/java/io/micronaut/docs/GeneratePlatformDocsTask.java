package io.micronaut.docs;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class GeneratePlatformDocsTask extends DefaultTask {
    private static final String TEMPLATE_ROOT = "/io/micronaut/docs/templates";
    private static final String SITE_ASSET_PATH = "platform-assets";
    private static final String OVERVIEW_SECTION_ID = "platform";
    private static final String CATEGORY_COUNT = "category.count";
    private static final int CARD_DESCRIPTION_MIN_WORDS = 24;
    private static final int CARD_DESCRIPTION_MAX_WORDS = 30;
    private static final String SITE_CSS_RESOURCE = "/io/micronaut/docs/assets/site.css";
    private static final String LOGO_BLACK_RESOURCE = "/io/micronaut/docs/assets/logos/micronaut-horizontal-black.svg";
    private static final String LOGO_WHITE_RESOURCE = "/io/micronaut/docs/assets/logos/micronaut-horizontal-white.svg";
    private static final String MICRONAUT_SALLY_RESOURCE = "/io/micronaut/docs/assets/icons/micronaut-sally.svg";
    private static final String BRAND_ICON_RESOURCE_ROOT = "/io/micronaut/docs/assets/icons/brands/";
    private static final String LANGUAGE_ICON_RESOURCE_ROOT = "/io/micronaut/docs/assets/icons/languages/";
    private static final String LUCIDE_PROPERTIES_RESOURCE = "META-INF/maven/org.webjars.npm/lucide-static/pom.properties";
    private static final String LUCIDE_ICON_ROOT = "META-INF/resources/webjars/lucide-static/%s/icons/";
    private static final Set<String> CONTENT_SEARCH_STOP_WORDS = Set.of(
        "about", "above", "after", "again", "against", "also", "another", "because", "before", "being", "below",
        "between", "cannot", "could", "does", "doing", "during", "each", "either", "example", "first", "following",
        "from", "have", "into", "more", "most", "must", "only", "other", "over", "same", "should", "some", "such",
        "than", "that", "their", "then", "there", "these", "this", "those", "through", "using", "when", "where",
        "which", "while", "with", "would", "your"
    );
    private static final String EMBEDDED_REFERENCE_STYLE_RESOURCE = "/io/micronaut/docs/assets/embedded-reference.css";
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern VERSION = Pattern.compile("<strong>\\s*Version:\\s*</strong>\\s*([^<\\r\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIV_TAG = Pattern.compile("<(/?)div\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ID_ATTRIBUTE = Pattern.compile("\\bid\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_ATTRIBUTE = Pattern.compile("\\b(href|src)\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAGRAPH_TAG = Pattern.compile("<p\\b[^>]*>(.*?)</p>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONFIGURATION_PROPERTY_ROW = Pattern.compile("<tr(\\b[^>]*)>\\s*(<td\\b[^>]*>\\s*<p\\b[^>]*>\\s*<code>(.*?)</code>\\s*</p>\\s*</td>)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OLD_GUIDE_STYLESHEET_LINK = Pattern.compile(
        "\\s*<link\\b(?=[^>]*\\bhref=\"\\.\\./css/)[^>]*>\\s*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OLD_GUIDE_SCRIPT = Pattern.compile(
        "\\s*<script\\b(?=[^>]*\\bsrc=\"(?:\\.\\./js/|https://cdnjs\\.cloudflare\\.com/ajax/libs/clipboard\\.js/))[^>]*>\\s*</script>\\s*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OLD_GUIDE_INLINE_HIGHLIGHT_SCRIPT = Pattern.compile(
        "\\s*<script>\\s*hljs\\.initHighlightingOnLoad\\(\\);\\s*</script>\\s*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JAVADOC_SEARCH_OBJECT = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern JAVADOC_SEARCH_FIELD = Pattern.compile("\"([plu])\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern SVG_TAG = Pattern.compile("<svg\\b([^>]*)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SVG_CLASS = Pattern.compile("\\bclass\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_VIEW_BOX = Pattern.compile("\\bviewBox\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_TITLE = Pattern.compile("<title\\b[^>]*>.*?</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SVG_ROLE = Pattern.compile("\\s+role\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_ARIA_HIDDEN = Pattern.compile("\\s+aria-hidden\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern SVG_FOCUSABLE = Pattern.compile("\\s+focusable\\s*=\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE);

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPlatformVersionCatalog();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getDescriptionCatalog();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getIconCatalog();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCategoryCatalog();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    @Optional
    public abstract Property<String> getProjectSlugs();

    @TaskAction
    public void generate() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
        List<GuideProject> projects = GuideProject.selectBySlugs(
            GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath()),
            getProjectSlugs().getOrElse("")
        );
        getLogger().quiet("Generating platform docs site for {} projects at {}.", projects.size(), outputDirectory);

        getLogger().quiet("Cleaning output directory: {}", outputDirectory);
        deleteDirectory(outputDirectory);
        Files.createDirectories(outputDirectory);

        Map<String, String> platformVersions = PlatformVersions.read(getPlatformVersionCatalog().get().getAsFile().toPath());
        Properties descriptions = readDescriptionCatalog();
        Properties projectIcons = readIconCatalog();
        List<ProjectCategory> categories = readCategoryCatalog();
        List<GuideDocument> documents = new ArrayList<>();
        int index = 0;
        for (GuideProject project : projects) {
            getLogger().quiet("[{}/{}] Rendering {}.", ++index, projects.size(), project.displayName());
            if (copyGeneratedReferenceDocs(projectDirectory, outputDirectory, project)) {
                getLogger().quiet("[{}/{}] Copied {} API and configuration reference output.", index, projects.size(), project.displayName());
            } else {
                getLogger().quiet("[{}/{}] No generated reference output found for {}; guide content will still be rendered from sources.", index, projects.size(), project.displayName());
            }
            String platformVersion = platformVersions.get(project.platformVersionKey());
            String html = readOrRenderProjectGuideHtml(projectDirectory, project, platformVersion, index, projects.size());
            documents.add(parseGuide(project, html, projectDirectory.resolve(project.tocPath()), platformVersions.get(project.platformVersionKey())));
        }

        getLogger().quiet("Writing platform UI assets.");
        writeSiteAssets(outputDirectory, projectDirectory, documents, descriptions, projectIcons, categories);
        Files.writeString(outputDirectory.resolve("index.html"), renderTemplate("index", siteModel(projectDirectory, documents, descriptions, projectIcons, categories)), StandardCharsets.UTF_8);
        getLogger().quiet("Generated platform docs site: {}", outputDirectory.resolve("index.html"));
    }

    private Properties readDescriptionCatalog() throws IOException {
        Properties properties = new Properties();
        if (!getDescriptionCatalog().isPresent()) {
            return properties;
        }
        Path catalog = getDescriptionCatalog().get().getAsFile().toPath();
        if (!Files.isRegularFile(catalog)) {
            return properties;
        }
        getLogger().quiet("Reading platform docs descriptions from {}.", catalog);
        try (InputStream input = Files.newInputStream(catalog)) {
            properties.load(input);
        }
        return properties;
    }

    private Properties readIconCatalog() throws IOException {
        Properties properties = new Properties();
        if (!getIconCatalog().isPresent()) {
            return properties;
        }
        Path catalog = getIconCatalog().get().getAsFile().toPath();
        if (!Files.isRegularFile(catalog)) {
            return properties;
        }
        getLogger().quiet("Reading platform docs icons from {}.", catalog);
        try (InputStream input = Files.newInputStream(catalog)) {
            properties.load(input);
        }
        return properties;
    }

    private List<ProjectCategory> readCategoryCatalog() throws IOException {
        if (!getCategoryCatalog().isPresent()) {
            return defaultProjectCategories();
        }
        Path catalog = getCategoryCatalog().get().getAsFile().toPath();
        if (!Files.isRegularFile(catalog)) {
            return defaultProjectCategories();
        }

        getLogger().quiet("Reading platform docs categories from {}.", catalog);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(catalog)) {
            properties.load(input);
        }

        int count = Integer.parseInt(properties.getProperty(CATEGORY_COUNT, "0"));
        List<ProjectCategory> categories = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "category." + i + ".";
            String name = requiredCategoryProperty(properties, prefix + "name");
            categories.add(new ProjectCategory(
                properties.getProperty(prefix + "slug", categorySlug(name)),
                name,
                properties.getProperty(prefix + "icon", "book-open").trim(),
                properties.getProperty(prefix + "description", "").trim(),
                categoryProjects(properties.getProperty(prefix + "projects", ""))
            ));
        }
        return categories.isEmpty() ? defaultProjectCategories() : categories;
    }

    private static String requiredCategoryProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Platform docs category catalog is missing " + key);
        }
        return value.trim();
    }

    private static Set<String> categoryProjects(String value) {
        Set<String> projects = new LinkedHashSet<>();
        for (String project : value.split(",")) {
            String slug = project.trim();
            if (!slug.isBlank()) {
                projects.add(slug);
            }
        }
        return projects;
    }

    private static List<ProjectCategory> defaultProjectCategories() {
        return List.of(
            new ProjectCategory("most-popular", "Most Popular", "book-open", "", Set.of("core", "data", "security", "graphql", "spring", "kafka", "openapi")),
            new ProjectCategory("api", "API", "route", "", Set.of("grpc", "guice", "jackson-xml", "jaxrs", "json-schema", "serde", "servlet")),
            new ProjectCategory("build", "Build", "code", "", Set.of("aot", "gradle", "sourcegen", "openrewrite")),
            new ProjectCategory("cloud", "Cloud", "cloud", "", Set.of("aws", "azure", "discovery-client", "gcp", "kubernetes", "object-storage", "oracle-cloud")),
            new ProjectCategory("configuration", "Configuration", "sliders-horizontal", "", Set.of("logging", "toml")),
            new ProjectCategory("data-access", "Data Access", "database", "", Set.of("cassandra", "coherence", "eclipsestore", "mongodb", "neo4j", "r2dbc", "redis", "sql")),
            new ProjectCategory("database-migration", "Database Migration", "database-zap", "", Set.of("flyway", "liquibase")),
            new ProjectCategory("errors", "Errors", "braces", "", Set.of("problem-json")),
            new ProjectCategory("languages", "Languages", "code", "", Set.of("groovy", "kotlin", "graal-languages")),
            new ProjectCategory("messaging", "Messaging", "message-square", "", Set.of("jms", "mqtt", "nats", "pulsar", "rabbitmq")),
            new ProjectCategory("misc", "Misc", "boxes", "", Set.of("acme", "cache", "chatbots", "email", "picocli", "session")),
            new ProjectCategory("reactive", "Reactive", "workflow", "", Set.of("reactor", "rxjava3")),
            new ProjectCategory("views", "Views", "layout-template", "", Set.of("multitenancy", "rss", "views")),
            new ProjectCategory("dev-and-test", "Dev & Test", "test-tube-2", "", Set.of("test", "test-resources", "control-panel")),
            new ProjectCategory("ai", "AI", "bot", "", Set.of("langchain4j", "mcp")),
            new ProjectCategory("validation", "Validation", "check-circle", "", Set.of("hibernate-validator", "validation")),
            new ProjectCategory("other", "Other", "folder-git-2", "", Set.of())
        );
    }

    private static GuideDocument parseGuide(GuideProject project, String html, Path tocFile, String platformVersion) throws IOException {
        String title = firstMatch(TITLE, html)
            .map(GeneratePlatformDocsTask::stripTags)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .orElse(project.displayName());
        String generatedVersion = firstMatch(VERSION, html)
            .map(GeneratePlatformDocsTask::stripTags)
            .map(String::trim)
            .orElse("");
        String version = platformVersion == null || platformVersion.isBlank() ? generatedVersion : platformVersion;
        List<TocItem> tocItems = readTocItems(project, tocFile);
        String content = extractDocsContent(html);
        content = SCRIPT_TAG.matcher(content).replaceAll("");
        content = prefixIds(content, project.slug());
        content = rewriteUrls(content, project);
        content = optimizeImages(content);
        content = withProjectAnchor(project, content);
        tocItems = existingTocItems(project, tocItems, content);
        return new GuideDocument(project, title, version, tocItems, content);
    }

    private static String withProjectAnchor(GuideProject project, String content) {
        String anchor = projectAnchor(project);
        if (content.contains("id=\"" + anchor + "\"")) {
            return content;
        }
        return "<span class=\"project-document-anchor\" id=\"" + anchor + "\" aria-hidden=\"true\"></span>\n" + content;
    }

    private static String projectAnchor(GuideProject project) {
        return project.slug() + "-docs";
    }

    private static List<TocItem> existingTocItems(GuideProject project, List<TocItem> tocItems, String content) {
        if (tocItems.isEmpty()) {
            return List.of(projectTocItem(project));
        }
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = ID_ATTRIBUTE.matcher(content);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        List<TocItem> existing = tocItems.stream()
            .filter(item -> ids.contains(item.prefixedId()))
            .toList();
        return existing.isEmpty() ? List.of(projectTocItem(project)) : existing;
    }

    private static TocItem projectTocItem(GuideProject project) {
        return new TocItem(0, "", "Docs", "docs", projectAnchor(project));
    }

    private static boolean copyGeneratedReferenceDocs(Path projectDirectory, Path outputDirectory, GuideProject project) throws IOException {
        Path sourceDirectory = projectDirectory.resolve(project.generatedDocsPath());
        Path targetDirectory = outputDirectory.resolve("assets").resolve(project.slug()).resolve("docs");
        return GeneratedReferenceDocs.copyReferenceDocs(sourceDirectory, targetDirectory, new GeneratedReferenceDocs.FileTransformer() {
            @Override
            public boolean shouldTransform(String relativePath) {
                return isEmbeddedReferenceHtml(relativePath);
            }

            @Override
            public String transform(String relativePath, String content) throws IOException {
                return transformEmbeddedReferenceHtml(relativePath, content);
            }
        });
    }

    private static String renderProjectGuideHtml(
        Path projectDirectory,
        GuideProject project,
        String platformVersion
    ) throws IOException {
        return new ModernGuideRenderer(projectDirectory, project, platformVersion).render();
    }

    private String readOrRenderProjectGuideHtml(
        Path projectDirectory,
        GuideProject project,
        String platformVersion,
        int projectIndex,
        int projectCount
    ) throws IOException {
        Path renderedGuideHtml = projectDirectory.resolve(project.platformGuideHtmlPath());
        if (Files.isRegularFile(renderedGuideHtml) && !hasGuideSourceFiles(projectDirectory, project)) {
            getLogger().quiet(
                "[{}/{}] Reading pre-rendered {} guide fragment because source guide files are not available.",
                projectIndex,
                projectCount,
                project.displayName()
            );
            return Files.readString(renderedGuideHtml, StandardCharsets.UTF_8);
        }
        getLogger().quiet(
            "[{}/{}] Rendering {} guide with the Micronaut docs engine.",
            projectIndex,
            projectCount,
            project.displayName()
        );
        return renderProjectGuideHtml(projectDirectory, project, platformVersion);
    }

    private static boolean hasGuideSourceFiles(Path projectDirectory, GuideProject project) throws IOException {
        Path guideSource = projectDirectory.resolve(project.submodulePath()).resolve("src/main/docs/guide");
        if (!Files.isDirectory(guideSource)) {
            return false;
        }
        try (var paths = Files.walk(guideSource)) {
            return paths.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".adoc"));
        }
    }

    private static boolean isEmbeddedReferenceHtml(String relativePath) {
        return relativePath.equals("guide/configurationreference.html")
            || relativePath.startsWith("api/") && relativePath.endsWith(".html");
    }

    private static String transformEmbeddedReferenceHtml(String relativePath, String html) throws IOException {
        String transformed = html;
        if (relativePath.equals("guide/configurationreference.html")) {
            transformed = injectConfigurationPropertyAnchors(transformed);
            transformed = stripOldGuideAssets(transformed);
        }
        return injectEmbeddedReferenceStyle(transformed);
    }

    private static String stripOldGuideAssets(String html) {
        String transformed = OLD_GUIDE_STYLESHEET_LINK.matcher(html).replaceAll("\n");
        transformed = OLD_GUIDE_SCRIPT.matcher(transformed).replaceAll("\n");
        return OLD_GUIDE_INLINE_HIGHLIGHT_SCRIPT.matcher(transformed).replaceAll("\n");
    }

    private static String injectEmbeddedReferenceStyle(String html) throws IOException {
        int headEnd = html.indexOf("</head>");
        if (headEnd < 0) {
            return html;
        }
        String style = "\n<style>\n" + resourceText(EMBEDDED_REFERENCE_STYLE_RESOURCE) + "\n</style>\n";
        return html.substring(0, headEnd) + style + html.substring(headEnd);
    }

    private static String injectConfigurationPropertyAnchors(String html) {
        Matcher matcher = CONFIGURATION_PROPERTY_ROW.matcher(html);
        StringBuilder result = new StringBuilder(html.length() + 1024);
        while (matcher.find()) {
            String attributes = matcher.group(1);
            if (hasAttribute("<tr" + attributes + ">", "id")) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String propertyName = normalizePlainText(stripTags(matcher.group(3)));
            String replacement = "<tr" + attributes + " id=\"" + escapeAttribute(configurationPropertyAnchor(propertyName)) + "\">" + matcher.group(2);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static java.util.Optional<String> firstMatch(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return java.util.Optional.of(matcher.group(1));
        }
        return java.util.Optional.empty();
    }

    private static List<TocItem> readTocItems(GuideProject project, Path tocFile) throws IOException {
        if (!Files.isRegularFile(tocFile)) {
            throw new IOException("Missing TOC YAML for " + project.displayName() + ": " + tocFile);
        }
        return GuideToc.read(tocFile)
            .entries()
            .stream()
            .map(entry -> new TocItem(
                entry.level(),
                escapeHtml(entry.number()),
                escapeHtml(entry.title()),
                entry.id(),
                project.slug() + "-" + entry.id()
            ))
            .toList();
    }

    private static String extractDocsContent(String html) {
        int start = html.indexOf("<div class=\"docs-content\"");
        if (start < 0) {
            throw new IllegalArgumentException("Guide HTML does not contain <div class=\"docs-content\">.");
        }

        int openingEnd = html.indexOf('>', start);
        if (openingEnd < 0) {
            throw new IllegalArgumentException("Guide HTML contains an unterminated docs-content div.");
        }

        int closingStart = findMatchingDivEnd(html, start);
        return html.substring(openingEnd + 1, closingStart).trim();
    }

    private static int findMatchingDivEnd(String html, int start) {
        Matcher matcher = DIV_TAG.matcher(html);
        matcher.region(start, html.length());
        int depth = 0;
        while (matcher.find()) {
            if (matcher.group(1).isEmpty()) {
                depth++;
            } else {
                depth--;
                if (depth == 0) {
                    return matcher.start();
                }
            }
        }
        throw new IllegalArgumentException("Guide HTML contains an unterminated docs-content div.");
    }

    private static String prefixIds(String html, String slug) {
        Matcher matcher = ID_ATTRIBUTE.matcher(html);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String id = matcher.group(1);
            matcher.appendReplacement(result, Matcher.quoteReplacement("id=\"" + slug + "-" + id + "\""));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String rewriteUrls(String html, GuideProject project) {
        Matcher matcher = URL_ATTRIBUTE.matcher(html);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String attribute = matcher.group(1);
            String value = matcher.group(2);
            String rewritten = rewriteUrl(value, project);
            matcher.appendReplacement(result, Matcher.quoteReplacement(attribute + "=\"" + escapeAttribute(rewritten) + "\""));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String rewriteUrl(String value, GuideProject project) {
        if (value.isBlank()) {
            return value;
        }
        if (value.startsWith("#")) {
            return "#" + project.slug() + "-" + value.substring(1);
        }

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http:")
            || lower.startsWith("https:")
            || lower.startsWith("mailto:")
            || lower.startsWith("javascript:")
            || lower.startsWith("data:")
            || value.startsWith("//")) {
            return value;
        }

        String path = value;
        String suffix = "";
        int queryIndex = firstSuffixIndex(value);
        if (queryIndex >= 0) {
            path = value.substring(0, queryIndex);
            suffix = value.substring(queryIndex);
        }

        Path resolved = Path.of("assets", project.slug(), "docs", "guide").resolve(path).normalize();
        return resolved.toString().replace('\\', '/') + suffix;
    }

    private static String optimizeImages(String html) {
        Matcher matcher = IMG_TAG.matcher(html);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String tag = matcher.group();
            String optimized = tag;
            if (!hasAttribute(optimized, "loading")) {
                optimized = optimized.replaceFirst("(?i)<img\\b", "<img loading=\"lazy\"");
            }
            if (!hasAttribute(optimized, "decoding")) {
                optimized = optimized.replaceFirst("(?i)<img\\b", "<img decoding=\"async\"");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(optimized));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean hasAttribute(String tag, String attribute) {
        return Pattern.compile("\\b" + Pattern.quote(attribute) + "\\s*=", Pattern.CASE_INSENSITIVE)
            .matcher(tag)
            .find();
    }

    private static int firstSuffixIndex(String value) {
        int queryIndex = value.indexOf('?');
        int hashIndex = value.indexOf('#');
        if (queryIndex < 0) {
            return hashIndex;
        }
        if (hashIndex < 0) {
            return queryIndex;
        }
        return Math.min(queryIndex, hashIndex);
    }

    private static void writeSiteAssets(Path outputDirectory, Path projectDirectory, List<GuideDocument> documents, Properties descriptions, Properties projectIcons, List<ProjectCategory> categories) throws IOException {
        Path siteAssets = outputDirectory.resolve(SITE_ASSET_PATH);
        Files.createDirectories(siteAssets);
        writeProjectDocuments(siteAssets, documents);
        writeSidebarMenu(siteAssets, projectDirectory, documents, descriptions, projectIcons, categories);
        Files.writeString(siteAssets.resolve("site.css"), resourceText(SITE_CSS_RESOURCE), StandardCharsets.UTF_8);
        Files.writeString(siteAssets.resolve("site.js"), renderTemplate("assets/site.js", scriptModel(documents)), StandardCharsets.UTF_8);
        String searchIndexJson = searchIndexJson(projectDirectory, documents, descriptions);
        Files.writeString(siteAssets.resolve("search-index.json"), searchIndexJson, StandardCharsets.UTF_8);
        Files.writeString(
            siteAssets.resolve("search-index.js"),
            "window.__PLATFORM_SEARCH_INDEX__=" + searchIndexJson + ";\n",
            StandardCharsets.UTF_8
        );
        copyResource(LOGO_BLACK_RESOURCE, siteAssets.resolve("logos/micronaut-horizontal-black.svg"));
        copyResource(LOGO_WHITE_RESOURCE, siteAssets.resolve("logos/micronaut-horizontal-white.svg"));
        copyResource(MICRONAUT_SALLY_RESOURCE, siteAssets.resolve("icons/micronaut-sally.svg"));
    }

    private static void writeProjectDocuments(Path siteAssets, List<GuideDocument> documents) throws IOException {
        Path documentDirectory = siteAssets.resolve("documents");
        Files.createDirectories(documentDirectory);
        for (GuideDocument document : documents) {
            String slug = document.project().slug();
            String html = document.contentHtml();
            Files.writeString(documentDirectory.resolve(slug + ".html"), html, StandardCharsets.UTF_8);
            Files.writeString(
                documentDirectory.resolve(slug + ".js"),
                "window.__PLATFORM_DOCUMENTS__=window.__PLATFORM_DOCUMENTS__||{};"
                    + "window.__PLATFORM_DOCUMENTS__[\"" + jsonString(slug) + "\"]=\""
                    + jsonString(html)
                    + "\";\n",
                StandardCharsets.UTF_8
            );
        }
    }

    private static void writeSidebarMenu(Path siteAssets, Path projectDirectory, List<GuideDocument> documents, Properties descriptions, Properties projectIcons, List<ProjectCategory> categories) throws IOException {
        String html = renderTemplate("sidebar-menu", sidebarMenuModel(projectDirectory, documents, descriptions, projectIcons, categories));
        Files.writeString(siteAssets.resolve("sidebar-menu.html"), html, StandardCharsets.UTF_8);
        Files.writeString(
            siteAssets.resolve("sidebar-menu.js"),
            "window.__PLATFORM_SIDEBAR_MENU__=\""
                + jsonString(html)
                + "\";\n",
            StandardCharsets.UTF_8
        );
    }

    private static String renderTemplate(String templateName, Map<String, Object> model) throws IOException {
        Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader(TEMPLATE_ROOT, ".hbs"))
            .infiniteLoops(true)
            .with(new ConcurrentMapTemplateCache());
        Template template = handlebars.compile(templateName);
        return template.apply(model);
    }

    private static String resourceText(String resourcePath) throws IOException {
        try (InputStream input = GeneratePlatformDocsTask.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing classpath resource: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void copyResource(String resourcePath, Path target) throws IOException {
        try (InputStream input = GeneratePlatformDocsTask.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing classpath resource: " + resourcePath);
            }
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, Object> siteModel(Path projectDirectory, List<GuideDocument> documents, Properties descriptions, Properties projectIcons, List<ProjectCategory> categories) throws IOException, InterruptedException {
        String defaultProject = documents.get(0).project().slug();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("assetPath", SITE_ASSET_PATH);
        model.put("defaultProject", defaultProject);
        model.put("overviewSectionId", OVERVIEW_SECTION_ID);
        model.put("platform", platformModel(projectDirectory));
        model.put("icons", iconModel());
        List<Map<String, Object>> documentModels = documentModels(projectDirectory, documents, descriptions, projectIcons);
        model.put("documents", documentModels);
        model.put("categories", categoryModels(documentModels, categories));
        model.put("sidebarMenuPath", SITE_ASSET_PATH + "/sidebar-menu.html");
        model.put("sidebarMenuScriptPath", SITE_ASSET_PATH + "/sidebar-menu.js");
        return model;
    }

    private static Map<String, Object> platformModel(Path projectDirectory) throws IOException, InterruptedException {
        Path platformDirectory = projectDirectory.resolve("repos/micronaut-platform");
        Map<String, String> properties = readProperties(platformDirectory.resolve("gradle.properties"));
        String version = properties.getOrDefault("projectVersion", "");
        String branch = GitSupport.run(platformDirectory, "branch", "--show-current").trim();
        if (branch.isBlank()) {
            branch = "detached";
        }
        String revision = GitSupport.run(platformDirectory, "describe", "--tags", "--always", "--dirty").trim();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("name", "Micronaut Platform");
        model.put("version", version);
        model.put("displayVersion", version.isBlank() ? branch : version);
        model.put("branch", branch);
        model.put("revision", revision);
        return model;
    }

    private static Map<String, String> readProperties(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Map<String, String> properties = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex < 1) {
                continue;
            }
            properties.put(trimmed.substring(0, equalsIndex).trim(), trimmed.substring(equalsIndex + 1).trim());
        }
        return properties;
    }

    private static Map<String, String> iconModel() throws IOException {
        Map<String, String> icons = new LinkedHashMap<>();
        icons.put("arrowUp", lucideIcon("arrow-up", "trigger-icon"));
        icons.put("bookOpen", lucideIcon("book-open", "project-icon"));
        icons.put("braces", lucideIcon("braces", "badge-icon"));
        icons.put("externalLink", lucideIcon("external-link", "badge-icon"));
        icons.put("fileText", lucideIcon("file-text", "badge-icon"));
        icons.put("github", lucideIcon("github", "badge-icon"));
        icons.put("gitBranch", lucideIcon("git-branch", "badge-icon"));
        icons.put("maximize2", lucideIcon("maximize-2", "trigger-icon"));
        icons.put("menu", lucideIcon("menu", "trigger-icon"));
        icons.put("minimize2", lucideIcon("minimize-2", "trigger-icon"));
        icons.put("moon", lucideIcon("moon", "theme-icon-svg"));
        icons.put("panelLeft", lucideIcon("panel-left", "trigger-icon"));
        icons.put("search", lucideIcon("search", "search-icon"));
        icons.put("slidersHorizontal", lucideIcon("sliders-horizontal", "badge-icon"));
        icons.put("sun", lucideIcon("sun", "theme-icon-svg"));
        icons.put("x", lucideIcon("x", "trigger-icon"));
        return icons;
    }

    private static String lucideIcon(String name, String extraClass) throws IOException {
        String version = lucideVersion();
        String resource = LUCIDE_ICON_ROOT.formatted(version) + name + ".svg";
        String svg = classLoaderResourceText(resource)
            .replaceFirst("(?s)^\\s*<!--.*?-->\\s*", "")
            .trim();
        Matcher matcher = SVG_TAG.matcher(svg);
        if (!matcher.find()) {
            throw new IOException("Lucide icon does not contain an svg element: " + resource);
        }
        String attributes = matcher.group(1);
        Matcher classMatcher = SVG_CLASS.matcher(attributes);
        String replacement;
        if (classMatcher.find()) {
            String classes = (extraClass + " " + classMatcher.group(1)).trim();
            String updatedAttributes = classMatcher.replaceFirst(Matcher.quoteReplacement("class=\"" + classes + "\""));
            replacement = "<svg" + updatedAttributes + " aria-hidden=\"true\" focusable=\"false\">";
        } else {
            replacement = "<svg class=\"" + extraClass + "\"" + attributes + " aria-hidden=\"true\" focusable=\"false\">";
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static String brandIcon(String name, String extraClass) throws IOException {
        String svg = resourceText(BRAND_ICON_RESOURCE_ROOT + name + ".svg");
        svg = SVG_TITLE.matcher(svg).replaceAll("").trim();
        return inlineSvg(svg, extraClass);
    }

    private static String inlineSvg(String svg, String extraClass) throws IOException {
        Matcher matcher = SVG_TAG.matcher(svg);
        if (!matcher.find()) {
            throw new IOException("Inline icon does not contain an svg element.");
        }
        String attributes = matcher.group(1);
        attributes = SVG_ROLE.matcher(attributes).replaceAll("");
        attributes = SVG_ARIA_HIDDEN.matcher(attributes).replaceAll("");
        attributes = SVG_FOCUSABLE.matcher(attributes).replaceAll("");
        Matcher classMatcher = SVG_CLASS.matcher(attributes);
        String replacement;
        if (classMatcher.find()) {
            String classes = (extraClass + " " + classMatcher.group(1)).trim();
            String updatedAttributes = classMatcher.replaceFirst(Matcher.quoteReplacement("class=\"" + classes + "\""));
            replacement = "<svg" + updatedAttributes + " aria-hidden=\"true\" focusable=\"false\">";
        } else {
            replacement = "<svg class=\"" + extraClass + "\"" + attributes + " aria-hidden=\"true\" focusable=\"false\">";
        }
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static String lucideVersion() throws IOException {
        try (InputStream input = GeneratePlatformDocsTask.class.getClassLoader().getResourceAsStream(LUCIDE_PROPERTIES_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing Lucide WebJar metadata: " + LUCIDE_PROPERTIES_RESOURCE);
            }
            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IOException("Lucide WebJar metadata does not define a version.");
            }
            return version;
        }
    }

    private static String classLoaderResourceText(String resourcePath) throws IOException {
        try (InputStream input = GeneratePlatformDocsTask.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing classpath resource: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> scriptModel(List<GuideDocument> documents) throws IOException {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("firstSections", firstSectionModels(documents));
        model.put("searchIndexUrl", SITE_ASSET_PATH + "/search-index.json");
        model.put("searchIndexScriptUrl", SITE_ASSET_PATH + "/search-index.js");
        model.put("sidebarMenuUrl", SITE_ASSET_PATH + "/sidebar-menu.html");
        model.put("sidebarMenuScriptUrl", SITE_ASSET_PATH + "/sidebar-menu.js");
        model.put("codeLanguageIcons", codeLanguageIconsJson());
        model.put("pageIndexItems", pageIndexItemsJson(documents));
        return model;
    }

    private static String pageIndexItemsJson(List<GuideDocument> documents) {
        StringBuilder json = new StringBuilder(1024 * 32);
        json.append('{');
        for (int documentIndex = 0; documentIndex < documents.size(); documentIndex++) {
            GuideDocument document = documents.get(documentIndex);
            if (documentIndex > 0) {
                json.append(',');
            }
            json.append('"').append(jsonString(document.project().slug())).append("\":[");
            for (int itemIndex = 0; itemIndex < document.tocItems().size(); itemIndex++) {
                TocItem item = document.tocItems().get(itemIndex);
                String number = tocItemPlainText(item.numberHtml());
                String title = tocItemPlainText(item.titleHtml());
                String label = number.isBlank() ? title : number + " " + title;
                if (itemIndex > 0) {
                    json.append(',');
                }
                json.append('{')
                    .append("\"id\":\"").append(jsonString(item.prefixedId())).append("\",")
                    .append("\"number\":\"").append(jsonString(number)).append("\",")
                    .append("\"title\":\"").append(jsonString(title)).append("\",")
                    .append("\"label\":\"").append(jsonString(label)).append("\",")
                    .append("\"level\":").append(Math.max(0, item.level()))
                    .append('}');
            }
            json.append(']');
        }
        return json.append('}').toString();
    }

    private static String tocItemPlainText(String html) {
        return normalizePlainText(stripTags(html)
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&"));
    }

    private static String codeLanguageIconsJson() throws IOException {
        Map<String, CodeLanguageIcon> icons = new LinkedHashMap<>();
        putBrandLanguageIcon(icons, "gradle", "gradle");
        putBrandLanguageIcon(icons, "graphql", "graphql");
        putBrandLanguageIcon(icons, "groovy", "apachegroovy");
        putBrandLanguageIcon(icons, "html", "html5");
        putFilledLanguageIcon(icons, "java", "java");
        putBrandLanguageIcon(icons, "javascript", "javascript");
        putBrandLanguageIcon(icons, "json", "json");
        putBrandLanguageIcon(icons, "kotlin", "kotlin");
        putBrandLanguageIcon(icons, "maven", "apachemaven");
        putBrandLanguageIcon(icons, "python", "python");
        putBrandLanguageIcon(icons, "shell", "gnubash");
        putBrandLanguageIcon(icons, "toml", "toml");
        putBrandLanguageIcon(icons, "typescript", "typescript");
        putBrandLanguageIcon(icons, "yaml", "yaml");
        putLanguageIcon(icons, "hocon", "hocon");
        putLanguageIcon(icons, "properties", "properties");
        putLanguageIcon(icons, "protobuf", "protobuf");
        putLanguageIcon(icons, "sql", "sql");
        putLanguageIcon(icons, "text", "text");
        putLanguageIcon(icons, "xml", "xml");
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, CodeLanguageIcon> entry : icons.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            CodeLanguageIcon icon = entry.getValue();
            json.append('"').append(jsonString(entry.getKey())).append("\":{")
                .append("\"viewBox\":\"").append(jsonString(icon.viewBox())).append("\",")
                .append("\"body\":\"").append(jsonString(icon.body())).append("\",")
                .append("\"fill\":").append(icon.fill())
                .append('}');
        }
        return json.append('}').toString();
    }

    private static void putBrandLanguageIcon(Map<String, CodeLanguageIcon> icons, String language, String iconName) throws IOException {
        icons.put(language, svgLanguageIcon(resourceText(BRAND_ICON_RESOURCE_ROOT + iconName + ".svg"), true));
    }

    private static void putLanguageIcon(Map<String, CodeLanguageIcon> icons, String language, String iconName) throws IOException {
        icons.put(language, svgLanguageIcon(resourceText(LANGUAGE_ICON_RESOURCE_ROOT + iconName + ".svg"), false));
    }

    private static void putFilledLanguageIcon(Map<String, CodeLanguageIcon> icons, String language, String iconName) throws IOException {
        icons.put(language, svgLanguageIcon(resourceText(LANGUAGE_ICON_RESOURCE_ROOT + iconName + ".svg"), true));
    }

    private static CodeLanguageIcon svgLanguageIcon(String svg, boolean fill) throws IOException {
        String normalized = SVG_TITLE.matcher(svg).replaceAll("").trim();
        Matcher matcher = SVG_TAG.matcher(normalized);
        if (!matcher.find()) {
            throw new IOException("Language icon does not contain an svg element.");
        }
        Matcher viewBoxMatcher = SVG_VIEW_BOX.matcher(matcher.group(1));
        if (!viewBoxMatcher.find()) {
            throw new IOException("Language icon does not contain a viewBox attribute.");
        }
        int endTag = normalized.toLowerCase(Locale.ROOT).lastIndexOf("</svg>");
        if (endTag < matcher.end()) {
            throw new IOException("Language icon does not contain a closing svg element.");
        }
        return new CodeLanguageIcon(viewBoxMatcher.group(1), normalized.substring(matcher.end(), endTag).trim(), fill);
    }

    private static Map<String, Object> sidebarMenuModel(Path projectDirectory, List<GuideDocument> documents, Properties descriptions, Properties projectIcons, List<ProjectCategory> categories) throws IOException {
        Map<String, Object> model = new LinkedHashMap<>();
        List<Map<String, Object>> documentModels = documentModels(projectDirectory, documents, descriptions, projectIcons);
        model.put("documents", documentModels);
        model.put("categories", categoryModels(documentModels, categories));
        return model;
    }

    private static List<Map<String, Object>> categoryModels(List<Map<String, Object>> documents, List<ProjectCategory> categories) throws IOException {
        Map<String, ProjectCategory> categoryByName = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> documentsByCategory = new LinkedHashMap<>();
        for (ProjectCategory category : categories) {
            categoryByName.put(category.name(), category);
            documentsByCategory.put(category.name(), new ArrayList<>());
        }
        ProjectCategory other = categoryByName.values()
            .stream()
            .filter(category -> "other".equals(category.slug()))
            .findFirst()
            .orElse(new ProjectCategory("other", "Other", "folder-git-2", "", Set.of()));
        categoryByName.putIfAbsent(other.name(), other);
        documentsByCategory.putIfAbsent(other.name(), new ArrayList<>());

        for (Map<String, Object> document : documents) {
            String slug = String.valueOf(document.get("slug"));
            documentsByCategory.computeIfAbsent(projectCategory(slug, categories, other), ignored -> new ArrayList<>()).add(document);
        }

        List<Map<String, Object>> categoryModels = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : documentsByCategory.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            ProjectCategory category = categoryByName.getOrDefault(entry.getKey(), other);
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("name", category.name());
            model.put("slug", category.slug());
            model.put("description", category.description());
            model.put("icon", lucideIcon(category.icon(), "category-icon-svg"));
            model.put("documents", entry.getValue());
            categoryModels.add(model);
        }
        return categoryModels;
    }

    private static String projectCategory(String slug, List<ProjectCategory> categories, ProjectCategory other) {
        for (ProjectCategory category : categories) {
            if (category.projectSlugs().contains(slug)) {
                return category.name();
            }
        }
        return other.name();
    }

    private static String categorySlug(String name) {
        return name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
    }

    private static String searchIndexJson(Path projectDirectory, List<GuideDocument> documents, Properties descriptions) throws IOException {
        List<SearchItem> items = new ArrayList<>();
        for (GuideDocument document : documents) {
            GuideProject project = document.project();
            ProjectDescription description = projectDescription(projectDirectory, document, descriptions);
            items.add(new SearchItem(
                "project",
                project.slug(),
                project.displayName(),
                project.displayName(),
                project.slug(),
                "Open documentation",
                project.displayName() + " " + project.slug() + " " + description.shortDescription() + " " + description.longDescription(),
                ""
            ));
            for (TocItem item : document.tocItems()) {
                String number = normalizePlainText(stripTags(item.numberHtml()));
                String title = normalizePlainText(stripTags(item.titleHtml()));
                items.add(new SearchItem(
                    "section",
                    project.slug(),
                    project.displayName(),
                    searchResultTitle(project, title),
                    item.prefixedId(),
                    project.displayName(),
                    project.displayName() + " " + project.slug() + " " + number + " " + title,
                    ""
                ));
            }
            items.addAll(contentSearchItems(document));
            String firstSection = firstFragment(document);
            String configurationReferencePath = generatedDocumentPath(projectDirectory, project, "guide/configurationreference.html");
            if (!configurationReferencePath.isBlank()) {
                Path configurationReferenceFile = projectDirectory.resolve(project.generatedDocsPath()).resolve("guide/configurationreference.html");
                for (ConfigurationProperty property : configurationProperties(configurationReferenceFile)) {
                    items.add(new SearchItem(
                        "configuration",
                        project.slug(),
                        project.displayName(),
                        property.name(),
                        firstSection,
                        "Configuration property",
                        project.displayName() + " " + project.slug() + " " + property.name() + " " + property.description(),
                        configurationReferencePath + "#" + property.anchor()
                    ));
                }
            }
            for (ApiType apiType : apiTypes(projectDirectory, project)) {
                items.add(new SearchItem(
                    "api-type",
                    project.slug(),
                    project.displayName(),
                    apiType.name(),
                    firstSection,
                    apiType.packageName(),
                    apiType.qualifiedName() + " " + apiType.name() + " " + apiType.packageName(),
                    apiType.referencePath()
                ));
            }
        }

        Map<String, LinkedHashSet<Integer>> terms = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            SearchItem item = items.get(i);
            if ("api-type".equals(item.kind())) {
                continue;
            }
            boolean contentItem = "content".equals(item.kind());
            LinkedHashSet<String> tokens = new LinkedHashSet<>(searchTokens(item.searchText()));
            tokens.addAll(searchTokens(item.title()));
            Set<String> prefixTokens = prefixSearchTokens(item, tokens);
            for (String token : tokens) {
                if (contentItem && !isContentSearchToken(token)) {
                    continue;
                }
                addSearchTerm(terms, token, i);
                if (contentItem || !prefixTokens.contains(token)) {
                    continue;
                }
                int prefixLength = Math.min(token.length(), 20);
                for (int length = 2; length < prefixLength; length++) {
                    addSearchTerm(terms, token.substring(0, length), i);
                }
            }
        }

        StringBuilder json = new StringBuilder(1024 * 64);
        json.append("{\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            SearchItem item = items.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                .append("\"kind\":\"").append(jsonString(item.kind())).append("\",")
                .append("\"project\":\"").append(jsonString(item.project())).append("\",")
                .append("\"projectTitle\":\"").append(jsonString(item.projectTitle())).append("\",")
                .append("\"title\":\"").append(jsonString(item.title())).append("\",")
                .append("\"href\":\"#").append(jsonString(item.section())).append("\",")
                .append("\"detail\":\"").append(jsonString(item.detail())).append("\",")
                .append("\"referenceUrl\":\"").append(jsonString(item.referenceUrl())).append("\"")
                .append('}');
        }
        json.append("],\"terms\":{");
        int termIndex = 0;
        for (Map.Entry<String, LinkedHashSet<Integer>> entry : terms.entrySet()) {
            if (termIndex++ > 0) {
                json.append(',');
            }
            json.append('"').append(jsonString(entry.getKey())).append("\":[");
            int idIndex = 0;
            for (Integer id : entry.getValue()) {
                if (idIndex++ > 0) {
                    json.append(',');
                }
                json.append(id);
            }
            json.append(']');
        }
        json.append("}}");
        return json.toString();
    }

    private static Set<String> prefixSearchTokens(SearchItem item, LinkedHashSet<String> tokens) {
        if ("content".equals(item.kind())) {
            return Set.of();
        }
        return tokens;
    }

    private static boolean isContentSearchToken(String token) {
        return token.length() >= 3
            && token.length() <= 60
            && !CONTENT_SEARCH_STOP_WORDS.contains(token)
            && !token.chars().allMatch(Character::isDigit);
    }

    private static List<SearchItem> contentSearchItems(GuideDocument document) {
        GuideProject project = document.project();
        List<SectionStart> sections = sectionStarts(document);
        List<SearchItem> items = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            SectionStart section = sections.get(i);
            int end = i + 1 < sections.size() ? sections.get(i + 1).offset() : document.contentHtml().length();
            String html = document.contentHtml().substring(section.offset(), end);
            String content = searchableHtmlText(html);
            if (searchTokens(content).isEmpty()) {
                continue;
            }
            String title = normalizePlainText(stripTags(section.item().titleHtml()));
            items.add(new SearchItem(
                "content",
                project.slug(),
                project.displayName(),
                searchResultTitle(project, title),
                section.item().prefixedId(),
                project.displayName(),
                project.displayName() + " " + project.slug() + " " + content,
                ""
            ));
        }
        return items;
    }

    private static List<SectionStart> sectionStarts(GuideDocument document) {
        List<SectionStart> sections = new ArrayList<>();
        for (TocItem item : document.tocItems()) {
            int offset = indexOfId(document.contentHtml(), item.prefixedId());
            if (offset >= 0) {
                sections.add(new SectionStart(item, offset));
            }
        }
        sections.sort(Comparator.comparingInt(SectionStart::offset));
        return sections;
    }

    private static int indexOfId(String html, String id) {
        Matcher matcher = ID_ATTRIBUTE.matcher(html);
        while (matcher.find()) {
            if (id.equals(matcher.group(1))) {
                return matcher.start();
            }
        }
        return -1;
    }

    private static String searchableHtmlText(String html) {
        return normalizePlainText(html
            .replaceAll("(?is)<script\\b[^>]*>.*?</script>", " ")
            .replaceAll("(?is)<style\\b[^>]*>.*?</style>", " ")
            .replaceAll("(?is)<[^>]+>", " "));
    }

    private static void addSearchTerm(Map<String, LinkedHashSet<Integer>> terms, String term, int itemIndex) {
        terms.computeIfAbsent(term, ignored -> new LinkedHashSet<>()).add(itemIndex);
    }

    private static List<ConfigurationProperty> configurationProperties(Path configurationReferenceFile) throws IOException {
        if (!Files.isRegularFile(configurationReferenceFile)) {
            return List.of();
        }
        String html = Files.readString(configurationReferenceFile, StandardCharsets.UTF_8);
        Map<String, ConfigurationProperty> properties = new LinkedHashMap<>();
        Matcher matcher = CONFIGURATION_PROPERTY_ROW.matcher(html);
        while (matcher.find()) {
            String name = normalizePlainText(stripTags(matcher.group(3)));
            if (name.isBlank()) {
                continue;
            }
            properties.putIfAbsent(name, new ConfigurationProperty(name, configurationPropertyAnchor(name), ""));
        }
        return List.copyOf(properties.values());
    }

    private static List<ApiType> apiTypes(Path projectDirectory, GuideProject project) throws IOException {
        Path typeSearchIndex = projectDirectory.resolve(project.generatedDocsPath()).resolve("api/type-search-index.js");
        if (!Files.isRegularFile(typeSearchIndex)) {
            return List.of();
        }
        Path apiDirectory = typeSearchIndex.getParent();
        String script = Files.readString(typeSearchIndex, StandardCharsets.UTF_8);
        Map<String, ApiType> types = new LinkedHashMap<>();
        Matcher objectMatcher = JAVADOC_SEARCH_OBJECT.matcher(script);
        while (objectMatcher.find()) {
            Map<String, String> fields = javadocSearchFields(objectMatcher.group(1));
            String packageName = fields.getOrDefault("p", "");
            String name = fields.getOrDefault("l", "");
            if (packageName.isBlank() || name.isBlank()) {
                continue;
            }
            String relativePath = fields.get("u");
            if (relativePath == null || relativePath.isBlank()) {
                relativePath = packageName.replace('.', '/') + "/" + name + ".html";
            }
            Path typeFile = apiDirectory.resolve(relativePath).normalize();
            if (!typeFile.startsWith(apiDirectory) || !Files.isRegularFile(typeFile)) {
                continue;
            }
            String referencePath = Path.of("assets", project.slug(), "docs", "api")
                .resolve(relativePath)
                .toString()
                .replace('\\', '/');
            types.putIfAbsent(packageName + "." + name, new ApiType(name, packageName, referencePath));
        }
        return List.copyOf(types.values());
    }

    private static Map<String, String> javadocSearchFields(String object) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher fieldMatcher = JAVADOC_SEARCH_FIELD.matcher(object);
        while (fieldMatcher.find()) {
            fields.put(fieldMatcher.group(1), unescapeJsonString(fieldMatcher.group(2)));
        }
        return fields;
    }

    private static String unescapeJsonString(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character != '\\' || i + 1 >= value.length()) {
                result.append(character);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        String codePoint = value.substring(i + 1, i + 5);
                        try {
                            result.append((char) Integer.parseInt(codePoint, 16));
                            i += 4;
                        } catch (NumberFormatException ignored) {
                            result.append("\\u").append(codePoint);
                            i += 4;
                        }
                    } else {
                        result.append("\\u");
                    }
                }
                default -> result.append(escaped);
            }
        }
        return result.toString();
    }

    private static String searchResultTitle(GuideProject project, String title) {
        String projectName = project.displayName();
        if (title.regionMatches(true, 0, projectName + " ", 0, projectName.length() + 1)) {
            return title.substring(projectName.length()).trim();
        }
        return title;
    }

    private static String configurationPropertyAnchor(String propertyName) {
        String anchor = normalizePlainText(propertyName)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        return anchor.isBlank() ? "configuration-property" : "configuration-property-" + anchor;
    }

    private static List<String> searchTokens(String value) {
        String plainText = normalizePlainText(value);
        String normalized = plainText.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        addSearchTokens(tokens, normalized.replaceAll("[^a-z0-9]+", " "));
        addSearchTokens(tokens, splitCamelCase(plainText).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " "));
        String compact = normalized.replaceAll("[^a-z0-9]+", "");
        if (compact.length() > 1 && compact.length() <= 40) {
            tokens.add(compact);
        }
        return List.copyOf(tokens);
    }

    private static String splitCamelCase(String value) {
        return value
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    }

    private static void addSearchTokens(LinkedHashSet<String> tokens, String value) {
        for (String token : value.split("\\s+")) {
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\u2028' -> escaped.append("\\u2028");
                case '\u2029' -> escaped.append("\\u2029");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u").append(String.format("%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static List<Map<String, Object>> documentModels(Path projectDirectory, List<GuideDocument> documents, Properties descriptions, Properties projectIcons) throws IOException {
        List<Map<String, Object>> models = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            models.add(documentModel(projectDirectory, documents.get(i), descriptions, projectIcons, false));
        }
        return models;
    }

    private static Map<String, Object> documentModel(Path projectDirectory, GuideDocument document, Properties descriptions, Properties projectIcons, boolean selected) throws IOException {
        GuideProject project = document.project();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("slug", project.slug());
        model.put("displayName", project.displayName());
        model.put("projectIcon", projectIcon(project, projectIcons));
        model.put("publishedGuideUrl", project.publishedGuideUrl());
        model.put("repositoryUrl", project.repositoryUrl());
        model.put("branch", project.branch());
        model.put("title", document.title());
        ProjectDescription description = projectDescription(projectDirectory, document, descriptions);
        model.put("shortDescription", description.shortDescription());
        model.put("longDescription", description.longDescription());
        model.put("description", description.longDescription());
        model.put("version", document.version());
        model.put("sidebarTitle", document.version().isBlank() ? project.displayName() : project.displayName() + " " + document.version());
        model.put("firstSection", firstFragment(document));
        model.put("documentPath", projectDocumentPath(project, ".html"));
        model.put("documentScriptPath", projectDocumentPath(project, ".js"));
        model.put("toc", sidebarTocNodeModels(buildTocTree(document.tocItems())));
        model.put("selected", selected);
        String apiReferencePath = generatedDocumentPath(projectDirectory, project, "api/index.html");
        String configurationReferencePath = generatedDocumentPath(projectDirectory, project, "guide/configurationreference.html");
        model.put("apiReferencePath", apiReferencePath);
        model.put("configurationReferencePath", configurationReferencePath);
        model.put("hasReferenceLinks", !apiReferencePath.isBlank() || !configurationReferencePath.isBlank());
        return model;
    }

    private static String projectDocumentPath(GuideProject project, String extension) {
        return Path.of(SITE_ASSET_PATH, "documents", project.slug() + extension)
            .toString()
            .replace('\\', '/');
    }

    private static String projectIcon(GuideProject project, Properties projectIcons) throws IOException {
        String configured = projectIcons.getProperty("project." + project.slug() + ".icon", "lucide:book-open").trim();
        int separator = configured.indexOf(':');
        if (separator <= 0 || separator == configured.length() - 1) {
            throw new IOException("Invalid icon mapping for " + project.slug() + ": " + configured);
        }
        String source = configured.substring(0, separator);
        String name = configured.substring(separator + 1);
        return switch (source) {
            case "brand" -> brandIcon(name, "project-icon project-brand-icon");
            case "image" -> imageIcon(name, "project-icon project-image-icon");
            case "lucide" -> lucideIcon(name, "project-icon project-lucide-icon");
            default -> throw new IOException("Unsupported icon source for " + project.slug() + ": " + source);
        };
    }

    private static String imageIcon(String name, String extraClass) {
        return "<img class=\"" + extraClass + "\" src=\"" + SITE_ASSET_PATH + "/icons/" + escapeAttribute(name) + "\" alt=\"\" aria-hidden=\"true\">";
    }

    private static ProjectDescription projectDescription(Path projectDirectory, GuideDocument document, Properties descriptions) {
        ProjectDescription generated = generatedProjectDescription(projectDirectory, document);
        String slug = document.project().slug();
        String shortDescription = descriptionProperty(descriptions, slug, "shortDescription")
            .orElse(generated.shortDescription());
        String longDescription = descriptionProperty(descriptions, slug, "longDescription")
            .orElse(generated.longDescription());
        return new ProjectDescription(
            withoutMicronautBranding(shortDescription),
            normalizeCardDescription(longDescription)
        );
    }

    private static ProjectDescription generatedProjectDescription(Path projectDirectory, GuideDocument document) {
        GuideProject project = document.project();
        String fallback = document.title();
        String shortDescription = fallback;
        Path propertiesFile = projectDirectory.resolve(project.submodulePath()).resolve("gradle.properties");
        if (Files.isRegularFile(propertiesFile)) {
            shortDescription = readProjectDescription(propertiesFile).orElse(fallback);
        }
        String introduction = introductionSummary(project, document.contentHtml());
        String longDescription = introduction.isBlank()
            ? enrichDescription(shortDescription, introduction, fallback)
            : introduction;
        return new ProjectDescription(
            withoutMicronautBranding(shortDescription),
            normalizeCardDescription(longDescription)
        );
    }

    private static java.util.Optional<String> descriptionProperty(Properties descriptions, String slug, String propertyName) {
        return java.util.Optional.ofNullable(descriptions.getProperty("project." + slug + "." + propertyName))
            .map(GeneratePlatformDocsTask::normalizePlainText)
            .filter(description -> !description.isBlank());
    }

    private static java.util.Optional<String> readProjectDescription(Path propertiesFile) {
        try {
            return java.util.Optional.ofNullable(readProperties(propertiesFile).get("projectDesc"))
                .map(String::trim)
                .filter(description -> !description.isBlank());
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    private static String introductionSummary(GuideProject project, String contentHtml) {
        String introductionAnchor = "id=\"" + project.slug() + "-introduction\"";
        int introductionIndex = contentHtml.indexOf(introductionAnchor);
        String source = introductionIndex >= 0 ? contentHtml.substring(introductionIndex) : contentHtml;
        StringBuilder summary = new StringBuilder();
        Matcher matcher = PARAGRAPH_TAG.matcher(source);
        while (matcher.find() && wordCount(summary.toString()) < CARD_DESCRIPTION_MAX_WORDS) {
            String paragraph = normalizePlainText(stripTags(matcher.group(1)));
            if (paragraph.isBlank()
                || paragraph.startsWith("Version:")
                || paragraph.length() < 40) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(' ');
            }
            summary.append(paragraph);
        }
        return normalizeCardDescription(summary.toString());
    }

    private static String enrichDescription(String description, String introduction, String fallback) {
        String normalizedDescription = normalizePlainText(description);
        String normalizedIntroduction = normalizePlainText(introduction);
        if (normalizedDescription.isBlank()) {
            normalizedDescription = fallback;
        }
        if (wordCount(normalizedDescription) >= CARD_DESCRIPTION_MIN_WORDS || normalizedIntroduction.isBlank()) {
            return normalizedDescription;
        }
        if (containsText(normalizedIntroduction, normalizedDescription)) {
            return normalizedIntroduction;
        }
        if (containsText(normalizedDescription, normalizedIntroduction)) {
            return normalizedDescription;
        }
        return normalizeCardDescription(normalizedDescription + (normalizedDescription.endsWith(".") ? " " : ". ") + normalizedIntroduction);
    }

    private static boolean containsText(String text, String fragment) {
        return text.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static String withoutMicronautBranding(String description) {
        String normalized = normalizePlainText(description);
        normalized = normalized
            .replaceAll("(?i)^extensions\\s+to\\s+integrate\\s+micronaut\\s+and\\s+(.+)$", "$1 integration extensions")
            .replaceAll("(?i)^integration\\s+between\\s+micronaut\\s+and\\s+(.+)$", "$1 integration")
            .replaceAll("(?i)^integration\\s+between\\s+(.+)\\s+and\\s+micronaut$", "$1 integration")
            .replaceAll("(?i)^(.+)\\s+support\\s+for\\s+micronaut$", "$1 Support")
            .replaceAll("(?i)\\bthe\\s+micronaut\\s+framework\\b", "the framework")
            .replaceAll("(?i)\\bmicronaut\\s+framework\\b", "the framework")
            .replaceAll("(?i)\\bmicronaut\\b", "")
            .replaceAll("\\s+", " ")
            .replaceAll("\\s+([,.;:])", "$1")
            .replaceAll("(?i)\\s+(for|with|and|in|using)$", "")
            .trim();
        if (normalized.isBlank()) {
            return description;
        }
        if (!normalized.isEmpty() && Character.isLowerCase(normalized.charAt(0))) {
            normalized = Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
        }
        return normalized;
    }

    private static String normalizePlainText(String value) {
        return value
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&#8203;", "")
            .replace("&#8217;", "'")
            .replace("&#8230;", "...")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String normalizeCardDescription(String value) {
        String normalized = normalizePlainText(value);
        if (wordCount(normalized) <= CARD_DESCRIPTION_MAX_WORDS) {
            return normalized;
        }
        String sentenceLimited = sentenceLimitedDescription(normalized);
        if (!sentenceLimited.isBlank()) {
            return sentenceLimited;
        }
        return words(normalized).stream()
            .limit(CARD_DESCRIPTION_MAX_WORDS)
            .collect(Collectors.joining(" "))
            .replaceAll("[,;:]$", "")
            .trim();
    }

    private static String sentenceLimitedDescription(String value) {
        Matcher matcher = Pattern.compile(".*?[.!?](?:\\s+|$)").matcher(value);
        String selected = "";
        while (matcher.find()) {
            String candidate = (selected + matcher.group()).trim();
            int words = wordCount(candidate);
            if (words > CARD_DESCRIPTION_MAX_WORDS) {
                break;
            }
            selected = candidate;
        }
        return wordCount(selected) >= CARD_DESCRIPTION_MIN_WORDS ? selected : "";
    }

    private static int wordCount(String value) {
        return words(value).size();
    }

    private static List<String> words(String value) {
        String normalized = normalizePlainText(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split("\\s+"));
    }

    private static String generatedDocumentPath(Path projectDirectory, GuideProject project, String relativePath) {
        if (!Files.isRegularFile(projectDirectory.resolve(project.generatedDocsPath()).resolve(relativePath))) {
            return "";
        }
        return Path.of("assets", project.slug(), "docs")
            .resolve(relativePath)
            .toString()
            .replace('\\', '/');
    }

    private static List<Map<String, Object>> firstSectionModels(List<GuideDocument> documents) {
        List<Map<String, Object>> models = new ArrayList<>();
        for (GuideDocument document : documents) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("project", escapeJavaScript(document.project().slug()));
            model.put("section", escapeJavaScript(firstFragment(document)));
            models.add(model);
        }
        return models;
    }

    private static List<Map<String, Object>> tocNodeModels(List<TocNode> nodes) {
        List<Map<String, Object>> models = new ArrayList<>();
        for (TocNode node : nodes) {
            models.add(tocNodeModel(node));
        }
        return models;
    }

    private static List<Map<String, Object>> sidebarTocNodeModels(List<TocNode> nodes) {
        List<Map<String, Object>> models = new ArrayList<>();
        for (TocNode node : nodes) {
            TocItem item = node.item();
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("prefixedId", item.prefixedId());
            model.put("numberHtml", item.numberHtml());
            model.put("titleHtml", item.titleHtml());
            model.put("children", List.of());
            model.put("hasChildren", false);
            models.add(model);
        }
        return models;
    }

    private static Map<String, Object> tocNodeModel(TocNode node) {
        TocItem item = node.item();
        List<Map<String, Object>> children = tocNodeModels(node.children());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("prefixedId", item.prefixedId());
        model.put("numberHtml", item.numberHtml());
        model.put("titleHtml", item.titleHtml());
        model.put("children", children);
        model.put("hasChildren", !children.isEmpty());
        return model;
    }

    private static List<TocNode> buildTocTree(List<TocItem> items) {
        List<TocNode> roots = new ArrayList<>();
        List<TocNode> parents = new ArrayList<>();
        for (TocItem item : items) {
            TocNode node = new TocNode(item);
            int level = Math.max(0, item.level());
            while (parents.size() <= level) {
                parents.add(null);
            }
            parents.set(level, node);
            while (parents.size() > level + 1) {
                parents.remove(parents.size() - 1);
            }

            if (level == 0 || parents.get(level - 1) == null) {
                roots.add(node);
            } else {
                parents.get(level - 1).children().add(node);
            }
        }
        return roots;
    }

    private static String firstFragment(GuideDocument document) {
        if (document.tocItems().isEmpty()) {
            return document.project().slug();
        }
        return document.tocItems().get(0).prefixedId();
    }

    private static String stripTags(String value) {
        return value.replaceAll("<[^>]+>", "");
    }

    private static String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escapeHtml(value).replace("\"", "&quot;");
    }

    private static String escapeJavaScript(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "");
    }

    private record GuideDocument(
        GuideProject project,
        String title,
        String version,
        List<TocItem> tocItems,
        String contentHtml
    ) {
    }

    private record ProjectDescription(String shortDescription, String longDescription) {
    }

    private record CodeLanguageIcon(String viewBox, String body, boolean fill) {
    }

    private record ProjectCategory(String slug, String name, String icon, String description, Set<String> projectSlugs) {
    }

    private record SearchItem(
        String kind,
        String project,
        String projectTitle,
        String title,
        String section,
        String detail,
        String searchText,
        String referenceUrl
    ) {
    }

    private record ConfigurationProperty(String name, String anchor, String description) {
    }

    private record ApiType(String name, String packageName, String referencePath) {

        String qualifiedName() {
            return packageName + "." + name;
        }
    }

    private record SectionStart(TocItem item, int offset) {
    }

    private record TocItem(int level, String numberHtml, String titleHtml, String originalId, String prefixedId) {
    }

    private record TocNode(TocItem item, List<TocNode> children) {
        TocNode(TocItem item) {
            this(item, new ArrayList<>());
        }
    }
}
