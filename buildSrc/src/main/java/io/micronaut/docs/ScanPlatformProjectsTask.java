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
    private static final String UNKNOWN_CREATED_AT = "9999-12-31T23:59:59Z";
    private static final Map<String, String> REPOSITORY_OVERRIDES = Map.of(
        "oraclecloud", "micronaut-oracle-cloud",
        "serialization", "micronaut-serialization"
    );
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

    @TaskAction
    public void scan() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        Path platformVersionCatalog = getPlatformVersionCatalog().get().getAsFile().toPath();
        Path projectManifest = getProjectManifest().get().getAsFile().toPath();
        getLogger().quiet("Scanning Micronaut Platform catalog: {}", projectDirectory.relativize(platformVersionCatalog));

        Map<String, Submodule> submodules = readSubmodules(projectDirectory.resolve(".gitmodules"));
        Map<String, PlatformVersions.PlatformProjectVersion> platformProjects = PlatformVersions.readMicronautBomProjects(
            platformVersionCatalog
        );
        getLogger().quiet("Found {} managed Micronaut projects and {} configured submodules.", platformProjects.size(), submodules.size());

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        getLogger().quiet("Resolving repository creation dates from GitHub API{}.", githubToken() == null ? "" : " with token authentication");
        Map<String, String> repositoryCreationDates = githubOrganizationRepositoryCreationDates(client);
        getLogger().quiet("Resolved creation dates for {} GitHub repositories.", repositoryCreationDates.size());

        List<GuideProject> projects = new ArrayList<>();
        int index = 0;
        int unknownCreatedAt = 0;
        for (PlatformVersions.PlatformProjectVersion platformProject : platformProjects.values()) {
            RepositoryMetadata metadata = resolveRepository(client, repositoryCreationDates, platformProject);
            String repositoryUrl = "https://github.com/micronaut-projects/" + metadata.name() + ".git";
            Submodule submodule = submodules.get(normalizedRepositoryUrl(repositoryUrl));
            String submodulePath = submodule == null ? "repos/" + metadata.name() : submodule.path();
            String branch = submodule == null || submodule.branch().isBlank() ? branchFor(platformProject.version()) : submodule.branch();
            String repositoryCreatedAt = metadata.createdAt();
            if (UNKNOWN_CREATED_AT.equals(repositoryCreatedAt)) {
                repositoryCreatedAt = localRepositoryCreatedAt(projectDirectory.resolve(submodulePath)).orElse(UNKNOWN_CREATED_AT);
            }
            if (UNKNOWN_CREATED_AT.equals(repositoryCreatedAt)) {
                unknownCreatedAt++;
            }
            String displayName = displayName(metadata.name());
            getLogger().quiet(
                "[{}/{}] {} -> {} ({}, {})",
                ++index,
                platformProjects.size(),
                displayName,
                metadata.name(),
                branch,
                repositoryCreatedAt
            );
            projects.add(new GuideProject(
                slugFromSubmodulePath(submodulePath),
                displayName,
                publishedGuideUrl(metadata.name()),
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
        getLogger().quiet(
            "Wrote {} projects to {} ({} unresolved repository dates).",
            projects.size(),
            projectDirectory.relativize(projectManifest),
            unknownCreatedAt
        );
    }

    private static RepositoryMetadata resolveRepository(
        HttpClient client,
        Map<String, String> repositoryCreationDates,
        PlatformVersions.PlatformProjectVersion project
    ) {
        for (String name : repositoryNameCandidates(project)) {
            String createdAt = repositoryCreationDates.get(name);
            if (createdAt != null) {
                return new RepositoryMetadata(name, createdAt);
            }
        }
        for (String name : repositoryNameCandidates(project)) {
            Optional<String> createdAt = githubRepositoryCreatedAt(client, name);
            if (createdAt.isPresent()) {
                return new RepositoryMetadata(name, createdAt.get());
            }
        }
        return new RepositoryMetadata(repositoryNameCandidates(project).get(0), UNKNOWN_CREATED_AT);
    }

    private static List<String> repositoryNameCandidates(PlatformVersions.PlatformProjectVersion project) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
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

    private record RepositoryMetadata(String name, String createdAt) {
    }

    private record Submodule(String path, String url, String branch) {
    }
}
