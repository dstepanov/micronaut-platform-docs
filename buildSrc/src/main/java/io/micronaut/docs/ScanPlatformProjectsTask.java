package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ScanPlatformProjectsTask extends DefaultTask {
    private static final Pattern SUBMODULE_HEADER = Pattern.compile("^\\s*\\[submodule \"([^\"]+)\"]\\s*$");
    private static final Pattern SUBMODULE_PROPERTY = Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*=\\s*(.+?)\\s*$");
    private static final Pattern REPOSITORY_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CREATED_AT = Pattern.compile("\"created_at\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern VERSION_BRANCH = Pattern.compile("^(\\d+)\\.(\\d+)\\..*$");
    private static final Map<String, String> REPOSITORY_OVERRIDES = Map.of(
        "mongo", "micronaut-mongodb",
        "oraclecloud", "micronaut-oracle-cloud",
        "serialization", "micronaut-serialization"
    );
    private static final Set<String> EXCLUDED_PROJECT_KEYS = Set.of("crac", "guides");
    private static final Set<String> UPPERCASE_WORDS = Set.of(
        "acme",
        "aot",
        "aws",
        "gcp",
        "grpc",
        "jms",
        "jmx",
        "json",
        "mcp",
        "mqtt",
        "nats",
        "r2dbc",
        "rss",
        "sql",
        "xml"
    );

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPlatformVersionCatalog();

    @OutputFile
    public abstract RegularFileProperty getProjectManifest();

    @OutputFile
    public abstract RegularFileProperty getRepositoryMetadata();

    @TaskAction
    public void scan() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        Path platformVersionCatalog = getPlatformVersionCatalog().get().getAsFile().toPath();
        Path projectManifest = getProjectManifest().get().getAsFile().toPath();
        Path repositoryMetadata = getRepositoryMetadata().get().getAsFile().toPath();
        getLogger().quiet("Scanning Micronaut Platform catalog: {}", projectDirectory.relativize(platformVersionCatalog));

        Map<String, Submodule> submodules = readSubmodules(projectDirectory.resolve(".gitmodules"));
        RepositoryMetadataCache metadataCache = RepositoryMetadataCache.read(repositoryMetadata, projectManifest);
        getLogger().quiet(
            "Loaded {} cached repository metadata entries from {}.",
            metadataCache.size(),
            projectDirectory.relativize(repositoryMetadata)
        );
        Map<String, PlatformVersions.PlatformProjectVersion> platformProjects = PlatformVersions.readMicronautBomProjects(
            platformVersionCatalog
        );
        List<PlatformVersions.PlatformProjectVersion> includedProjects = platformProjects.values()
            .stream()
            .filter(platformProject -> !EXCLUDED_PROJECT_KEYS.contains(platformProject.projectKey()))
            .toList();
        List<PlatformVersions.PlatformProjectVersion> excludedProjects = platformProjects.values()
            .stream()
            .filter(platformProject -> EXCLUDED_PROJECT_KEYS.contains(platformProject.projectKey()))
            .toList();
        getLogger().quiet(
            "Found {} managed Micronaut projects, selected {} docs projects, and {} configured submodules.",
            platformProjects.size(),
            includedProjects.size(),
            submodules.size()
        );
        if (!excludedProjects.isEmpty()) {
            getLogger().quiet(
                "Excluded from docs aggregation: {}.",
                excludedProjects.stream().map(PlatformVersions.PlatformProjectVersion::projectKey).toList()
            );
        }

        RepositoryCreationDateResolver repositoryCreationDates = new RepositoryCreationDateResolver(metadataCache.creationDates());

        List<GuideProject> projects = new ArrayList<>();
        int index = 0;
        int unknownCreatedAt = 0;
        for (PlatformVersions.PlatformProjectVersion platformProject : includedProjects) {
            RepositoryMetadata metadata = resolveRepository(projectDirectory, submodules, metadataCache, repositoryCreationDates, platformProject);
            RepositoryMetadataCache.Entry cachedMetadata = metadataCache.byName(metadata.name()).orElse(RepositoryMetadataCache.Entry.fromName(metadata.name()));
            String repositoryUrl = choose(cachedMetadata.url(), repositoryUrl(metadata.name()));
            Submodule submodule = submodules.get(normalizedRepositoryUrl(repositoryUrl));
            String submodulePath = choose(cachedMetadata.submodulePath(), submodule == null ? "repos/" + metadata.name() : submodule.path());
            String branch = submodule == null || submodule.branch().isBlank() ? branchFor(platformProject.version()) : submodule.branch();
            String repositoryCreatedAt = metadata.createdAt();
            if (GuideProject.UNKNOWN_REPOSITORY_CREATED_AT.equals(repositoryCreatedAt)) {
                unknownCreatedAt++;
            }
            String displayName = choose(cachedMetadata.displayName(), displayName(metadata.name()));
            String slug = choose(cachedMetadata.slug(), slugFromSubmodulePath(submodulePath));
            String publishedGuideUrl = choose(cachedMetadata.publishedGuideUrl(), publishedGuideUrl(metadata.name()));
            getLogger().quiet(
                "[{}/{}] {} -> {} ({}, {})",
                ++index,
                includedProjects.size(),
                displayName,
                metadata.name(),
                branch,
                repositoryCreatedAt
            );
            projects.add(new GuideProject(
                slug,
                displayName,
                platformProject.projectKey(),
                platformProject.module(),
                metadata.name(),
                publishedGuideUrl,
                repositoryUrl,
                branch,
                submodulePath,
                platformProject.versionRef(),
                repositoryCreatedAt
            ));
        }

        GuideProject.writeManifest(
            projectManifest,
            GuideProject.sortByRepositoryAge(projects)
        );
        RepositoryMetadataCache.write(repositoryMetadata, GuideProject.sortByRepositoryAge(projects));
        getLogger().quiet(
            "Wrote {} projects to {} and {} cached repository metadata entries to {} ({} unresolved repository dates).",
            projects.size(),
            projectDirectory.relativize(projectManifest),
            projects.size(),
            projectDirectory.relativize(repositoryMetadata),
            unknownCreatedAt
        );
    }

    private RepositoryMetadata resolveRepository(
        Path projectDirectory,
        Map<String, Submodule> submodules,
        RepositoryMetadataCache metadataCache,
        RepositoryCreationDateResolver repositoryCreationDates,
        PlatformVersions.PlatformProjectVersion project
    ) throws IOException, InterruptedException {
        List<String> candidates = repositoryNameCandidates(project, metadataCache);
        for (String name : candidates) {
            Submodule submodule = submodules.get(normalizedRepositoryUrl(repositoryUrl(name)));
            if (submodule != null) {
                Optional<String> createdAt = repositoryCreationDates.cached(name);
                if (createdAt.isEmpty()) {
                    createdAt = localRepositoryCreatedAt(projectDirectory.resolve(submodule.path()));
                }
                if (createdAt.isEmpty()) {
                    createdAt = repositoryCreationDates.github(name);
                }
                repositoryCreationDates.put(name, createdAt.orElse(GuideProject.UNKNOWN_REPOSITORY_CREATED_AT));
                return new RepositoryMetadata(name, createdAt.orElse(GuideProject.UNKNOWN_REPOSITORY_CREATED_AT));
            }
        }
        for (String name : candidates) {
            Optional<String> createdAt = repositoryCreationDates.cached(name);
            if (createdAt.isPresent()) {
                repositoryCreationDates.put(name, createdAt.get());
                return new RepositoryMetadata(name, createdAt.get());
            }
        }
        for (String name : candidates) {
            Optional<String> createdAt = localRepositoryCreatedAt(projectDirectory.resolve("repos").resolve(name));
            if (createdAt.isPresent()) {
                repositoryCreationDates.put(name, createdAt.get());
                return new RepositoryMetadata(name, createdAt.get());
            }
        }
        for (String name : candidates) {
            Optional<String> createdAt = repositoryCreationDates.github(name);
            if (createdAt.isPresent()) {
                return new RepositoryMetadata(name, createdAt.get());
            }
        }
        return new RepositoryMetadata(candidates.get(0), GuideProject.UNKNOWN_REPOSITORY_CREATED_AT);
    }

    private static List<String> repositoryNameCandidates(
        PlatformVersions.PlatformProjectVersion project,
        RepositoryMetadataCache metadataCache
    ) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        metadataCache.byProjectKey(project.projectKey()).map(RepositoryMetadataCache.Entry::name).ifPresent(names::add);
        metadataCache.byModule(project.module()).map(RepositoryMetadataCache.Entry::name).ifPresent(names::add);
        String projectKey = project.projectKey();
        String override = REPOSITORY_OVERRIDES.get(projectKey);
        if (override != null) {
            names.add(override);
        }
        names.add(stripBomSuffix(project.artifactId()));
        names.add("micronaut-" + projectKey);
        names.add(project.alias().substring("boms-".length()));
        return List.copyOf(names);
    }

    private static Map<String, String> githubOrganizationRepositoryCreationDates(HttpClient client) {
        Map<String, String> repositories = new LinkedHashMap<>();
        for (int page = 1; page <= 5; page++) {
            HttpRequest request = githubRequest("https://api.github.com/orgs/micronaut-projects/repos?type=public&per_page=100&page=" + page);
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    break;
                }
                List<String> objects = jsonObjects(response.body());
                if (objects.isEmpty()) {
                    break;
                }
                for (String object : objects) {
                    Optional<String> name = firstMatch(REPOSITORY_NAME, object);
                    Optional<String> createdAt = firstMatch(CREATED_AT, object);
                    if (name.isPresent() && createdAt.isPresent()) {
                        repositories.put(name.get(), createdAt.get());
                    }
                }
                if (objects.size() < 100) {
                    break;
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
        return repositories;
    }

    private static Optional<String> githubRepositoryCreatedAt(HttpClient client, String repositoryName) {
        HttpRequest request = githubRequest("https://api.github.com/repos/micronaut-projects/" + repositoryName);
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return firstMatch(CREATED_AT, response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return Optional.empty();
    }

    private static HttpRequest githubRequest(String uri) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "micronaut-platform-docs")
            .timeout(Duration.ofSeconds(12))
            .GET();
        String githubToken = githubToken();
        if (githubToken != null && !githubToken.isBlank()) {
            request.header("Authorization", "Bearer " + githubToken);
        }
        return request.build();
    }

    private static String githubToken() {
        String githubToken = System.getenv("GITHUB_TOKEN");
        if (githubToken != null && !githubToken.isBlank()) {
            return githubToken;
        }
        githubToken = System.getenv("GH_TOKEN");
        return githubToken == null || githubToken.isBlank() ? null : githubToken;
    }

    private static void putCreationDate(Map<String, String> creationDates, String repositoryName, String createdAt) {
        if (repositoryName == null || repositoryName.isBlank() || createdAt == null || createdAt.isBlank()) {
            return;
        }
        if (GuideProject.UNKNOWN_REPOSITORY_CREATED_AT.equals(createdAt)) {
            return;
        }
        creationDates.put(repositoryName, createdAt);
    }

    private static String repositoryUrl(String repositoryName) {
        return "https://github.com/micronaut-projects/" + repositoryName + ".git";
    }

    private static String choose(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Optional<String> firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static List<String> jsonObjects(String jsonArray) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int objectStart = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < jsonArray.length(); i++) {
            char ch = jsonArray.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    objects.add(jsonArray.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }
        return objects;
    }

    private static String stripBomSuffix(String artifactId) {
        if (artifactId.endsWith("-bom")) {
            return artifactId.substring(0, artifactId.length() - "-bom".length());
        }
        return artifactId;
    }

    private static String publishedGuideUrl(String repositoryName) {
        if ("micronaut-core".equals(repositoryName)) {
            return "https://docs.micronaut.io/latest/guide/";
        }
        return "https://micronaut-projects.github.io/" + repositoryName + "/latest/guide/";
    }

    private static String branchFor(String version) {
        Matcher matcher = VERSION_BRANCH.matcher(version);
        if (matcher.matches()) {
            return matcher.group(1) + "." + matcher.group(2) + ".x";
        }
        return "master";
    }

    private static String slugFromSubmodulePath(String submodulePath) {
        String name = Path.of(submodulePath).getFileName().toString();
        if (name.startsWith("micronaut-")) {
            return name.substring("micronaut-".length());
        }
        return name;
    }

    private static String displayName(String repositoryName) {
        String name = repositoryName;
        if (name.startsWith("micronaut-")) {
            name = name.substring("micronaut-".length());
        }
        List<String> words = new ArrayList<>();
        words.add("Micronaut");
        for (String word : name.split("-")) {
            words.add(displayWord(word));
        }
        return String.join(" ", words);
    }

    private static String displayWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (UPPERCASE_WORDS.contains(lower)) {
            return lower.toUpperCase(Locale.ROOT);
        }
        if ("crac".equals(lower)) {
            return "CRaC";
        }
        if ("picocli".equals(lower)) {
            return "Picocli";
        }
        if ("mongodb".equals(lower)) {
            return "MongoDB";
        }
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private static Optional<String> localRepositoryCreatedAt(Path submoduleDirectory) throws IOException, InterruptedException {
        if (!Files.isDirectory(submoduleDirectory.resolve(".git")) && !Files.isRegularFile(submoduleDirectory.resolve(".git"))) {
            return Optional.empty();
        }
        String roots = GitSupport.run(submoduleDirectory, "rev-list", "--max-parents=0", "HEAD").trim();
        Optional<String> oldest = Optional.empty();
        for (String root : roots.lines().toList()) {
            if (root.isBlank()) {
                continue;
            }
            String createdAt = GitSupport.run(submoduleDirectory, "show", "-s", "--format=%cI", root).trim();
            if (oldest.isEmpty() || createdAt.compareTo(oldest.get()) < 0) {
                oldest = Optional.of(createdAt);
            }
        }
        return oldest;
    }

    private static Map<String, Submodule> readSubmodules(Path gitmodules) throws IOException {
        Map<String, Submodule> submodules = new LinkedHashMap<>();
        if (!Files.isRegularFile(gitmodules)) {
            return submodules;
        }

        String currentName = "";
        Map<String, String> currentProperties = new LinkedHashMap<>();
        for (String line : Files.readAllLines(gitmodules, StandardCharsets.UTF_8)) {
            Matcher header = SUBMODULE_HEADER.matcher(line);
            if (header.matches()) {
                appendSubmodule(submodules, currentName, currentProperties);
                currentName = header.group(1);
                currentProperties = new LinkedHashMap<>();
                continue;
            }
            Matcher property = SUBMODULE_PROPERTY.matcher(line);
            if (property.matches()) {
                currentProperties.put(property.group(1), property.group(2));
            }
        }
        appendSubmodule(submodules, currentName, currentProperties);
        return submodules;
    }

    private static void appendSubmodule(Map<String, Submodule> submodules, String name, Map<String, String> properties) {
        String url = properties.get("url");
        String path = properties.get("path");
        if (name.isBlank() || url == null || path == null) {
            return;
        }
        submodules.put(normalizedRepositoryUrl(url), new Submodule(path, url, properties.getOrDefault("branch", "")));
    }

    private static String normalizedRepositoryUrl(String url) {
        String normalized = url.trim();
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - ".git".length());
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private final class RepositoryCreationDateResolver {
        private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        private final Map<String, String> creationDates;
        private Map<String, String> githubCreationDates = Map.of();
        private boolean resolvedGithubOrganization;

        private RepositoryCreationDateResolver(Map<String, String> creationDates) {
            this.creationDates = new LinkedHashMap<>(creationDates);
        }

        private Optional<String> cached(String repositoryName) {
            return Optional.ofNullable(creationDates.get(repositoryName));
        }

        private Optional<String> github(String repositoryName) {
            if (!resolvedGithubOrganization) {
                getLogger().quiet(
                    "Resolving missing repository creation dates from GitHub API{}.",
                    githubToken() == null ? "" : " with token authentication"
                );
                githubCreationDates = githubOrganizationRepositoryCreationDates(client);
                resolvedGithubOrganization = true;
                getLogger().quiet("Resolved creation dates for {} GitHub repositories.", githubCreationDates.size());
            }
            String createdAt = githubCreationDates.get(repositoryName);
            if (createdAt == null) {
                Optional<String> repositoryCreatedAt = githubRepositoryCreatedAt(client, repositoryName);
                if (repositoryCreatedAt.isPresent()) {
                    createdAt = repositoryCreatedAt.get();
                }
            }
            if (createdAt == null) {
                return Optional.empty();
            }
            put(repositoryName, createdAt);
            return Optional.of(createdAt);
        }

        private void put(String repositoryName, String createdAt) {
            putCreationDate(creationDates, repositoryName, createdAt);
        }

        private Map<String, String> creationDates() {
            return creationDates;
        }
    }

    private record RepositoryMetadata(String name, String createdAt) {
    }

    private record Submodule(String path, String url, String branch) {
    }
}
