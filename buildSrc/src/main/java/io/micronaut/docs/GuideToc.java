package io.micronaut.docs;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source table-of-contents model for a Micronaut guide.
 *
 * <p>The platform docs generator keeps this model separate from rendering so the same
 * parsed {@code toc.yml} structure can drive source rendering, sidebar data, page index data,
 * search entries, and verification without depending on the old generated guide templates.</p>
 */
final class GuideToc {
    private static final String DEFAULT_EXTENSION = ".adoc";

    private final String title;
    private final List<Node> children;

    private GuideToc(String title, List<Node> children) {
        this.title = title;
        this.children = List.copyOf(children);
    }

    static GuideToc read(Path tocFile) throws IOException {
        return read(tocFile, null);
    }

    static GuideToc readSource(Path guideSourceDirectory) throws IOException {
        return read(guideSourceDirectory.resolve("toc.yml"), guideSourceDirectory);
    }

    String title() {
        return title;
    }

    List<Node> children() {
        return children;
    }

    List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Node child : children) {
            appendEntries(entries, child);
        }
        return List.copyOf(entries);
    }

    private static void appendEntries(List<Entry> entries, Node node) {
        entries.add(new Entry(node.level(), node.number(), node.id(), node.title(), node.file()));
        for (Node child : node.children()) {
            appendEntries(entries, child);
        }
    }

    private static GuideToc read(Path tocFile, Path guideSourceDirectory) throws IOException {
        if (!Files.isRegularFile(tocFile)) {
            throw new IOException("Missing TOC YAML: " + tocFile);
        }

        Object parsed;
        Yaml parser = new Yaml(new SafeConstructor(new LoaderOptions()));
        try (InputStream input = Files.newInputStream(tocFile)) {
            parsed = parser.load(input);
        }
        if (!(parsed instanceof Map<?, ?> toc)) {
            throw new IOException("TOC YAML must be a map: " + tocFile);
        }

        Map<Object, Object> root = copyMap(toc);
        Object title = root.remove("title");
        List<Node> children = new ArrayList<>();
        appendNodes(children, guideSourceDirectory, List.of(), root, 0, "");
        return new GuideToc(title == null ? "" : title.toString(), children);
    }

    private static void appendNodes(
        List<Node> target,
        Path guideSourceDirectory,
        List<String> parentIds,
        Map<?, ?> toc,
        int level,
        String numberPrefix
    ) throws IOException {
        int index = 1;
        for (Map.Entry<?, ?> entry : toc.entrySet()) {
            String id = tocKey(entry.getKey());
            if ("title".equals(id)) {
                continue;
            }
            String number = numberPrefix.isBlank() ? Integer.toString(index) : numberPrefix + "." + index;
            Object value = entry.getValue();
            String title = tocTitle(id, value);
            List<String> pathIds = new ArrayList<>(parentIds);
            String file = determineFilePath(guideSourceDirectory, pathIds, id);
            pathIds.add(id);
            List<Node> children = new ArrayList<>();
            if (value instanceof Map<?, ?> childMap) {
                Map<Object, Object> childSections = copyMap(childMap);
                childSections.remove("title");
                appendNodes(children, guideSourceDirectory, pathIds, childSections, level + 1, number);
            }
            target.add(new Node(level, number, id, title, file, children));
            index++;
        }
    }

    private static String tocKey(Object key) {
        if (key instanceof String id && !id.isBlank()) {
            return id;
        }
        throw new IllegalArgumentException("TOC section keys must be non-blank strings.");
    }

    private static Map<Object, Object> copyMap(Map<?, ?> map) {
        Map<Object, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private static String tocTitle(String id, Object value) {
        if (value instanceof String title && !title.isBlank()) {
            return title.trim();
        }
        if (value instanceof Map<?, ?> children) {
            Object title = children.get("title");
            if (title instanceof String text && !text.isBlank()) {
                return text.trim();
            }
            throw new IllegalArgumentException("TOC section '" + id + "' must define a non-blank title.");
        }
        throw new IllegalArgumentException("TOC section '" + id + "' must be a string or map.");
    }

    private static String determineFilePath(Path guideSourceDirectory, List<String> parentIds, String id) throws IOException {
        if (guideSourceDirectory == null) {
            return null;
        }

        String filePath = id + DEFAULT_EXTENSION;
        if (Files.isRegularFile(guideSourceDirectory.resolve(filePath))) {
            return filePath;
        }
        if (!parentIds.isEmpty()) {
            for (int depth = 1; depth <= parentIds.size(); depth++) {
                List<String> elements = new ArrayList<>(parentIds.subList(0, depth));
                elements.add(id + DEFAULT_EXTENSION);
                filePath = String.join("/", elements);
                if (Files.isRegularFile(guideSourceDirectory.resolve(filePath))) {
                    return filePath;
                }
            }
        }

        throw new IOException("Missing guide source file for TOC section '" + id + "' under " + guideSourceDirectory);
    }

    record Node(int level, String number, String id, String title, String file, List<Node> children) {
        Node {
            children = List.copyOf(children);
        }
    }

    record Entry(int level, String number, String id, String title, String file) {
    }
}
