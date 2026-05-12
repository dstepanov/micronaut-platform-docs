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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public abstract class AlignPlatformVersionsTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPlatformVersionCatalog();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

    @TaskAction
    public void align() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        Map<String, String> versions = PlatformVersions.read(getPlatformVersionCatalog().get().getAsFile().toPath());
        List<GuideProject> projects = GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath());

        getLogger().quiet("Aligning {} platform project submodules to platform-managed versions.", projects.size());
        int index = 0;
        for (GuideProject project : projects) {
            String expectedVersion = versions.get(project.platformVersionKey());
            if (expectedVersion == null) {
                throw new IllegalStateException("Platform catalog does not contain " + project.platformVersionKey());
            }

            Path submoduleDirectory = projectDirectory.resolve(project.submodulePath());
            assertCleanSubmodule(project, submoduleDirectory);
            String expectedTag = "v" + expectedVersion;
            getLogger().quiet("[{}/{}] {} -> {}", ++index, projects.size(), project.displayName(), expectedTag);
            GitSupport.run(submoduleDirectory, "fetch", "--tags", "origin");
            GitSupport.run(submoduleDirectory, "switch", "--detach", expectedTag);
        }
        getLogger().quiet("Aligned {} platform project submodules.", projects.size());
    }

    private static void assertCleanSubmodule(GuideProject project, Path submoduleDirectory) throws IOException, InterruptedException {
        String status = GitSupport.run(submoduleDirectory, "status", "--porcelain");
        if (!status.isBlank()) {
            throw new IllegalStateException(project.displayName()
                + " has local changes. Commit, stash, or remove them before aligning versions:\n"
                + status);
        }
    }
}
