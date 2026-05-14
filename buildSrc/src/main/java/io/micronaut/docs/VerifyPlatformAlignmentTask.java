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

public abstract class VerifyPlatformAlignmentTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPlatformVersionCatalog();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

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
    public void verify() throws IOException, InterruptedException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        Map<String, String> versions = PlatformVersions.read(getPlatformVersionCatalog().get().getAsFile().toPath());
        List<GuideProject> projects = GuideProject.selectBySlugs(
            GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath()),
            getProjectSlugs().getOrElse("")
        );
        PlatformDocsShardPlan.ShardSelection selection = PlatformDocsShardPlan.select(
            projects,
            getShardPlan().get().getAsFile().toPath(),
            getShardIndex().getOrElse(0),
            getShardCount().getOrElse(1)
        );
        List<GuideProject> selectedProjects = selection.projects();

        getLogger().quiet(
            "Verifying platform alignment for {} of {} project submodules (shard {}/{}).",
            selectedProjects.size(),
            projects.size(),
            selection.shardIndex() + 1,
            selection.shardCount()
        );
        int index = 0;
        for (GuideProject project : selectedProjects) {
            String expectedVersion = versions.get(project.platformVersionKey());
            if (expectedVersion == null) {
                throw new IllegalStateException("Platform catalog does not contain " + project.platformVersionKey());
            }

            String expectedTag = "v" + expectedVersion;
            Path submoduleDirectory = projectDirectory.resolve(project.submodulePath());
            getLogger().quiet("[{}/{}] {} expects {}", ++index, selectedProjects.size(), project.displayName(), expectedTag);
            verifyProjectAlignment(project, submoduleDirectory, expectedTag);
        }
        getLogger().quiet(
            "Verified platform alignment for {} of {} project submodules (shard {}/{}).",
            selectedProjects.size(),
            projects.size(),
            selection.shardIndex() + 1,
            selection.shardCount()
        );
    }

    private void verifyProjectAlignment(GuideProject project, Path submoduleDirectory, String expectedTag) throws IOException, InterruptedException {
        GitSupport.fetchTags(submoduleDirectory);
        String headCommit = GitSupport.resolveCommit(submoduleDirectory, "HEAD");
        if (GitSupport.tagExists(submoduleDirectory, expectedTag)) {
            String expectedCommit = GitSupport.resolveCommit(submoduleDirectory, expectedTag);
            if (!expectedCommit.equals(headCommit)) {
                throw new IllegalStateException(project.displayName()
                    + " must be checked out at tag "
                    + expectedTag
                    + " from "
                    + project.platformVersionKey()
                    + ", but HEAD is "
                    + headCommit
                    + " and "
                    + expectedTag
                    + " is "
                    + expectedCommit);
            }
            return;
        }

        GitSupport.fetchRemoteBranch(submoduleDirectory, project.branch());
        String fallbackRef = GitSupport.remoteBranchRef(project.branch());
        if (!GitSupport.commitRefExists(submoduleDirectory, fallbackRef)) {
            throw new IllegalStateException(project.displayName()
                + " is missing platform tag "
                + expectedTag
                + " and fallback branch "
                + fallbackRef);
        }
        String fallbackCommit = GitSupport.resolveCommit(submoduleDirectory, fallbackRef);
        if (!fallbackCommit.equals(headCommit)) {
            throw new IllegalStateException(project.displayName()
                + " must be checked out at tag "
                + expectedTag
                + " or fallback branch "
                + fallbackRef
                + ", but HEAD is "
                + headCommit
                + " and "
                + fallbackRef
                + " is "
                + fallbackCommit);
        }
        getLogger().quiet("{} tag {} is missing; verified fallback {}.", project.displayName(), expectedTag, fallbackRef);
    }
}
