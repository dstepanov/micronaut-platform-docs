package io.micronaut.docs;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VerifyPlatformDocsTaskTest {

    @Test
    void projectSectionsAreScopedToTheSelectedProject() {
        String sidebar = """
            <details class="project-section" data-project-nav="test">
                <a class="toc-link" href="#test-introduction" data-section="test-introduction">Introduction</a>
            </details>
            <details class="project-section" data-project-nav="test-resources">
                <a class="toc-link" href="#test-resources-introduction" data-section="test-resources-introduction">Introduction</a>
            </details>
            """;

        assertEquals(Set.of("test-introduction"), VerifyPlatformDocsTask.projectSections(sidebar, "test"));
        assertEquals(Set.of("test-resources-introduction"), VerifyPlatformDocsTask.projectSections(sidebar, "test-resources"));
    }
}
