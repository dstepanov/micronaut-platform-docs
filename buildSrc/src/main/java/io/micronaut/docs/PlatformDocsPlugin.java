package io.micronaut.docs;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskProvider;

public final class PlatformDocsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);

        var platformVersionCatalog = project.getLayout().getProjectDirectory().file("repos/micronaut-platform/gradle/libs.versions.toml");
        var projectManifest = project.getLayout().getProjectDirectory().file("gradle/platform-doc-projects.properties");
        var shardPlan = project.getLayout().getProjectDirectory().file(PlatformDocsShardPlan.DEFAULT_RELATIVE_PATH);
        var guideShardIndex = project.getProviders()
            .gradleProperty("platformDocs.guideShardIndex")
            .orElse(project.getProviders().environmentVariable("PLATFORM_DOCS_GUIDE_SHARD_INDEX"))
            .map(Integer::parseInt)
            .orElse(0);
        var guideShardCount = project.getProviders()
            .gradleProperty("platformDocs.guideShardCount")
            .orElse(project.getProviders().environmentVariable("PLATFORM_DOCS_GUIDE_SHARD_COUNT"))
            .map(Integer::parseInt)
            .orElse(1);

        TaskProvider<SyncPlatformSubmoduleTask> syncPlatformSubmodule = project.getTasks().register(
            "syncPlatformSubmodule",
            SyncPlatformSubmoduleTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Initializes the micronaut-platform submodule used as the version source of truth.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
            }
        );

        TaskProvider<ScanPlatformProjectsTask> scanPlatformProjects = project.getTasks().register(
            "scanPlatformProjects",
            ScanPlatformProjectsTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Scans micronaut-platform's version catalog and writes the docs project manifest sorted by repository age.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getPlatformVersionCatalog().convention(platformVersionCatalog);
                task.getProjectManifest().convention(projectManifest);
                task.dependsOn(syncPlatformSubmodule);
                task.getOutputs().upToDateWhen(taskProvider -> false);
            }
        );

        TaskProvider<WritePlatformDocsShardMatrixTask> writePlatformDocsShardMatrix = project.getTasks().register(
            "writePlatformDocsShardMatrix",
            WritePlatformDocsShardMatrixTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Writes the GitHub Actions matrix from the configured platform docs shard plan.");
                task.getProjectManifest().convention(projectManifest);
                task.getShardPlan().convention(shardPlan);
                task.getShardCount().convention(guideShardCount);
                task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("platform-docs-shard-matrix.json"));
                task.dependsOn(scanPlatformProjects);
            }
        );

        TaskProvider<SyncPlatformProjectSubmodulesTask> syncPlatformProjectSubmodules = project.getTasks().register(
            "syncPlatformProjectSubmodules",
            SyncPlatformProjectSubmodulesTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Adds missing git submodules and checks out platform-managed tags for Micronaut projects discovered from micronaut-platform.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getPlatformVersionCatalog().convention(platformVersionCatalog);
                task.getProjectManifest().convention(projectManifest);
                task.dependsOn(scanPlatformProjects);
            }
        );

        TaskProvider<AlignPlatformVersionsTask> alignPlatformVersions = project.getTasks().register(
            "alignPlatformVersions",
            AlignPlatformVersionsTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Checks out documentation submodules at the versions declared by micronaut-platform.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getPlatformVersionCatalog().convention(platformVersionCatalog);
                task.getProjectManifest().convention(projectManifest);
                task.dependsOn(scanPlatformProjects);
                task.mustRunAfter(syncPlatformProjectSubmodules);
            }
        );

        TaskProvider<VerifyPlatformAlignmentTask> verifyPlatformAlignment = project.getTasks().register(
            "verifyPlatformAlignment",
            VerifyPlatformAlignmentTask.class,
            task -> {
                task.setGroup("verification");
                task.setDescription("Checks that documentation submodules match micronaut-platform's version catalog.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getPlatformVersionCatalog().convention(platformVersionCatalog);
                task.getProjectManifest().convention(projectManifest);
                task.getShardPlan().convention(shardPlan);
                task.getShardIndex().convention(guideShardIndex);
                task.getShardCount().convention(guideShardCount);
                task.dependsOn(scanPlatformProjects);
                task.mustRunAfter(alignPlatformVersions);
            }
        );

        TaskProvider<SyncPlatformGuideShardSubmodulesTask> syncPlatformGuideShardSubmodules = project.getTasks().register(
            "syncPlatformGuideShardSubmodules",
            SyncPlatformGuideShardSubmodulesTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Initializes and aligns only the guide submodules selected by the configured shard.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getProjectManifest().convention(projectManifest);
                task.getPlatformVersionCatalog().convention(platformVersionCatalog);
                task.getShardPlan().convention(shardPlan);
                task.getShardIndex().convention(guideShardIndex);
                task.getShardCount().convention(guideShardCount);
                task.dependsOn(scanPlatformProjects);
            }
        );
        verifyPlatformAlignment.configure(task -> task.mustRunAfter(syncPlatformGuideShardSubmodules));

        TaskProvider<BuildGuideDocsTask> buildPlatformGuideDocs = project.getTasks().register(
            "buildPlatformGuideDocs",
            BuildGuideDocsTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Builds guide docs for each Micronaut project discovered from micronaut-platform.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getProjectManifest().convention(projectManifest);
                task.getShardPlan().convention(shardPlan);
                task.getShardIndex().convention(guideShardIndex);
                task.getShardCount().convention(guideShardCount);
                task.dependsOn(scanPlatformProjects);
                task.dependsOn(syncPlatformGuideShardSubmodules);
                task.mustRunAfter(verifyPlatformAlignment);
            }
        );

        TaskProvider<StageGuideDocsArtifactTask> stagePlatformGuideDocsArtifact = project.getTasks().register(
            "stagePlatformGuideDocsArtifact",
            StageGuideDocsArtifactTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Stages generated guide docs for upload as a GitHub Actions shard artifact.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getProjectManifest().convention(projectManifest);
                task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("guide-docs-artifact"));
                task.getShardPlan().convention(shardPlan);
                task.getShardIndex().convention(guideShardIndex);
                task.getShardCount().convention(guideShardCount);
                task.dependsOn(buildPlatformGuideDocs);
                task.mustRunAfter(verifyPlatformAlignment);
            }
        );

        TaskProvider<GeneratePlatformDocsTask> generatePlatformDocs = project.getTasks().register(
            "generatePlatformDocs",
            GeneratePlatformDocsTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Generates the single-page Micronaut documentation site.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getPlatformVersionCatalog().convention(platformVersionCatalog);
                task.getProjectManifest().convention(projectManifest);
                task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("site"));
                task.dependsOn(scanPlatformProjects);
                task.dependsOn(verifyPlatformAlignment);
                task.dependsOn(buildPlatformGuideDocs);
                task.getOutputs().upToDateWhen(taskProvider -> false);
            }
        );

        TaskProvider<GeneratePlatformDocsTask> renderPlatformDocs = project.getTasks().register(
            "renderPlatformDocs",
            GeneratePlatformDocsTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Renders the single-page Micronaut documentation site from existing generated guide docs.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getPlatformVersionCatalog().convention(platformVersionCatalog);
                task.getProjectManifest().convention(projectManifest);
                task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("site"));
                task.dependsOn(scanPlatformProjects);
                task.getOutputs().upToDateWhen(taskProvider -> false);
            }
        );

        TaskProvider<VerifyPlatformDocsTask> verifyPlatformDocs = project.getTasks().register(
            "verifyPlatformDocs",
            VerifyPlatformDocsTask.class,
            task -> {
                task.setGroup("verification");
                task.setDescription("Checks that the generated documentation page contains each configured project.");
                task.getIndexFile().convention(generatePlatformDocs.flatMap(taskProvider -> taskProvider.getOutputDirectory().file("index.html")));
                task.getProjectManifest().convention(projectManifest);
                task.dependsOn(generatePlatformDocs);
            }
        );

        project.getTasks().register(
            "verifyRenderedPlatformDocs",
            VerifyPlatformDocsTask.class,
            task -> {
                task.setGroup("verification");
                task.setDescription("Checks the rendered documentation page without building guide docs.");
                task.getIndexFile().convention(renderPlatformDocs.flatMap(taskProvider -> taskProvider.getOutputDirectory().file("index.html")));
                task.getProjectManifest().convention(projectManifest);
                task.dependsOn(renderPlatformDocs);
            }
        );

        project.getTasks().named("assemble").configure(task -> task.dependsOn(generatePlatformDocs));
        project.getTasks().named("check").configure(task -> task.dependsOn(verifyPlatformAlignment, verifyPlatformDocs));

        project.getTasks().register("cM", task -> {
            task.setGroup("verification");
            task.setDescription("Runs the verification checks available in this docs generator project.");
            task.dependsOn("check");
        });

        project.getTasks().register("spotlessCheck", task -> {
            task.setGroup("verification");
            task.setDescription("Placeholder Spotless check; no Spotless formatting rules are configured yet.");
        });
    }
}
