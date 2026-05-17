package io.micronaut.docs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModernGuideRendererTest {

    @Test
    void falseSnippetIndentAttributesAreNormalizedToZero() {
        String source = """
            snippet::io.micronaut.aws.alexa.ssml.AudioSpec[project="aws-alexa", tags="ssmlsample", source="test", indent="false"]
            snippet::example[source="main", indent='false']
            snippet::example[source="main", indent=false]
            """;

        assertEquals("""
            snippet::io.micronaut.aws.alexa.ssml.AudioSpec[project="aws-alexa", tags="ssmlsample", source="test", indent=0]
            snippet::example[source="main", indent=0]
            snippet::example[source="main", indent=0]
            """, ModernGuideRenderer.normalizeAsciiDocSource(source));
    }

    @Test
    void falseIncludeIndentAttributesAreNormalizedToZero() {
        String source = """
            include::{sourcedir}/jsonfeed-core/src/test/groovy/io/micronaut/rss/jsonfeed/JsonFeedSpec.groovy[tag=builder,indent=false]
            include::{sourcedir}/example/Example.java[tag=builder, indent = "false", lines=1..2]
            """;

        assertEquals("""
            include::{sourcedir}/jsonfeed-core/src/test/groovy/io/micronaut/rss/jsonfeed/JsonFeedSpec.groovy[tag=builder,indent=0]
            include::{sourcedir}/example/Example.java[tag=builder, indent = 0, lines=1..2]
            """, ModernGuideRenderer.normalizeAsciiDocSource(source));
    }
}
