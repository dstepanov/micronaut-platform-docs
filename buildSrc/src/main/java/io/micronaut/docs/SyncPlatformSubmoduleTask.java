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

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @TaskAction
    public void sync() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        if (Files.isRegularFile(projectDirectory.resolve(PLATFORM_SUBMODULE_PATH).resolve("gradle/libs.versions.toml"))) {
            getLogger().quiet("Micronaut Platform submodule is already initialized at {}.", PLATFORM_SUBMODULE_PATH);
            return;
        }
        getLogger().quiet("Initializing Micronaut Platform submodule at {}.", PLATFORM_SUBMODULE_PATH);
        GitSupport.run(projectDirectory, "submodule", "update", "--init", PLATFORM_SUBMODULE_PATH);
    }
}
