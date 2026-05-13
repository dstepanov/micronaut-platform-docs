package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class SyncPlatformSubmoduleTask extends DefaultTask {
    private static final String PLATFORM_SUBMODULE_PATH = "repos/micronaut-platform";
    private static final String PLATFORM_REPOSITORY_URL =
        "https://github.com/micronaut-projects/micronaut-platform.git";
    private static final String PLATFORM_BRANCH = "5.0.x";

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @TaskAction
    public void sync() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        Path platformVersionCatalog = projectDirectory
            .resolve(PLATFORM_SUBMODULE_PATH)
            .resolve("gradle/libs.versions.toml");
        if (Files.isRegularFile(platformVersionCatalog)) {
            getLogger().quiet("Micronaut Platform submodule is already initialized at {}.", PLATFORM_SUBMODULE_PATH);
            return;
        }
        getLogger().quiet(
            "Cloning Micronaut Platform from {} at {} into {}.",
            PLATFORM_REPOSITORY_URL,
            PLATFORM_BRANCH,
            PLATFORM_SUBMODULE_PATH
        );
        GitSupport.cloneRepository(projectDirectory, PLATFORM_REPOSITORY_URL, PLATFORM_BRANCH, PLATFORM_SUBMODULE_PATH);
    }
}
