package io.micronaut.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitSupportTest {
    private static final String PLATFORM_BRANCH = "3.0.x";
    private static final String PLATFORM_TAG = "v1.0.0";

    @TempDir
    Path temporaryDirectory;

    @Test
    void checkoutPlatformVersionUsesTagWhenAvailable() throws IOException, InterruptedException {
        RepositoryFixture fixture = createRepositoryFixture();
        Path cloneDirectory = cloneRepository(fixture, "tag-checkout");

        GitSupport.PlatformCheckout checkout = GitSupport.checkoutPlatformVersion(cloneDirectory, PLATFORM_TAG, PLATFORM_BRANCH);

        assertFalse(checkout.branchFallback());
        assertEquals(PLATFORM_TAG, checkout.ref());
        assertEquals(fixture.tagCommit(), GitSupport.resolveCommit(cloneDirectory, "HEAD"));
    }

    @Test
    void checkoutPlatformVersionUsesBranchWhenTagIsMissing() throws IOException, InterruptedException {
        RepositoryFixture fixture = createRepositoryFixture();
        Path cloneDirectory = cloneRepository(fixture, "branch-checkout");

        GitSupport.PlatformCheckout checkout = GitSupport.checkoutPlatformVersion(cloneDirectory, "v3.0.0", PLATFORM_BRANCH);

        assertTrue(checkout.branchFallback());
        assertEquals("origin/" + PLATFORM_BRANCH, checkout.ref());
        assertEquals(fixture.branchCommit(), GitSupport.resolveCommit(cloneDirectory, "HEAD"));
    }

    private Path cloneRepository(RepositoryFixture fixture, String destination) throws IOException, InterruptedException {
        GitSupport.cloneRepository(temporaryDirectory, fixture.remoteDirectory().toString(), PLATFORM_BRANCH, destination);
        return temporaryDirectory.resolve(destination);
    }

    private RepositoryFixture createRepositoryFixture() throws IOException, InterruptedException {
        Path remoteDirectory = temporaryDirectory.resolve("remote.git");
        GitSupport.run(temporaryDirectory, "init", "--bare", remoteDirectory.toString());

        Path sourceDirectory = temporaryDirectory.resolve("source");
        Files.createDirectories(sourceDirectory);
        GitSupport.run(sourceDirectory, "init");
        GitSupport.run(sourceDirectory, "config", "user.email", "platform-docs@example.com");
        GitSupport.run(sourceDirectory, "config", "user.name", "Platform Docs");

        Files.writeString(sourceDirectory.resolve("docs.txt"), "tagged docs\n");
        GitSupport.run(sourceDirectory, "add", "docs.txt");
        GitSupport.run(sourceDirectory, "commit", "-m", "Create tagged docs");
        GitSupport.run(sourceDirectory, "branch", "-M", PLATFORM_BRANCH);
        GitSupport.run(sourceDirectory, "tag", PLATFORM_TAG);
        String tagCommit = GitSupport.resolveCommit(sourceDirectory, PLATFORM_TAG);

        Files.writeString(sourceDirectory.resolve("docs.txt"), "branch docs\n");
        GitSupport.run(sourceDirectory, "commit", "-am", "Update branch docs");
        String branchCommit = GitSupport.resolveCommit(sourceDirectory, "HEAD");

        GitSupport.run(sourceDirectory, "remote", "add", "origin", remoteDirectory.toString());
        GitSupport.run(sourceDirectory, "push", "origin", PLATFORM_BRANCH, PLATFORM_TAG);
        return new RepositoryFixture(remoteDirectory, tagCommit, branchCommit);
    }

    private record RepositoryFixture(Path remoteDirectory, String tagCommit, String branchCommit) {
    }
}
