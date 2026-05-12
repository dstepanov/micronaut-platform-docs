package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public abstract class SyncPlatformProjectSubmodulesTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPlatformVersionCatalog();

    @TaskAction
    public void sync() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        List<GuideProject> projects = GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath());
        Map<String, String> versions = PlatformVersions.read(getPlatformVersionCatalog().get().getAsFile().toPath());
        getLogger().quiet("Syncing {} platform project submodules and checking out platform-managed tags.", projects.size());
        int index = 0;
        int added = 0;
        int existing = 0;
        for (GuideProject project : projects) {
            String expectedVersion = versions.get(project.platformVersionKey());
            if (expectedVersion == null) {
                throw new IllegalStateException("Platform catalog does not contain " + project.platformVersionKey());
            }
            String expectedTag = "v" + expectedVersion;
            Path submoduleDirectory = projectDirectory.resolve(project.submodulePath());
            if (Files.exists(submoduleDirectory)) {
                existing++;
                getLogger().quiet(
                    "[{}/{}] {} already present at {}; checking out {}.",
                    ++index,
                    projects.size(),
                    project.displayName(),
                    project.submodulePath(),
                    expectedTag
                );
            } else {
                getLogger().quiet(
                    "[{}/{}] Adding {} from {} and checking out {}.",
                    ++index,
                    projects.size(),
                    project.displayName(),
                    project.repositoryUrl(),
                    expectedTag
                );
                GitSupport.run(
                    projectDirectory,
                    "submodule",
                    "add",
                    project.repositoryUrl(),
                    project.submodulePath()
                );
                added++;
            }
            checkoutPlatformTag(projectDirectory, project, submoduleDirectory, expectedTag);
        }
        getLogger().quiet("Submodule sync complete: {} added, {} already present, {} checked out.", added, existing, projects.size());
    }

    private static void checkoutPlatformTag(Path projectDirectory, GuideProject project, Path submoduleDirectory, String expectedTag) throws IOException, InterruptedException {
        assertCleanSubmodule(project, submoduleDirectory);
        GitSupport.run(submoduleDirectory, "fetch", "--tags", "origin");
        GitSupport.run(submoduleDirectory, "switch", "--detach", expectedTag);
        GitSupport.run(projectDirectory, "add", ".gitmodules", project.submodulePath());
    }

    private static void assertCleanSubmodule(GuideProject project, Path submoduleDirectory) throws IOException, InterruptedException {
        String status = GitSupport.run(submoduleDirectory, "status", "--porcelain");
        if (!status.isBlank()) {
            throw new IllegalStateException(project.displayName()
                + " has local changes. Commit, stash, or remove them before syncing platform project submodules:\n"
                + status);
        }
    }
}
