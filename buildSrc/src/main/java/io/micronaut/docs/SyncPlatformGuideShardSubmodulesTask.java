package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public abstract class SyncPlatformGuideShardSubmodulesTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPlatformVersionCatalog();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getShardPlan();

    @Input
    public abstract Property<Integer> getShardIndex();

    @Input
    public abstract Property<Integer> getShardCount();

    @Input
    @Optional
    public abstract Property<String> getProjectSlugs();

    @TaskAction
    public void sync() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        List<GuideProject> projects = GuideProject.selectBySlugs(
            GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath()),
            getProjectSlugs().getOrElse("")
        );
        Map<String, String> versions = PlatformVersions.read(getPlatformVersionCatalog().get().getAsFile().toPath());
        PlatformDocsShardPlan.ShardSelection selection = PlatformDocsShardPlan.select(
            projects,
            getShardPlan().get().getAsFile().toPath(),
            getShardIndex().getOrElse(0),
            getShardCount().getOrElse(1)
        );

        getLogger().quiet(
            "Syncing {} of {} platform guide submodules (shard {}/{}).",
            selection.projects().size(),
            projects.size(),
            selection.shardIndex() + 1,
            selection.shardCount()
        );

        int index = 0;
        for (GuideProject project : selection.projects()) {
            String expectedVersion = versions.get(project.platformVersionKey());
            if (expectedVersion == null) {
                throw new IllegalStateException("Platform catalog does not contain " + project.platformVersionKey());
            }
            String expectedTag = "v" + expectedVersion;
            Path submoduleDirectory = projectDirectory.resolve(project.submodulePath());
            getLogger().quiet(
                "[{}/{}] Initializing {} at {}.",
                ++index,
                selection.projects().size(),
                project.displayName(),
                expectedTag
            );
            if (GitSupport.isGitRepository(submoduleDirectory)) {
                getLogger().quiet(
                    "[{}/{}] {} submodule is already initialized.",
                    index,
                    selection.projects().size(),
                    project.displayName()
                );
            } else {
                getLogger().quiet(
                    "[{}/{}] Cloning {} from {}.",
                    index,
                    selection.projects().size(),
                    project.displayName(),
                    project.repositoryUrl()
                );
                GitSupport.cloneRepository(
                    projectDirectory,
                    project.repositoryUrl(),
                    project.branch(),
                    project.submodulePath()
                );
            }
            assertCleanSubmodule(project, submoduleDirectory);
            GitSupport.run(submoduleDirectory, "fetch", "--tags", "--no-write-fetch-head", "origin");
            GitSupport.run(submoduleDirectory, "switch", "--detach", expectedTag);
        }
    }

    private static void assertCleanSubmodule(GuideProject project, Path submoduleDirectory) throws IOException, InterruptedException {
        String status = GitSupport.run(submoduleDirectory, "status", "--porcelain");
        if (!status.isBlank()) {
            throw new IllegalStateException(project.displayName()
                + " has local changes. Commit, stash, or remove them before syncing guide shard submodules:\n"
                + status);
        }
    }
}
