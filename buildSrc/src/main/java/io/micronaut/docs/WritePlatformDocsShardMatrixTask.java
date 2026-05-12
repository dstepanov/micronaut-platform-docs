package io.micronaut.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class WritePlatformDocsShardMatrixTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getProjectManifest();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getShardPlan();

    @Input
    public abstract Property<Integer> getShardCount();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void write() throws IOException {
        List<GuideProject> projects = GuideProject.readManifest(getProjectManifest().get().getAsFile().toPath());
        Path shardPlan = getShardPlan().get().getAsFile().toPath();
        List<List<GuideProject>> shards = PlatformDocsShardPlan.shards(projects, shardPlan, getShardCount().getOrElse(1));
        getLogger().quiet("Writing GitHub Actions matrix for {} platform docs shards.", shards.size());

        StringBuilder json = new StringBuilder("{\"include\":[");
        for (int i = 0; i < shards.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"shard\":")
                .append(i)
                .append(",\"shard_count\":")
                .append(shards.size())
                .append('}');
            getLogger().quiet("[{}/{}] Shard {}: {}", i + 1, shards.size(), i, shards.get(i).stream().map(GuideProject::slug).toList());
        }
        json.append("]}\n");

        Path output = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.toString(), StandardCharsets.UTF_8);
        getLogger().quiet("Wrote platform docs shard matrix to {}.", output);
    }
}
