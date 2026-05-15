plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.asciidoctorj)
    implementation(libs.grails.gdoc.engine)
    implementation(libs.handlebars)
    implementation(libs.lucide.static)
    implementation(files("../gradle/build-plugin/micronaut-gradle-plugins-${libs.versions.micronaut.build.get()}.jar"))
    implementation(libs.node.gradle.plugin)
    implementation(libs.snakeyaml)
    implementation(libs.tomlj)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.playwright)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        create("platformDocs") {
            id = "io.micronaut.platform-docs"
            implementationClass = "io.micronaut.docs.PlatformDocsPlugin"
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val playwrightDriverTmp = layout.buildDirectory.dir("playwright-driver-tmp")
    doFirst {
        playwrightDriverTmp.get().asFile.mkdirs()
    }
    systemProperty("playwright.driver.tmpdir", playwrightDriverTmp.get().asFile.absolutePath)
    systemProperty(
        "platformDocs.indexFile",
        providers.systemProperty("platformDocs.indexFile")
            .orElse(layout.projectDirectory.file("../build/site/index.html").asFile.absolutePath)
            .get()
    )
}
