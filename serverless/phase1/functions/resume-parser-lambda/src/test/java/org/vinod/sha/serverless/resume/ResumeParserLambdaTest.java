package org.vinod.sha.serverless.resume;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeParserLambdaTest {

    private final ResumeParserLambda lambda = new ResumeParserLambda();

    @Test
    void shouldParseResumeContent() {
        ResumeParseRequest request = new ResumeParseRequest(
                "cand-1001",
                "resume.txt",
                "text/plain",
                "Email: vinod@example.com Skills: Java, Spring Boot, React Experience: 6 years"
        );

        ResumeParseResponse response = lambda.handleRequest(request, null);

        assertThat(response.parseStatus()).isEqualTo("PARSED");
        assertThat(response.candidateId()).isEqualTo("cand-1001");
        assertThat(response.email()).isEqualTo("vinod@example.com");
        assertThat(response.experienceYears()).isEqualTo(6);
        assertThat(response.skills()).contains("java", "spring boot", "react");
    }

    @Test
    void shouldFailForBlankContent() {
        ResumeParseRequest request = new ResumeParseRequest("cand-1002", "resume.txt", "text/plain", " ");

        ResumeParseResponse response = lambda.handleRequest(request, null);

        assertThat(response.parseStatus()).isEqualTo("FAILED");
        assertThat(response.skills()).isEmpty();
    }
}

