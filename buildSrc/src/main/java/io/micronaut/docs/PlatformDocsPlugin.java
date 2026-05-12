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

        TaskProvider<ScanPlatformProjectsTask> scanPlatformProjects = project.getTasks().register(
            "scanPlatformProjects",
            ScanPlatformProjectsTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Scans micronaut-platform's version catalog and writes the docs project manifest sorted by repository age.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getPlatformVersionCatalog().convention(platformVersionCatalog);
                task.getProjectManifest().convention(projectManifest);
                task.getOutputs().upToDateWhen(taskProvider -> false);
            }
        );

        TaskProvider<SyncPlatformProjectSubmodulesTask> syncPlatformProjectSubmodules = project.getTasks().register(
            "syncPlatformProjectSubmodules",
            SyncPlatformProjectSubmodulesTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Adds missing git submodules for Micronaut projects discovered from micronaut-platform.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
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
                task.dependsOn(scanPlatformProjects);
                task.mustRunAfter(alignPlatformVersions);
            }
        );

        TaskProvider<BuildGuideDocsTask> buildPlatformGuideDocs = project.getTasks().register(
            "buildPlatformGuideDocs",
            BuildGuideDocsTask.class,
            task -> {
                task.setGroup("documentation");
                task.setDescription("Builds guide docs for each Micronaut project discovered from micronaut-platform.");
                task.getProjectDirectory().convention(project.getLayout().getProjectDirectory());
                task.getProjectManifest().convention(projectManifest);
                task.dependsOn(scanPlatformProjects);
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
