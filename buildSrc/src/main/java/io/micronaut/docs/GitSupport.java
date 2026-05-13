package io.micronaut.docs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class GitSupport {

    private GitSupport() {
    }

    static String run(Path workingDirectory, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);

        Process process = new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int exitCode = process.waitFor();
        String text = output.toString(StandardCharsets.UTF_8);
        if (exitCode != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed in " + workingDirectory + ": " + text);
        }
        return text;
    }

    static boolean isGitRepository(Path directory) {
        return Files.exists(directory.resolve(".git"));
    }

    static void cloneRepository(
        Path workingDirectory,
        String repositoryUrl,
        String branch,
        String destination
    ) throws IOException, InterruptedException {
        Path destinationDirectory = workingDirectory.resolve(destination);
        Files.createDirectories(destinationDirectory.getParent());
        if (branch == null || branch.isBlank()) {
            run(workingDirectory, "clone", repositoryUrl, destination);
        } else {
            run(workingDirectory, "clone", "--branch", branch, "--single-branch", repositoryUrl, destination);
        }
    }
}
