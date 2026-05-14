package io.micronaut.docs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class GitSupport {
    private static final String ORIGIN = "origin";

    private GitSupport() {
    }

    static String run(Path workingDirectory, String... args) throws IOException, InterruptedException {
        GitResult result = execute(workingDirectory, args);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed in " + workingDirectory + ": " + result.output());
        }
        return result.output();
    }

    static PlatformCheckout checkoutPlatformVersion(Path workingDirectory, String expectedTag, String fallbackBranch) throws IOException, InterruptedException {
        fetchTags(workingDirectory);
        if (tagExists(workingDirectory, expectedTag)) {
            run(workingDirectory, "switch", "--detach", expectedTag);
            return PlatformCheckout.tag(expectedTag);
        }
        fetchRemoteBranch(workingDirectory, fallbackBranch);
        String remoteBranchRef = remoteBranchRef(fallbackBranch);
        if (!commitRefExists(workingDirectory, remoteBranchRef)) {
            throw new IllegalStateException("Missing platform tag " + expectedTag
                + " and fallback branch " + remoteBranchRef
                + " in " + workingDirectory);
        }
        run(workingDirectory, "switch", "--detach", remoteBranchRef);
        return PlatformCheckout.branch(expectedTag, remoteBranchRef);
    }

    static void fetchTags(Path workingDirectory) throws IOException, InterruptedException {
        run(workingDirectory, "fetch", "--tags", "--no-write-fetch-head", ORIGIN);
    }

    static void fetchRemoteBranch(Path workingDirectory, String branch) throws IOException, InterruptedException {
        run(workingDirectory, "fetch", "--no-write-fetch-head", ORIGIN, "+refs/heads/" + branch + ":refs/remotes/" + ORIGIN + "/" + branch);
    }

    static boolean tagExists(Path workingDirectory, String tag) throws IOException, InterruptedException {
        return succeeds(workingDirectory, "show-ref", "--verify", "--quiet", "refs/tags/" + tag);
    }

    static boolean commitRefExists(Path workingDirectory, String ref) throws IOException, InterruptedException {
        return succeeds(workingDirectory, "rev-parse", "--verify", "--quiet", ref + "^{commit}");
    }

    static String resolveCommit(Path workingDirectory, String ref) throws IOException, InterruptedException {
        return run(workingDirectory, "rev-parse", "--verify", ref + "^{commit}").trim();
    }

    static String remoteBranchRef(String branch) {
        return ORIGIN + "/" + branch;
    }

    private static boolean succeeds(Path workingDirectory, String... args) throws IOException, InterruptedException {
        return execute(workingDirectory, args).exitCode() == 0;
    }

    private static GitResult execute(Path workingDirectory, String... args) throws IOException, InterruptedException {
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
        return new GitResult(exitCode, text);
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

    record PlatformCheckout(String expectedTag, String ref, boolean branchFallback) {
        static PlatformCheckout tag(String expectedTag) {
            return new PlatformCheckout(expectedTag, expectedTag, false);
        }

        static PlatformCheckout branch(String expectedTag, String ref) {
            return new PlatformCheckout(expectedTag, ref, true);
        }
    }

    private record GitResult(int exitCode, String output) {
    }
}
