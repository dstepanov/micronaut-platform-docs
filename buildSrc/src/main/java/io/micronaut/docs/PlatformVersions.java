package io.micronaut.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlatformVersions {
    private static final Pattern VERSION_ENTRY = Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*=\\s*\"([^\"]+)\"", Pattern.MULTILINE);
    private static final Pattern MICRONAUT_BOM_ENTRY = Pattern.compile("^\\s*(boms-micronaut-[A-Za-z0-9_.-]+)\\s*=\\s*\\{([^}]*)}", Pattern.MULTILINE);
    private static final Pattern MODULE_ATTRIBUTE = Pattern.compile("\\bmodule\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern VERSION_REF_ATTRIBUTE = Pattern.compile("\\bversion\\.ref\\s*=\\s*\"([^\"]+)\"");

    private PlatformVersions() {
    }

    static Map<String, String> read(Path versionCatalog) throws IOException {
        String toml = Files.readString(versionCatalog, StandardCharsets.UTF_8);
        Matcher matcher = VERSION_ENTRY.matcher(toml);
        Map<String, String> versions = new LinkedHashMap<>();
        while (matcher.find()) {
            versions.put(matcher.group(1), matcher.group(2));
        }
        return versions;
    }

    static Map<String, PlatformProjectVersion> readMicronautBomProjects(Path versionCatalog) throws IOException {
        String toml = Files.readString(versionCatalog, StandardCharsets.UTF_8);
        Map<String, String> versions = read(versionCatalog);
        Matcher matcher = MICRONAUT_BOM_ENTRY.matcher(toml);
        Map<String, PlatformProjectVersion> projects = new LinkedHashMap<>();
        while (matcher.find()) {
            String alias = matcher.group(1);
            String attributes = matcher.group(2);
            String module = requiredAttribute(MODULE_ATTRIBUTE, attributes, alias, "module");
            String versionRef = requiredAttribute(VERSION_REF_ATTRIBUTE, attributes, alias, "version.ref");
            if (!versionRef.startsWith("managed-micronaut-")) {
                continue;
            }
            String version = versions.get(versionRef);
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("Platform catalog does not contain " + versionRef + " referenced by " + alias);
            }
            projects.putIfAbsent(versionRef, new PlatformProjectVersion(alias, module, versionRef, version));
        }
        return projects;
    }

    private static String requiredAttribute(Pattern pattern, String attributes, String alias, String attribute) {
        Matcher matcher = pattern.matcher(attributes);
        if (!matcher.find()) {
            throw new IllegalStateException("Platform catalog entry " + alias + " does not define " + attribute + ".");
        }
        return matcher.group(1);
    }

    record PlatformProjectVersion(
        String alias,
        String module,
        String versionRef,
        String version
    ) {
        String projectKey() {
            return versionRef.substring("managed-micronaut-".length());
        }

        String artifactId() {
            int colon = module.indexOf(':');
            if (colon < 0 || colon == module.length() - 1) {
                throw new IllegalStateException("Invalid module coordinates: " + module);
            }
            return module.substring(colon + 1);
        }
    }
}
