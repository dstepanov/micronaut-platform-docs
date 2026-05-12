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

public abstract class SyncPlatformProjectSubmodulesTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

    @TaskAction
    public void sync() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        List<GuideProject> projects = GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath());
        getLogger().quiet("Syncing {} platform project submodules.", projects.size());
        int index = 0;
        int added = 0;
        int existing = 0;
        for (GuideProject project : projects) {
            Path submoduleDirectory = projectDirectory.resolve(project.submodulePath());
            if (Files.exists(submoduleDirectory)) {
                existing++;
                getLogger().quiet("[{}/{}] {} already present at {}", ++index, projects.size(), project.displayName(), project.submodulePath());
                continue;
            }
            getLogger().quiet(
                "[{}/{}] Adding {} from {} ({})",
                ++index,
                projects.size(),
                project.displayName(),
                project.repositoryUrl(),
                project.branch()
            );
            GitSupport.run(
                projectDirectory,
                "submodule",
                "add",
                "-b",
                project.branch(),
                project.repositoryUrl(),
                project.submodulePath()
            );
            added++;
        }
        getLogger().quiet("Submodule sync complete: {} added, {} already present.", added, existing);
    }
}
