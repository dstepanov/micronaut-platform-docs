package io.micronaut.docs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class PlatformDocsShardPlan {
    static final String DEFAULT_RELATIVE_PATH = "gradle/platform-doc-shards.properties";

    private final int shardCount;
    private final Map<Integer, List<String>> preferredSlugsByShard;
    private final List<Integer> othersShards;

    private PlatformDocsShardPlan(int shardCount, Map<Integer, List<String>> preferredSlugsByShard, List<Integer> othersShards) {
        this.shardCount = shardCount;
        this.preferredSlugsByShard = preferredSlugsByShard;
        this.othersShards = othersShards;
    }

    static ShardSelection select(List<GuideProject> projects, Path planFile, int shardIndex, int fallbackShardCount) throws IOException {
        if (fallbackShardCount <= 1) {
            if (shardIndex != 0) {
                throw new IllegalArgumentException("Shard index " + shardIndex + " is outside shard count 1.");
            }
            return new ShardSelection(projects, 0, 1);
        }
        PlatformDocsShardPlan plan = read(planFile, fallbackShardCount);
        List<List<GuideProject>> shards = plan.shards(projects);
        if (shardIndex < 0 || shardIndex >= shards.size()) {
            throw new IllegalArgumentException("Shard index " + shardIndex + " is outside shard count " + shards.size() + ".");
        }
        return new ShardSelection(shards.get(shardIndex), shardIndex, shards.size());
    }

    static List<List<GuideProject>> shards(List<GuideProject> projects, Path planFile, int fallbackShardCount) throws IOException {
        return read(planFile, fallbackShardCount).shards(projects);
    }

    static int shardCount(Path planFile, int fallbackShardCount) throws IOException {
        return read(planFile, fallbackShardCount).shardCount;
    }

    private static PlatformDocsShardPlan read(Path planFile, int fallbackShardCount) throws IOException {
        if (fallbackShardCount < 1) {
            throw new IllegalArgumentException("Shard count must be greater than zero.");
        }
        if (planFile == null || !Files.isRegularFile(planFile)) {
            return defaultPlan(fallbackShardCount);
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(planFile)) {
            properties.load(input);
        }
        int shardCount = integer(properties, "shard.count", fallbackShardCount);
        if (shardCount < 1) {
            throw new IllegalArgumentException("shard.count must be greater than zero in " + planFile);
        }

        Map<Integer, List<String>> preferredSlugsByShard = new LinkedHashMap<>();
        for (int shard = 0; shard < shardCount; shard++) {
            List<String> slugs = csv(properties.getProperty("shard." + shard + ".projects", properties.getProperty("shard." + shard, "")));
            if (!slugs.isEmpty()) {
                preferredSlugsByShard.put(shard, slugs);
            }
        }
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("project.") && key.endsWith(".shard")) {
                String slug = key.substring("project.".length(), key.length() - ".shard".length());
                int shard = integer(properties, key, -1);
                validateShardIndex(shard, shardCount, key);
                preferredSlugsByShard.computeIfAbsent(shard, ignored -> new ArrayList<>()).add(slug);
            }
        }

        List<Integer> othersShards = csv(properties.getProperty("others.shards", ""))
            .stream()
            .map(Integer::parseInt)
            .peek(shard -> validateShardIndex(shard, shardCount, "others.shards"))
            .toList();
        if (othersShards.isEmpty()) {
            othersShards = range(shardCount);
        }

        return new PlatformDocsShardPlan(shardCount, preferredSlugsByShard, othersShards);
    }

    private static PlatformDocsShardPlan defaultPlan(int shardCount) {
        return new PlatformDocsShardPlan(shardCount, Map.of(), range(shardCount));
    }

    private List<List<GuideProject>> shards(List<GuideProject> projects) {
        List<List<GuideProject>> shards = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            shards.add(new ArrayList<>());
        }

        Map<String, GuideProject> projectsBySlug = new HashMap<>();
        for (GuideProject project : projects) {
            projectsBySlug.put(project.slug(), project);
        }

        Set<String> assigned = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<String>> entry : preferredSlugsByShard.entrySet()) {
            int shard = entry.getKey();
            validateShardIndex(shard, shardCount, "shard." + shard);
            for (String slug : entry.getValue()) {
                GuideProject project = projectsBySlug.get(slug);
                if (project == null) {
                    throw new IllegalArgumentException("Shard plan references unknown project slug: " + slug);
                }
                if (!assigned.add(slug)) {
                    throw new IllegalArgumentException("Shard plan assigns project more than once: " + slug);
                }
                shards.get(shard).add(project);
            }
        }

        int fallbackIndex = 0;
        for (GuideProject project : projects) {
            if (!assigned.contains(project.slug())) {
                int shard = othersShards.get(fallbackIndex % othersShards.size());
                shards.get(shard).add(project);
                fallbackIndex++;
            }
        }
        return shards;
    }

    private static int integer(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static List<Integer> range(int count) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(i);
        }
        return values;
    }

    private static void validateShardIndex(int shard, int shardCount, String propertyName) {
        if (shard < 0 || shard >= shardCount) {
            throw new IllegalArgumentException(propertyName + " references shard " + shard + ", but shard.count is " + shardCount + ".");
        }
    }

    record ShardSelection(List<GuideProject> projects, int shardIndex, int shardCount) {
    }
}
