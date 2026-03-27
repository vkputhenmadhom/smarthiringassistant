package org.vinod.sha.resumeparser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vinod.sha.resumeparser.entity.ParseStatus;
import org.vinod.sha.resumeparser.entity.Resume;
import org.vinod.sha.resumeparser.outbox.OutboxPublisher;
import org.vinod.sha.resumeparser.repository.ResumeRepository;
import org.vinod.shared.events.ResumeParseEvent;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeParserServiceIntegrationTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private DocumentProcessor documentProcessor;

    @Mock
    private OutboxPublisher outboxPublisher;

    private ResumeParserService resumeParserService;

    @BeforeEach
    void setUp() {
        resumeParserService = new ResumeParserService(resumeRepository, documentProcessor, outboxPublisher);

        AtomicLong ids = new AtomicLong(100L);
        when(resumeRepository.save(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            if (resume.getId() == null) {
                resume.setId(ids.incrementAndGet());
            }
            return resume;
        });
    }

    @Test
    void parseResume_success_publishesSuccessEventAndPersistsResult() throws Exception {
        byte[] file = "Jane Doe\njava spring docker\n5 years experience".getBytes();
        when(documentProcessor.extractText(any(), anyString())).thenReturn("Jane Doe\njane@example.com\njava spring docker\n5 years experience");

        Resume saved = resumeParserService.parseResume(42L, "resume.txt", file);

        assertNotNull(saved.getId());
        assertEquals(ParseStatus.SUCCESS, saved.getStatus());
        assertNotNull(saved.getParsedAt());
        assertNotNull(saved.getParsedData());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).enqueue(anyString(), anyString(), anyString(), payloadCaptor.capture());

        ResumeParseEvent event = (ResumeParseEvent) payloadCaptor.getValue();
        assertEquals(saved.getResumeId(), event.getResumeId());
        assertEquals(42L, event.getUserId());
        assertEquals("SUCCESS", event.getStatus());
    }

    @Test
    void parseResume_failure_marksResumeFailedAndPublishesFailureEvent() throws Exception {
        byte[] file = "invalid doc".getBytes();
        doThrow(new IllegalStateException("Text extraction failed"))
                .when(documentProcessor).extractText(any(), anyString());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resumeParserService.parseResume(7L, "resume.txt", file));

        assertEquals("Text extraction failed", ex.getMessage());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).enqueue(anyString(), anyString(), anyString(), payloadCaptor.capture());

        ResumeParseEvent event = (ResumeParseEvent) payloadCaptor.getValue();
        assertEquals(7L, event.getUserId());
        assertEquals("FAILED", event.getStatus());
    }
}


