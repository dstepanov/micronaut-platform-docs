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
            String tags = GitSupport.run(submoduleDirectory, "tag", "--points-at", "HEAD");
            if (!tags.lines().anyMatch(expectedTag::equals)) {
                throw new IllegalStateException(project.displayName()
                    + " must be checked out at tag "
                    + expectedTag
                    + " from "
                    + project.platformVersionKey()
                    + ", but HEAD has tags: "
                    + (tags.isBlank() ? "<none>" : tags.replace('\n', ' ').trim()));
            }
        }
        getLogger().quiet(
            "Verified platform alignment for {} of {} project submodules (shard {}/{}).",
            selectedProjects.size(),
            projects.size(),
            selection.shardIndex() + 1,
            selection.shardCount()
        );
    }
}
