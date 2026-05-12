plugins {
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.handlebars)
    implementation(libs.lucide.static)
    implementation(libs.micronaut.gradle.plugins)
    implementation(libs.snakeyaml)
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
