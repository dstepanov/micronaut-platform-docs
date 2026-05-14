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

    @Test
    void searchFixtureContainsCoreSerdeAndRequiredIndexTerms() throws IOException {
        assertRenderedSiteExists();
        String html = Files.readString(INDEX_FILE, StandardCharsets.UTF_8);
        assertTrue(html.contains("data-project=\"core\""), "The rendered search fixture must include Micronaut Core.");
        assertTrue(html.contains("data-project=\"serde\""), "The rendered search fixture must include Micronaut Serialization.");
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

        Path searchIndex = SITE_DIRECTORY.resolve("platform-assets/search-index.json");
        assertTrue(Files.isRegularFile(searchIndex), "The rendered search fixture must include a search index.");
        String indexJson = Files.readString(searchIndex, StandardCharsets.UTF_8);
        assertTrue(indexJson.contains("applicationcontext"), "The search index must include compact API type terms.");
        assertTrue(indexJson.contains("nettydefaultallocatornumheaparenas"), "The search index must include compact configuration property terms.");
        assertTrue(indexJson.contains("\"project\":\"serde\""), "The search index must include serde entries.");
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

                search(page, "ApplicationContext");
                Locator firstResult = visibleSearchResult(page);
                assertTrue(firstResult.innerText().contains("Documentation"), "Expected a documentation search result.");
                clickSearchResultContaining(page, "Application Context");
                page.waitForFunction("() => window.location.hash.length > 1 && document.body.dataset.project");
                assertFalse(((String) page.evaluate("() => document.body.dataset.project")).isBlank());
                page.waitForFunction("() => Boolean(document.querySelector('.platform-search-target'))");

                search(page, "Micronaut Serialization");
                clickSearchResultContaining(page, "Micronaut Serialization");
                page.waitForFunction("() => document.body.dataset.project === 'serde'");

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
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 900));
            page.setDefaultTimeout(10_000);
            page.setDefaultNavigationTimeout(10_000);
            try {
                page.navigate(site.indexUri().toString());
                page.waitForFunction("() => document.querySelector('[data-sidebar-menu]')?.dataset.menuLoaded === 'true'");

                page.locator("[data-project-option='core']").click();
                waitForLoadedProject(page, "core");
                assertNoVisibleDocumentationError(page, "core");
                assertEquals("core", page.evaluate("() => document.body.dataset.project"));

                page.locator(".topbar-title a[href='#platform']").click();
                page.waitForFunction("() => document.body.classList.contains('overview-active')");
                page.locator("[data-project-card='serde']").click();
                waitForLoadedProject(page, "serde");
                assertNoVisibleDocumentationError(page, "serde");
                assertEquals("serde", page.evaluate("() => document.body.dataset.project"));
            } catch (TimeoutError e) {
                throw new AssertionError("Project documentation did not load after clicking the sidebar project or overview card.", e);
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
        page.locator("[data-search-input]").fill(query);
        visibleSearchResult(page);
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

    private static Playwright createPlaywright() {
        if (System.getProperty("platformDocs.browserExecutable", "").isBlank()) {
            return Playwright.create();
        }
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
