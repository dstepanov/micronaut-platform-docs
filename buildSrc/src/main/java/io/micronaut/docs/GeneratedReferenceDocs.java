package io.micronaut.docs;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class GeneratedReferenceDocs {
    private static final Path API_DIRECTORY = Path.of("api");
    private static final Path CONFIGURATION_REFERENCE = Path.of("guide", "configurationreference.html");

    private GeneratedReferenceDocs() {
    }

    static boolean copyReferenceDocs(Path sourceDocsDirectory, Path targetDocsDirectory, FileTransformer transformer) throws IOException {
        if (!Files.isDirectory(sourceDocsDirectory)) {
            return false;
        }
        boolean copied = false;
        Path apiDirectory = sourceDocsDirectory.resolve(API_DIRECTORY);
        if (Files.isDirectory(apiDirectory)) {
            copyDirectory(apiDirectory, targetDocsDirectory.resolve(API_DIRECTORY), API_DIRECTORY, transformer);
            copied = true;
        }
        Path configurationReference = sourceDocsDirectory.resolve(CONFIGURATION_REFERENCE);
        if (Files.isRegularFile(configurationReference)) {
            copyFile(configurationReference, targetDocsDirectory.resolve(CONFIGURATION_REFERENCE), CONFIGURATION_REFERENCE, transformer);
            copied = true;
        }
        return copied;
    }

    private static void copyDirectory(
        Path sourceDirectory,
        Path targetDirectory,
        Path relativeDirectory,
        FileTransformer transformer
    ) throws IOException {
        try (var stream = Files.walk(sourceDirectory)) {
            for (Path source : stream.toList()) {
                Path relativePath = relativeDirectory.resolve(sourceDirectory.relativize(source));
                Path target = targetDirectory.resolve(sourceDirectory.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    copyFile(source, target, relativePath, transformer);
                }
            }
        }
    }

    private static void copyFile(Path source, Path target, Path relativePath, FileTransformer transformer) throws IOException {
        Files.createDirectories(target.getParent());
        String relative = relativePath.toString().replace('\\', '/');
        if (transformer.shouldTransform(relative)) {
            Files.writeString(target, transformer.transform(relative, Files.readString(source, transformer.charset())), transformer.charset());
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    interface FileTransformer {
        FileTransformer RAW = new FileTransformer() {
            @Override
            public boolean shouldTransform(String relativePath) {
                return false;
            }

            @Override
            public String transform(String relativePath, String content) {
                return content;
            }
        };

        default Charset charset() {
            return StandardCharsets.UTF_8;
        }

        boolean shouldTransform(String relativePath);

        String transform(String relativePath, String content) throws IOException;
    }
}
