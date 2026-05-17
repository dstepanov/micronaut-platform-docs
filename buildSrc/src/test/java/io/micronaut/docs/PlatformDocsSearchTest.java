package io.micronaut.docs;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 45, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SAME_THREAD)
final class PlatformDocsSearchTest {

    private static final Path INDEX_FILE = Path.of(System.getProperty("platformDocs.indexFile", "../build/site/index.html"))
        .toAbsolutePath()
        .normalize();
    private static final Path SITE_DIRECTORY = INDEX_FILE.getParent();
    private static final String BROWSER_CHANNEL = System.getProperty("platformDocs.browserChannel", "chrome");
    private static final String CONFIGURATION_PROPERTY = "netty.default.allocator.num-heap-arenas";
    private static final String CONFIGURATION_PROPERTY_ANCHOR = "configuration-property-netty-default-allocator-num-heap-arenas";
    private static final String RESOURCE_TEMPLATE_QUOTE = "Resource templates allow servers to expose parameterized resources using URI templates";
    private static final Map<String, String> TEST_PROJECTS = Map.of(
        "core", "Micronaut Core",
        "serde", "Micronaut Serialization",
        "data", "Micronaut Data",
        "mcp", "Micronaut MCP",
        "oracle-cloud", "Micronaut Oracle Cloud",
        "sourcegen", "Micronaut Sourcegen"
    );

    @Test
    void searchFixtureContainsTestProjectsAndRequiredIndexTerms() throws IOException {
        assertRenderedSiteExists();
        String html = Files.readString(INDEX_FILE, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> project : TEST_PROJECTS.entrySet()) {
            assertTrue(html.contains("data-project=\"" + project.getKey() + "\""), "The rendered search fixture must include " + project.getValue() + ".");
        }
        assertTrue(html.contains("data-project-category=\"most-popular\""), "The rendered overview must include the Most Popular category.");
        assertTrue(html.contains("data-project-category=\"api\""), "The rendered overview must include the API category.");
        assertTrue(html.contains("Frequently used framework, data, security, API, messaging, and integration modules."), "The rendered overview must include docs-index category descriptions.");
        assertFalse(html.contains("project-category-count"), "The rendered overview must not show category counts.");

        Path sidebarMenu = SITE_DIRECTORY.resolve("platform-assets/sidebar-menu.html");
        assertTrue(Files.isRegularFile(sidebarMenu), "The rendered site must include the generated sidebar menu.");
        String sidebarHtml = Files.readString(sidebarMenu, StandardCharsets.UTF_8);
        assertTrue(sidebarHtml.contains("data-sidebar-category=\"most-popular\""), "The sidebar must include the Most Popular category.");
        assertTrue(sidebarHtml.contains("data-sidebar-category=\"api\""), "The sidebar must include the API category.");
        assertFalse(sidebarHtml.contains("sidebar-category-count"), "The sidebar must not show category counts.");
        assertFalse(sidebarHtml.contains("project-filter"), "The sidebar must not include the removed project filter.");
        assertFalse(sidebarHtml.contains("data-project-filter"), "The sidebar must not include project filter controls.");
        assertFalse(sidebarHtml.contains("class=\"toc-children\""), "The sidebar must keep subsections out of the left menu.");
        assertFalse(sidebarHtml.contains("href=\"#core-whatsNew\""), "The sidebar must not render second-level Core subsections.");
        for (Map.Entry<String, String> project : TEST_PROJECTS.entrySet()) {
            assertTrue(sidebarHtml.contains("data-project-option=\"" + project.getKey() + "\""), "The sidebar must include " + project.getValue() + ".");
            Path projectDocument = SITE_DIRECTORY.resolve("platform-assets/documents/" + project.getKey() + ".html");
            assertTrue(Files.isRegularFile(projectDocument), "The rendered site must include the " + project.getValue() + " lazy-loaded document.");
        }

        Path coreDocument = SITE_DIRECTORY.resolve("platform-assets/documents/core.html");
        String coreHtml = Files.readString(coreDocument, StandardCharsets.UTF_8);
        assertTrue(coreHtml.contains("docs-code-block"), "The rendered Core guide must use the modern code block renderer.");
        assertTrue(coreHtml.contains("data-copy-code"), "The rendered Core guide must include copy buttons for code samples.");
        assertTrue(coreHtml.contains("guide-section-heading"), "Guide headings must be wrapped for hover-only edit actions.");
        assertTrue(coreHtml.contains("class=\"contribute-btn\""), "Guide headings must include the icon-only edit action.");
        assertFalse(coreHtml.contains(">Improve this doc<"), "The edit action must not render visible Improve this doc text.");
        assertFalse(coreHtml.contains("docs-code-language-single"), "Single-language code samples must not render a language toolbar.");
        assertFalse(coreHtml.contains("<span class=\"docs-code-language docs-code-language-single\">Bash</span>"), "Code samples must not show Bash labels.");
        assertFalse(coreHtml.contains("<span class=\"docs-code-language docs-code-language-single\">Text</span>"), "Unknown code samples must not show Text labels.");

        Path serdeDocument = SITE_DIRECTORY.resolve("platform-assets/documents/serde.html");
        String serdeHtml = Files.readString(serdeDocument, StandardCharsets.UTF_8);
        assertTrue(serdeHtml.contains("docs-code-block"), "The rendered Serde guide must use the modern code block renderer.");
        assertTrue(serdeHtml.contains("data-copy-code"), "The rendered Serde guide must include copy buttons for code samples.");
        assertTrue(serdeHtml.contains("class=\"shiki shiki-themes"), "The rendered guide must contain static Shiki highlighted code.");
        assertTrue(serdeHtml.contains("--shiki-light:"), "The rendered guide must contain light Shiki token colors.");
        assertTrue(serdeHtml.contains("--shiki-dark:"), "The rendered guide must contain dark Shiki token colors.");
        assertFalse(serdeHtml.contains("intellij-platform-"), "Static Shiki highlighting must not use the IntelliJ theme.");
        assertFalse(serdeHtml.contains("<p>        <div class=\"listingblock"), "Generated code blocks must not be wrapped in paragraph tags.");
        assertFalse(serdeHtml.contains("<p><div class=\"listingblock"), "Generated code blocks must not be wrapped in paragraph tags.");

        Path sourcegenDocument = SITE_DIRECTORY.resolve("platform-assets/documents/sourcegen.html");
        String sourcegenHtml = Files.readString(sourcegenDocument, StandardCharsets.UTF_8);
        assertFalse(sourcegenHtml.contains("<pre>`public static String [BeanName]Object.toString"), "Indented inline code must not render as a literal block with backticks.");
        assertFalse(sourcegenHtml.contains("<pre>`public static int [BeanName]Object.hashCode"), "Indented inline code must not render as a literal block with backticks.");
        assertTrue(sourcegenHtml.contains("class=\"language-java shiki-code\" data-lang=\"java\""), "Indented Java signatures must render as highlighted code blocks.");
        assertTrue(sourcegenHtml.contains("docs-code-dependency-snippet"), "Dependency snippets must receive the dependency code block style.");
        assertTrue(sourcegenHtml.contains("[BeanName]Object."), "Sourcegen generated-method signatures must remain visible.");

        Path mcpDocument = SITE_DIRECTORY.resolve("platform-assets/documents/mcp.html");
        String mcpHtml = Files.readString(mcpDocument, StandardCharsets.UTF_8);
        assertTrue(mcpHtml.contains("<div class=\"quoteblock\">"), "The rendered MCP guide must preserve Asciidoctor quote blocks.");
        assertTrue(mcpHtml.contains(RESOURCE_TEMPLATE_QUOTE), "The rendered MCP guide must include the resource template quote.");

        Path searchIndex = SITE_DIRECTORY.resolve("platform-assets/search-index.json");
        assertTrue(Files.isRegularFile(searchIndex), "The rendered search fixture must include a search index.");
        String indexJson = Files.readString(searchIndex, StandardCharsets.UTF_8);
        assertOptionalReferenceEntries(indexJson);
        for (String slug : TEST_PROJECTS.keySet()) {
            assertTrue(indexJson.contains("\"project\":\"" + slug + "\""), "The search index must include " + slug + " entries.");
        }

        Path siteScript = SITE_DIRECTORY.resolve("platform-assets/site.js");
        assertTrue(Files.isRegularFile(siteScript), "The rendered site must include a generated script.");
        String siteJs = Files.readString(siteScript, StandardCharsets.UTF_8);
        assertTrue(siteJs.contains("const pageIndexItems = {"), "The right rail must use the generated TOC-backed page index model.");
        assertTrue(siteJs.contains("\"serde-jacksonQuick\""), "The right rail model must include Serialization subsections beyond the introduction.");
        assertTrue(siteJs.contains("\"mcp-resourcesTemplates\""), "The right rail model must include deep MCP subsections.");
    }

    @Test
    void searchNavigatesToCoreContentSerdeProjectAndConfigurationReference() throws IOException {
        try (SiteServer site = serveRenderedSite();
             Playwright playwright = createPlaywright();
             Browser browser = launchChromium(playwright)) {
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 900));
            page.setDefaultTimeout(10_000);
            page.setDefaultNavigationTimeout(10_000);
            try {
                page.navigate(site.indexUri().toString());
                page.locator("[data-search-trigger]").click();
                page.locator("[data-search-backdrop]").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                page.locator("[data-search-input]").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                assertEquals("Search projects, classes, properties, docs...", page.locator("[data-search-input]").getAttribute("placeholder"));
                assertTrue((Boolean) page.evaluate(
                    "() => {" +
                        "const panel = document.querySelector('[data-search-dialog]');" +
                        "const backdrop = document.querySelector('[data-search-backdrop]');" +
                        "const rect = panel.getBoundingClientRect();" +
                        "const centeredX = Math.abs(rect.left + rect.width / 2 - window.innerWidth / 2) < 4;" +
                        "const commandTop = rect.top > window.innerHeight * 0.12 && rect.top < window.innerHeight * 0.28;" +
                        "return !panel.hidden && !backdrop.hidden && centeredX && commandTop;" +
                        "}"
                ), "Search must open as a centered command dialog over a visible backdrop.");
                page.locator(".site-search-prompt").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                String promptText = page.locator("[data-search-dialog]").innerText();
                assertTrue(promptText.contains("Search everything"), "Search panel must expose a command-style heading.");
                assertTrue(promptText.contains("Properties"), "Search scopes must include properties.");
                assertTrue(promptText.contains("Classes"), "Search scopes must include API classes.");
                assertEquals(2, page.locator(".site-search-prompt-badge").count(), "The All prompt should only show Class and Property badges.");
                assertFalse(promptText.contains("netty.default"), "The prompt must not suggest a specific configuration property.");
                assertFalse(promptText.contains("ApplicationContext"), "The prompt must not suggest a specific API class.");
                page.locator("[data-search-scope='projects']").click();
                assertEquals(0, page.locator(".site-search-prompt-badge").count(), "Class and Property badges belong only to the All prompt.");
                page.locator("[data-search-scope='all']").click();
                assertEquals(2, page.locator(".site-search-prompt-badge").count(), "The All prompt should restore Class and Property badges.");

                search(page, "data");
                Locator dataSearchResult = visibleSearchResult(page);
                String dataResult = dataSearchResult.innerText();
                assertTrue(dataResult.contains("Micronaut Data"), "A bare module name should rank the matching project first.");
                assertEquals("project", dataSearchResult.getAttribute("data-search-kind"), "A bare module name should prefer the project result over guide sections.");
                assertFalse(dataResult.contains("Project"), "Project results should not show a redundant Project label.");

                search(page, "serialization");
                Locator serializationSearchResult = visibleSearchResult(page);
                String serializationResult = serializationSearchResult.innerText();
                assertTrue(serializationResult.contains("Micronaut Serialization"), "A project name after Micronaut should rank as the closest match.");
                assertEquals("project", serializationSearchResult.getAttribute("data-search-kind"), "A project name after Micronaut should prefer the project result.");
                assertFalse(serializationResult.contains("Project"), "Project results should not show a redundant Project label.");

                String indexJson = searchIndexJson();
                if (hasApiTypeEntries(indexJson)) {
                    search(page, "ApplicationContext");
                    Locator firstResult = visibleSearchResult(page);
                    assertTrue(firstResult.innerText().contains("Class"), "Expected an API class search result.");
                    String classResultText = searchResultTextContaining(page, "io.micronaut.context");
                    assertTrue(classResultText.contains("ApplicationContext"), "Expected the ApplicationContext API type search result.");
                    clickSearchResultContaining(page, "ApplicationContext");
                    page.waitForFunction("() => document.body.classList.contains('reference-open')");
                    assertEquals("core", page.evaluate("() => document.body.dataset.project"));
                    page.waitForFunction("() => document.querySelector('[data-reference-frame]')?.src.includes('ApplicationContext.html')");
                    page.locator("button[data-reference-close]").click();
                    page.waitForFunction("() => !document.body.classList.contains('reference-open')");
                }

                search(page, "Application Context");
                clickSearchResultContaining(page, "Application Context");
                page.waitForFunction("() => window.location.hash.length > 1 && document.body.dataset.project");
                assertFalse(((String) page.evaluate("() => document.body.dataset.project")).isBlank());
                page.waitForFunction("() => Boolean(document.querySelector('.platform-search-target'))");

                search(page, "Micronaut Serialization");
                clickSearchResultContaining(page, "Micronaut Serialization");
                page.waitForFunction("() => document.body.dataset.project === 'serde'");
                assertEquals("#serde", page.evaluate("() => window.location.hash"));
                assertProjectStartsAtTop(page, "serde");

                if (hasConfigurationPropertyEntries(indexJson)) {
                    search(page, CONFIGURATION_PROPERTY);
                    String resultText = searchResultTextContaining(page, CONFIGURATION_PROPERTY);
                    assertTrue(resultText.contains("Configuration property"), "Expected a configuration property search result.");
                    clickSearchResultContaining(page, CONFIGURATION_PROPERTY);

                    page.waitForFunction("() => document.body.classList.contains('reference-open')");
                    assertEquals("core", page.evaluate("() => document.body.dataset.project"));
                    page.waitForFunction(
                        "anchor => document.querySelector('[data-reference-frame]')?.src.includes('#' + anchor)",
                        CONFIGURATION_PROPERTY_ANCHOR
                    );
                    page.waitForFunction(
                        "anchor => {" +
                            "const frame = document.querySelector('[data-reference-frame]');" +
                            "const target = frame?.contentDocument?.getElementById(anchor);" +
                            "return Boolean(target?.classList.contains('platform-reference-target'));" +
                            "}",
                        CONFIGURATION_PROPERTY_ANCHOR
                    );
                }
            } catch (TimeoutError e) {
                throw new AssertionError("Search did not produce the expected navigable platform docs result.", e);
            } finally {
                page.close();
            }
        }
    }

    @Test
    void mobileSidebarClosesWhenClickingOutsideAfterSelectingProject() throws IOException {
        try (SiteServer site = serveRenderedSite();
             Playwright playwright = createPlaywright();
             Browser browser = launchChromium(playwright)) {
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(390, 844));
            page.setDefaultTimeout(10_000);
            page.setDefaultNavigationTimeout(10_000);
            try {
                page.navigate(site.indexUri().toString());
                page.locator(".sidebar-toggle").click();
                page.waitForFunction("() => document.body.classList.contains('sidebar-open')");
                page.locator(".sidebar-toggle").click();
                page.waitForFunction("() => !document.body.classList.contains('sidebar-open')");
                page.locator(".sidebar-toggle").click();
                page.waitForFunction("() => document.body.classList.contains('sidebar-open')");
                page.locator("[data-project-option]").first().click();
                page.waitForFunction("() => document.body.dataset.project");
                page.mouse().click(386, 80);
                page.waitForFunction("() => !document.body.classList.contains('sidebar-open')");
            } catch (TimeoutError e) {
                throw new AssertionError("The mobile sidebar did not close after clicking outside it.", e);
            } finally {
                page.close();
            }
        }
    }

    @Test
    void clickingSidebarProjectAndOverviewCardLoadsDocumentationContent() throws IOException {
        try (SiteServer site = serveRenderedSite();
             Playwright playwright = createPlaywright();
             Browser browser = launchChromium(playwright)) {
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1500, 900));
            page.setDefaultTimeout(10_000);
            page.setDefaultNavigationTimeout(10_000);
            try {
                page.navigate(site.indexUri().toString());
                page.waitForFunction("() => document.querySelector('[data-sidebar-menu]')?.dataset.menuLoaded === 'true'");

                page.locator("[data-project-option='core']").click();
                waitForLoadedProject(page, "core");
                assertTopbarProject(page, "Micronaut Core");
                assertNoVisibleDocumentationError(page, "core");
                assertProjectHasHighlightedCode(page, "core");
                assertProjectHighlightsCodeWithCallouts(page, "core");
                assertProjectFormatsCodeCalloutFooter(page, "core");
                assertProjectCodeTitlesAreOutsideFrames(page, "core");
                assertPageIndexListsAndNavigates(page, "core", "1.1 What's New in Micronaut Framework 5.0.x", "core-whatsNew");
                page.locator(".toc a[href='#core-quickStart']").click();
                assertPageIndexListsSection(page, "core", "2.1 Install the CLI", "core-buildCLI");
                assertEquals("core", page.evaluate("() => document.body.dataset.project"));

                page.locator(".topbar-title a[href='#platform']").click();
                page.waitForFunction("() => document.body.classList.contains('overview-active')");
                page.locator("[data-project-card='serde']").click();
                waitForLoadedProject(page, "serde");
                assertTopbarProject(page, "Micronaut Serialization");
                assertEquals("#serde", page.evaluate("() => window.location.hash"));
                assertProjectStartsAtTop(page, "serde");
                assertNoVisibleDocumentationError(page, "serde");
                assertProjectHasMultiLanguageToolbarTabs(page, "serde");
                assertPageIndexListsSection(page, "serde", "1.1 Why Micronaut Serialization?", "serde-why");
                page.locator(".toc a[href='#serde-releaseHistory']").click();
                assertPageIndexListsSection(page, "serde", "2 Release History", "serde-releaseHistory");
                page.locator(".toc a[href='#serde-quickStart']").click();
                assertPageIndexListsSection(page, "serde", "3.1 Jackson Annotations & Jackson Core", "serde-jacksonQuick");
                assertEquals("serde", page.evaluate("() => document.body.dataset.project"));

                for (String project : List.of("data", "mcp", "oracle-cloud", "sourcegen")) {
                    page.locator(".topbar-title a[href='#platform']").click();
                    page.waitForFunction("() => document.body.classList.contains('overview-active')");
                    page.locator("[data-project-card='" + project + "']").click();
                    waitForLoadedProject(page, project);
                    assertTopbarProject(page, TEST_PROJECTS.get(project));
                    assertEquals("#" + project, page.evaluate("() => window.location.hash"));
                    assertProjectStartsAtTop(page, project);
                    assertNoVisibleDocumentationError(page, project);
                    if ("oracle-cloud".equals(project)) {
                        assertProjectDependencyTabsAreScopedToOneGroup(page, project);
                    }
                    if ("mcp".equals(project)) {
                        page.locator(".toc a[href='#mcp-server']").click();
                        assertPageIndexListsSection(page, "mcp", "3.7.3.4 Resources Templates", "mcp-resourcesTemplates");
                    }
                    assertEquals(project, page.evaluate("() => document.body.dataset.project"));
                }
            } catch (TimeoutError e) {
                throw new AssertionError("Project documentation did not load after clicking the sidebar project or overview card.", e);
            } finally {
                page.close();
            }
        }
    }

    @Test
    void resourceTemplateQuoteIsReadableInDarkMode() throws IOException {
        try (SiteServer site = serveRenderedSite();
             Playwright playwright = createPlaywright();
             Browser browser = launchChromium(playwright)) {
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 900));
            page.setDefaultTimeout(10_000);
            page.setDefaultNavigationTimeout(10_000);
            try {
                page.navigate(site.indexUri() + "#mcp-resourcesTemplates");
                waitForLoadedProject(page, "mcp");
                page.waitForFunction(
                    "expected => Array.from(document.querySelectorAll(\"article.guide-document[data-project='mcp'] .quoteblock\"))" +
                        ".some((quote) => quote.innerText.includes(expected))",
                    RESOURCE_TEMPLATE_QUOTE
                );
                if (!(Boolean) page.evaluate("() => document.body.classList.contains('dark-mode')")) {
                    page.locator("[data-theme-switcher]").click();
                    page.waitForFunction("() => document.body.classList.contains('dark-mode')");
                }

                @SuppressWarnings("unchecked")
                List<String> quoteStyles = (List<String>) page.evaluate(
                    "expected => {" +
                        "const quote = Array.from(document.querySelectorAll(\"article.guide-document[data-project='mcp'] .quoteblock\"))" +
                            ".find((element) => element.innerText.includes(expected));" +
                        "const blockquote = quote?.querySelector('blockquote');" +
                        "const paragraph = quote?.querySelector('p');" +
                        "const blockquoteStyle = getComputedStyle(blockquote);" +
                        "const paragraphStyle = getComputedStyle(paragraph);" +
                        "return [" +
                            "paragraph?.innerText.trim() || ''," +
                            "paragraphStyle.color," +
                            "paragraphStyle.fontStyle," +
                            "blockquoteStyle.backgroundColor," +
                            "blockquoteStyle.borderTopColor," +
                            "blockquoteStyle.borderRadius" +
                        "];" +
                    "}",
                    RESOURCE_TEMPLATE_QUOTE
                );
                assertEquals(RESOURCE_TEMPLATE_QUOTE, quoteStyles.get(0));
                assertTrue(perceivedBrightness(quoteStyles.get(1)) > 180, "Resource template quote text must stay readable in dark mode: " + quoteStyles.get(1));
                assertEquals("normal", quoteStyles.get(2), "Resource template quote text should read like documentation, not a pull quote.");
                assertFalse("rgba(0, 0, 0, 0)".equals(quoteStyles.get(3)), "Resource template quote must use a visible dark-mode panel background.");
                assertTrue(perceivedBrightness(quoteStyles.get(4)) > 180, "Resource template quote border must be visible in dark mode: " + quoteStyles.get(4));
                assertTrue(quoteStyles.get(5).startsWith("8px"), "Resource template quote should use the platform rounded callout frame.");
            } catch (TimeoutError e) {
                throw new AssertionError("The MCP resource template quote was not rendered with readable dark-mode styles.", e);
            } finally {
                page.close();
            }
        }
    }

    @Test
    void fileUrlLoadsDocumentationThroughScriptFallback() throws IOException {
        assertRenderedSiteExists();
        try (Playwright playwright = createPlaywright();
             Browser browser = launchChromium(playwright)) {
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 900));
            page.setDefaultTimeout(10_000);
            page.setDefaultNavigationTimeout(10_000);
            try {
                page.navigate(INDEX_FILE.toUri().toString());
                page.waitForFunction("() => document.querySelector('[data-sidebar-menu]')?.dataset.menuLoaded === 'true'");

                page.locator("[data-project-option='core']").click();
                waitForLoadedProject(page, "core");
                assertNoVisibleDocumentationError(page, "core");

                page.locator(".topbar-title a[href='#platform']").click();
                page.waitForFunction("() => document.body.classList.contains('overview-active')");
                page.locator("[data-project-card='serde']").click();
                waitForLoadedProject(page, "serde");
                assertNoVisibleDocumentationError(page, "serde");
            } catch (TimeoutError e) {
                String state = (String) page.evaluate(
                    "() => Array.from(document.querySelectorAll('article.guide-document')).map((article) => {" +
                        "const error = article.querySelector('[data-document-error]');" +
                        "return `${article.dataset.project}: hidden=${article.hidden}, loaded=${article.dataset.documentLoaded || 'false'}, errorHidden=${error?.hidden}`;" +
                        "}).join('\\n')"
                );
                throw new AssertionError("File URL project documentation did not load through the script fallback.\n" + state, e);
            } finally {
                page.close();
            }
        }
    }

    @Test
    void httpDocumentFetchFailureLoadsDocumentationThroughScriptFallback() throws IOException {
        try (SiteServer site = serveRenderedSite("platform-assets/documents/core.html");
             Playwright playwright = createPlaywright();
             Browser browser = launchChromium(playwright)) {
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 900));
            page.setDefaultTimeout(10_000);
            page.setDefaultNavigationTimeout(10_000);
            try {
                page.navigate(site.indexUri().toString());
                page.waitForFunction("() => document.querySelector('[data-sidebar-menu]')?.dataset.menuLoaded === 'true'");

                page.locator("[data-project-card='core']").click();
                waitForLoadedProject(page, "core");
                assertNoVisibleDocumentationError(page, "core");
            } catch (TimeoutError e) {
                throw new AssertionError("Project documentation did not load through the HTTP script fallback.", e);
            } finally {
                page.close();
            }
        }
    }

    private static void assertRenderedSiteExists() {
        assertTrue(Files.isRegularFile(INDEX_FILE), "Render platform docs before running integration tests: " + INDEX_FILE);
    }

    private static String searchIndexJson() throws IOException {
        Path searchIndex = SITE_DIRECTORY.resolve("platform-assets/search-index.json");
        assertTrue(Files.isRegularFile(searchIndex), "The rendered search fixture must include a search index.");
        return Files.readString(searchIndex, StandardCharsets.UTF_8);
    }

    private static void assertOptionalReferenceEntries(String indexJson) {
        if (indexJson.contains("\"kind\":\"api-type\"")) {
            assertTrue(indexJson.contains("\"title\":\"ApplicationContext\""), "The search index must include API type titles.");
            assertTrue(indexJson.contains("ApplicationContext.html"), "The search index must link API classes into the reference sheet.");
        }
        if (indexJson.contains("\"kind\":\"configuration\"")) {
            assertTrue(indexJson.contains("nettydefaultallocatornumheaparenas"), "The search index must include compact configuration property terms.");
        }
    }

    private static boolean hasApiTypeEntries(String indexJson) {
        return indexJson.contains("\"kind\":\"api-type\"")
            && indexJson.contains("\"title\":\"ApplicationContext\"")
            && indexJson.contains("ApplicationContext.html");
    }

    private static boolean hasConfigurationPropertyEntries(String indexJson) {
        return indexJson.contains("\"kind\":\"configuration\"")
            && indexJson.contains(CONFIGURATION_PROPERTY)
            && indexJson.contains("nettydefaultallocatornumheaparenas");
    }

    private static SiteServer serveRenderedSite() throws IOException {
        return serveRenderedSite(new String[0]);
    }

    private static SiteServer serveRenderedSite(String... unavailableRelativePaths) throws IOException {
        assertRenderedSiteExists();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> serveStaticFile(exchange, SITE_DIRECTORY, Set.of(unavailableRelativePaths)));
        server.start();
        return new SiteServer(server, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/index.html"));
    }

    private static void serveStaticFile(HttpExchange exchange, Path root, Set<String> unavailableRelativePaths) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Path file = requestedFile(root, exchange.getRequestURI());
            if (!file.startsWith(root) || unavailableRelativePaths.contains(relativePath(root, file)) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", contentType(file));
            long length = Files.size(file);
            exchange.sendResponseHeaders(200, "HEAD".equals(method) ? -1 : length);
            if (!"HEAD".equals(method)) {
                Files.copy(file, exchange.getResponseBody());
            }
        }
    }

    private static Path requestedFile(Path root, URI requestUri) {
        String path = URLDecoder.decode(requestUri.getRawPath(), StandardCharsets.UTF_8);
        if (path.isBlank() || "/".equals(path)) {
            path = "/index.html";
        }
        return root.resolve(path.substring(1)).normalize();
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String contentType(Path file) throws IOException {
        String detected = Files.probeContentType(file);
        if (detected != null) {
            return detected;
        }
        String name = file.getFileName().toString();
        if (name.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static void search(Page page, String query) {
        openSearchDialog(page);
        page.locator("[data-search-input]").fill(query);
        page.waitForFunction(
            "query => {" +
                "const input = document.querySelector('[data-search-input]');" +
                "const result = document.querySelector('[data-search-result]');" +
                "return input?.value === query && result?.innerText.toLowerCase().includes(query.toLowerCase().split(/\\s+/)[0]);" +
                "}",
            query
        );
        visibleSearchResult(page);
    }

    private static void openSearchDialog(Page page) {
        boolean open = (Boolean) page.evaluate("() => !document.querySelector('[data-search-dialog]')?.hidden");
        if (open) {
            return;
        }
        page.locator("[data-search-trigger]").click();
        page.locator("[data-search-input]").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    private static Locator visibleSearchResult(Page page) {
        Locator firstResult = page.locator("[data-search-result]").first();
        firstResult.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return firstResult;
    }

    private static String searchResultTextContaining(Page page, String expectedText) {
        page.waitForFunction(
            "expected => Array.from(document.querySelectorAll('[data-search-result]')).some((element) => element.innerText.includes(expected))",
            expectedText
        );
        return (String) page.evaluate(
            "expected => Array.from(document.querySelectorAll('[data-search-result]')).find((element) => element.innerText.includes(expected))?.innerText || ''",
            expectedText
        );
    }

    private static void clickSearchResultContaining(Page page, String expectedText) {
        assertTrue((Boolean) page.evaluate(
            "expected => {" +
                "const result = Array.from(document.querySelectorAll('[data-search-result]')).find((element) => element.innerText.includes(expected));" +
                "if (!result) {" +
                "return false;" +
                "}" +
                "result.click();" +
                "return true;" +
                "}",
            expectedText
        ), "Expected a search result containing: " + expectedText);
    }

    private static void waitForLoadedProject(Page page, String project) {
        page.waitForFunction(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "const content = article?.querySelector('[data-document-content]');" +
                "return Boolean(article && !article.hidden && article.dataset.documentLoaded === 'true' && content?.childElementCount);" +
                "}",
            project
        );
    }

    private static void assertProjectStartsAtTop(Page page, String project) {
        page.waitForFunction(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "const topbar = document.querySelector('.topbar');" +
                "if (!article || article.hidden) {" +
                "return false;" +
                "}" +
                "const preferredTop = (topbar?.getBoundingClientRect().height || 0) + 16;" +
                "const minimumTop = (topbar?.getBoundingClientRect().height || 0) - 2;" +
                "const articleTop = article.getBoundingClientRect().top;" +
                "return articleTop >= minimumTop && articleTop <= preferredTop + 10;" +
                "}",
            project
        );
    }

    private static void assertTopbarProject(Page page, String expectedProject) {
        page.waitForFunction(
            "expectedProject => {" +
                "const project = document.querySelector('[data-topbar-project]');" +
                "return project && !project.hidden && project.innerText.trim() === expectedProject;" +
                "}",
            expectedProject
        );
    }

    private static void assertPageIndexListsAndNavigates(Page page, String project, String label, String sectionId) {
        assertPageIndexListsSection(page, project, label, sectionId);
        page.locator("[data-page-index-link][href='#" + sectionId + "']").click();
        page.waitForFunction(
            "sectionId => window.location.hash === '#' + sectionId && " +
                "document.querySelector(`[data-page-index-link][data-section='${sectionId}']`)?.classList.contains('active')",
            sectionId
        );
    }

    private static void assertPageIndexListsSection(Page page, String project, String label, String sectionId) {
        page.waitForFunction(
            "project => {" +
                "const pageIndex = document.querySelector('[data-page-index]');" +
                "return document.body.dataset.project === project && pageIndex && !pageIndex.hidden && " +
                    "getComputedStyle(pageIndex).display !== 'none';" +
                "}",
            project
        );
        Locator link = page.locator("[data-page-index-link][href='#" + sectionId + "']");
        link.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        assertEquals(label, link.innerText().trim());
        assertTrue(page.locator("[data-page-index]").innerText().contains("In this section"), "The page index must expose an In this section heading.");
    }

    private static void assertNoVisibleDocumentationError(Page page, String project) {
        assertTrue((Boolean) page.evaluate(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "const error = article?.querySelector('[data-document-error]');" +
                "if (!error?.hidden) {" +
                "return false;" +
                "}" +
                "const style = window.getComputedStyle(error);" +
                "return style.display === 'none';" +
                "}",
            project
        ), "Documentation error must stay hidden and visually absent for " + project);
    }

    private static void assertProjectHasHighlightedCode(Page page, String project) {
        page.waitForFunction(
                "project => {" +
                    "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "return Boolean(article?.querySelector('pre.shiki code .line span[style*=\"--shiki-light\"]'));" +
                "}",
            project
        );
    }

    private static void assertProjectHighlightsCodeWithCallouts(Page page, String project) {
        page.waitForFunction(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "return Array.from(article?.querySelectorAll('pre code') || []).some((code) => " +
                    "code.textContent.includes('HelloControllerSpec') && " +
                    "code.closest('pre')?.classList.contains('shiki') && " +
                    "code.querySelector('.line span[style*=\"--shiki-light\"]') && " +
                    "code.querySelector('i.conum'));" +
                "}",
            project
        );
    }

    private static void assertProjectFormatsCodeCalloutFooter(Page page, String project) {
        page.waitForFunction(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "const footer = Array.from(article?.querySelectorAll('.docs-snippet-card-footer') || [])" +
                    ".find((element) => element.innerText.includes('The EmbeddedServer is configured') " +
                    "&& element.innerText.includes('The retrieve method returns the controller response'));" +
                "const items = Array.from(footer?.querySelectorAll('ol > li') || []);" +
                "if (!footer || items.length < 4) {" +
                "return false;" +
                "}" +
                "const footerStyle = getComputedStyle(footer);" +
                "const listStyle = getComputedStyle(footer.querySelector('ol'));" +
                "const firstItemStyle = getComputedStyle(items[0]);" +
                "const firstMarkerStyle = getComputedStyle(items[0], '::before');" +
                "const firstParagraphStyle = getComputedStyle(items[0].querySelector('p'));" +
                "return footer.classList.contains('docs-code-callouts') " +
                    "&& footerStyle.borderBottomLeftRadius.startsWith('12px') " +
                    "&& footerStyle.paddingTop === footerStyle.paddingBottom " +
                    "&& listStyle.listStyleType === 'none' " +
                    "&& firstItemStyle.display === 'grid' " +
                    "&& firstMarkerStyle.width === '20px' " +
                    "&& firstParagraphStyle.marginTop === '0px' " +
                    "&& firstParagraphStyle.marginBottom === '0px';" +
                "}",
            project
        );
    }

    private static void assertProjectCodeTitlesAreOutsideFrames(Page page, String project) {
        page.waitForFunction(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "const titles = Array.from(article?.querySelectorAll('.docs-code-title') || []);" +
                "return titles.length > 0 && titles.every((title) => !title.closest('.docs-code-block'));" +
                "}",
            project
        );
    }

    private static void assertProjectHasMultiLanguageToolbarTabs(Page page, String project) {
        page.waitForFunction(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "const tabGroups = Array.from(article?.querySelectorAll('.docs-code-tabs-multi') || []);" +
                "return tabGroups.some((tabs) => tabs.querySelectorAll('[role=tab][data-lang]').length > 1)" +
                    " && Boolean(article?.querySelector('.docs-code-tabs-multi [role=tab][data-lang=java]'))" +
                    " && Boolean(article?.querySelector('.docs-code-tabs-multi [role=tab][data-lang=kotlin]'));" +
                "}",
            project
        );
        assertTrue((Boolean) page.evaluate(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "return Array.from(article?.querySelectorAll('.docs-code-toolbar') || []).some((toolbar) => " +
                    "toolbar.querySelector('.docs-code-tabs-multi') && !toolbar.querySelector('.docs-code-language-single'));" +
                "}",
            project
        ), "Multi-language code toolbars must use tabs instead of a single language label.");
        assertTrue((Boolean) page.evaluate(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "return Array.from(article?.querySelectorAll('.multi-language-selector') || []).every((selector) => " +
                    "getComputedStyle(selector).display === 'none');" +
                "}",
            project
        ), "Legacy multi-language selectors must stay hidden when toolbar tabs are used.");
        assertTrue((Boolean) page.evaluate(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "return Array.from(article?.querySelectorAll('.docs-code-block:not(.multi-language-sample)') || []).some((block) => " +
                    "!block.querySelector(':scope > .docs-code-toolbar') && " +
                    "block.querySelector(':scope > .docs-code-copy') && " +
                    "block.querySelector(':scope > .docs-code-copy + .docs-code-content'));" +
                "}",
            project
        ), "Single-language code blocks must use an overlay copy button without a top toolbar.");
    }

    private static void assertProjectDependencyTabsAreScopedToOneGroup(Page page, String project) {
        page.waitForFunction(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "const ids = Array.from(new Set(Array.from(article?.querySelectorAll('.docs-code-tabs-multi[data-source-selector]') || [])" +
                    ".map((tabs) => tabs.dataset.sourceSelector)))" +
                    ".filter((id) => {" +
                        "const selector = article.querySelector(`[data-docs-tabs-id=\"${id}\"]`);" +
                        "const languages = Array.from(selector?.querySelectorAll('.language-option') || []).map((option) => option.getAttribute('data-lang'));" +
                        "return languages.includes('gradle') && languages.includes('maven');" +
                    "});" +
                "return ids.length >= 2;" +
            "}",
            project
        );
        assertTrue((Boolean) page.evaluate(
            "project => {" +
                "const article = document.querySelector(`article.guide-document[data-project='${project}']`);" +
                "const ids = Array.from(new Set(Array.from(article?.querySelectorAll('.docs-code-tabs-multi[data-source-selector]') || [])" +
                    ".map((tabs) => tabs.dataset.sourceSelector)))" +
                    ".filter((id) => {" +
                        "const selector = article.querySelector(`[data-docs-tabs-id=\"${id}\"]`);" +
                        "const languages = Array.from(selector?.querySelectorAll('.language-option') || []).map((option) => option.getAttribute('data-lang'));" +
                        "return languages.includes('gradle') && languages.includes('maven');" +
                    "});" +
                "const visibleLanguage = (id) => {" +
                    "const selector = article.querySelector(`[data-docs-tabs-id=\"${id}\"]`);" +
                    "let sample = selector?.nextElementSibling;" +
                    "while (sample?.classList?.contains('multi-language-sample')) {" +
                        "if (!sample.classList.contains('hidden')) {" +
                            "return sample.getAttribute('data-lang');" +
                        "}" +
                        "sample = sample.nextElementSibling;" +
                    "}" +
                    "return '';" +
                "};" +
                "const selectedLanguage = (id) => Array.from(new Set(Array.from(article.querySelectorAll(`[data-source-selector=\"${id}\"] [role=tab].selected`))" +
                    ".map((tab) => tab.getAttribute('data-lang'))));" +
                "const first = ids[0];" +
                "const second = ids[1];" +
                "if (visibleLanguage(first) !== 'gradle' || visibleLanguage(second) !== 'gradle') {" +
                    "return false;" +
                "}" +
                "article.querySelector(`[data-source-selector=\"${first}\"] [data-lang=\"maven\"]`)?.click();" +
                "const firstSelected = selectedLanguage(first);" +
                "const secondSelected = selectedLanguage(second);" +
                "return visibleLanguage(first) === 'maven'" +
                    " && visibleLanguage(second) === 'gradle'" +
                    " && firstSelected.length === 1 && firstSelected[0] === 'maven'" +
                    " && secondSelected.length === 1 && secondSelected[0] === 'gradle';" +
            "}",
            project
        ), "Switching one dependency tab must not switch other dependency tab groups.");
    }

    private static double perceivedBrightness(String cssColor) {
        int start = cssColor.indexOf('(');
        int end = cssColor.indexOf(')');
        if (start < 0 || end < 0 || end <= start) {
            return 0;
        }
        String[] components = cssColor.substring(start + 1, end).split(",");
        if (components.length < 3) {
            return 0;
        }
        double red = Double.parseDouble(components[0].trim());
        double green = Double.parseDouble(components[1].trim());
        double blue = Double.parseDouble(components[2].trim());
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }

    private static Playwright createPlaywright() {
        return Playwright.create(new Playwright.CreateOptions()
            .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
    }

    private static Browser launchChromium(Playwright playwright) {
        RuntimeException executableFailure = null;
        for (Path executable : browserExecutables()) {
            try {
                return playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setExecutablePath(executable)
                    .setHeadless(true)
                    .setTimeout(10_000));
            } catch (RuntimeException e) {
                if (executableFailure == null) {
                    executableFailure = e;
                } else {
                    executableFailure.addSuppressed(e);
                }
            }
        }
        try {
            return playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setTimeout(10_000));
        } catch (RuntimeException bundledFailure) {
            try {
                return playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setChannel(BROWSER_CHANNEL)
                    .setHeadless(true)
                    .setTimeout(10_000));
            } catch (RuntimeException channelFailure) {
                channelFailure.addSuppressed(bundledFailure);
                if (executableFailure != null) {
                    channelFailure.addSuppressed(executableFailure);
                }
                if (isMacBrowserSandboxFailure(channelFailure)
                    || isMacBrowserSandboxFailure(bundledFailure)
                    || isMacBrowserSandboxFailure(executableFailure)) {
                    throw new TestAbortedException("Playwright Chromium cannot launch in this macOS sandbox.", channelFailure);
                }
                throw new AssertionError("Unable to launch Playwright Chromium. Install Playwright Chromium or set -DplatformDocs.browserChannel to an available browser channel.", channelFailure);
            }
        }
    }

    private static boolean isMacBrowserSandboxFailure(Throwable failure) {
        if (failure == null) {
            return false;
        }
        String message = String.valueOf(failure.getMessage());
        if (message.contains("bootstrap_check_in") && message.contains("Permission denied (1100)")) {
            return true;
        }
        if (isMacBrowserSandboxFailure(failure.getCause())) {
            return true;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (isMacBrowserSandboxFailure(suppressed)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> browserExecutables() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        String configuredExecutable = System.getProperty("platformDocs.browserExecutable", "");
        if (!configuredExecutable.isBlank()) {
            candidates.add(Path.of(configuredExecutable));
        }

        candidates.add(Path.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
        candidates.add(Path.of("/usr/bin/google-chrome"));
        candidates.add(Path.of("/usr/bin/google-chrome-stable"));
        candidates.add(Path.of("/usr/bin/chromium"));
        candidates.add(Path.of("/usr/bin/chromium-browser"));

        Path playwrightCache = Path.of(System.getProperty("user.home"), "Library", "Caches", "ms-playwright");
        if (Files.isDirectory(playwrightCache)) {
            try (Stream<Path> paths = Files.list(playwrightCache)) {
                paths.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .forEach(path -> addPlaywrightCacheExecutables(candidates, path));
            } catch (Exception ignored) {
                // Fall through to the system browser candidates.
            }
        }

        return candidates.stream()
            .filter(Files::isRegularFile)
            .filter(Files::isExecutable)
            .toList();
    }

    private static void addPlaywrightCacheExecutables(LinkedHashSet<Path> candidates, Path cacheDirectory) {
        candidates.add(cacheDirectory.resolve("chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"));
        candidates.add(cacheDirectory.resolve("chrome-mac/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing"));
        candidates.add(cacheDirectory.resolve("chrome-headless-shell-mac-arm64/chrome-headless-shell"));
        candidates.add(cacheDirectory.resolve("chrome-headless-shell-mac/chrome-headless-shell"));
    }

    private record SiteServer(HttpServer server, URI indexUri) implements AutoCloseable {

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
