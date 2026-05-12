package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public abstract class BuildGuideDocsTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

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
    public void buildDocs() throws IOException, InterruptedException {
        String javaVersion = System.getProperty("java.specification.version");
        if (!"25".equals(javaVersion)) {
            throw new GradleException("Micronaut 5 documentation must be built with Java 25. Current Java version is " + javaVersion + ".");
        }

        Path projectDirectory = getProjectDirectory().get().getAsFile().toPath();
        String javaHome = System.getProperty("java.home");
        List<GuideProject> projects = GuideProject.selectBySlugs(
            GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath()),
            getProjectSlugs().getOrElse("")
        );
        int shardIndex = getShardIndex().getOrElse(0);
        int shardCount = getShardCount().getOrElse(1);
        PlatformDocsShardPlan.ShardSelection selection = PlatformDocsShardPlan.select(
            projects,
            getShardPlan().get().getAsFile().toPath(),
            shardIndex,
            shardCount
        );
        List<GuideProject> selectedProjects = selection.projects();
        getLogger().quiet(
            "Building guide docs for {} of {} projects with Java {} from {} (shard {}/{}).",
            selectedProjects.size(),
            projects.size(),
            javaVersion,
            javaHome,
            selection.shardIndex() + 1,
            selection.shardCount()
        );
        int index = 0;
        for (GuideProject project : selectedProjects) {
            Path submoduleDirectory = projectDirectory.resolve(project.submodulePath());
            if (!Files.isDirectory(submoduleDirectory)) {
                throw new IOException("Missing submodule for " + project.displayName() + ": " + submoduleDirectory
                    + ". Run ./gradlew -q syncPlatformProjectSubmodules.");
            }
            if (!Files.isRegularFile(submoduleDirectory.resolve("gradlew"))) {
                throw new IOException("Missing Gradle wrapper for " + project.displayName() + ": " + submoduleDirectory.resolve("gradlew"));
            }
            int current = ++index;
            long startedAt = System.nanoTime();
            getLogger().quiet("[{}/{}] Building {} docs.", current, selectedProjects.size(), project.displayName());
            ProcessBuilder processBuilder = new ProcessBuilder("./gradlew", "-q", "-Dorg.gradle.vfs.watch=false", "docs")
                .directory(submoduleDirectory.toFile())
                .inheritIO();
            processBuilder.environment().put("JAVA_HOME", javaHome);
            processBuilder.environment().put("PATH", javaHome + File.separator + "bin" + File.pathSeparator + processBuilder.environment().get("PATH"));
            int exitCode = processBuilder.start().waitFor();
            if (exitCode != 0) {
                throw new GradleException("Failed to build " + project.displayName() + " docs. Exit code: " + exitCode);
            }
            getLogger().quiet("[{}/{}] Built {} docs in {}.", current, selectedProjects.size(), project.displayName(), durationSince(startedAt));
        }
        getLogger().quiet(
            "Built guide docs for {} projects in shard {}/{}.",
            selectedProjects.size(),
            selection.shardIndex() + 1,
            selection.shardCount()
        );
    }

    private static String durationSince(long startedAt) {
        long millis = (System.nanoTime() - startedAt) / 1_000_000;
        if (millis < 1_000) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1_000.0);
    }
}
