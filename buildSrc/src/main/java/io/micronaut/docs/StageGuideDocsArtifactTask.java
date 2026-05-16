package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public abstract class StageGuideDocsArtifactTask extends DefaultTask {

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

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<Integer> getShardIndex();

    @Input
    public abstract Property<Integer> getShardCount();

    @Input
    @Optional
    public abstract Property<String> getProjectSlugs();

    @TaskAction
    public void stage() throws IOException {
        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
        int shardIndex = getShardIndex().getOrElse(0);
        int shardCount = getShardCount().getOrElse(1);
        List<GuideProject> projects = GuideProject.selectBySlugs(
            GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath()),
            getProjectSlugs().getOrElse("")
        );
        PlatformDocsShardPlan.ShardSelection selection = PlatformDocsShardPlan.select(
            projects,
            getShardPlan().get().getAsFile().toPath(),
            shardIndex,
            shardCount
        );
        List<GuideProject> selectedProjects = selection.projects();
        Map<String, String> platformVersions = PlatformVersions.read(getPlatformVersionCatalog().get().getAsFile().toPath());

        deleteDirectory(outputDirectory);
        Files.createDirectories(outputDirectory);
        getLogger().quiet(
            "Staging guide docs artifact for {} of {} projects (shard {}/{}).",
            selectedProjects.size(),
            projects.size(),
            selection.shardIndex() + 1,
            selection.shardCount()
        );

        int index = 0;
        for (GuideProject project : selectedProjects) {
            Path sourceDirectory = projectDirectory.resolve(project.generatedDocsPath());
            getLogger().quiet("[{}/{}] Staging {} platform guide fragment.", ++index, selectedProjects.size(), project.displayName());
            if (Files.isDirectory(sourceDirectory)) {
                Path targetDirectory = outputDirectory.resolve(project.generatedDocsPath());
                copyDirectory(sourceDirectory, targetDirectory);
                getLogger().quiet("[{}/{}] Staged {} API and configuration reference output.", index, selectedProjects.size(), project.displayName());
            } else {
                getLogger().quiet("[{}/{}] No generated reference output found for {}; skipping reference assets.", index, selectedProjects.size(), project.displayName());
            }
            copyToc(projectDirectory, outputDirectory, project);
            renderPlatformGuide(projectDirectory, outputDirectory, project, platformVersions);
        }
    }

    private void renderPlatformGuide(
        Path projectDirectory,
        Path outputDirectory,
        GuideProject project,
        Map<String, String> platformVersions
    ) throws IOException {
        String platformVersion = platformVersions.get(project.platformVersionKey());
        Path target = outputDirectory.resolve(project.platformGuideHtmlPath());
        Files.createDirectories(target.getParent());
        getLogger().quiet("Rendering {} platform guide fragment for the final site.", project.displayName());
        Files.writeString(target, new ModernGuideRenderer(projectDirectory, project, platformVersion).render());
    }

    private static void copyToc(Path projectDirectory, Path outputDirectory, GuideProject project) throws IOException {
        Path source = projectDirectory.resolve(project.tocPath());
        if (!Files.isRegularFile(source)) {
            throw new IOException("Missing TOC YAML for " + project.displayName() + ": " + source);
        }
        Path target = outputDirectory.resolve(project.tocPath());
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void copyDirectory(Path sourceDirectory, Path targetDirectory) throws IOException {
        try (var stream = Files.walk(sourceDirectory)) {
            for (Path source : stream.toList()) {
                Path target = targetDirectory.resolve(sourceDirectory.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
