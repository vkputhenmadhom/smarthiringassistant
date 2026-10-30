package org.vinod.sha.analyzer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vinod.sha.analyzer.entity.JobAnalysis;

import java.util.Locale;

/**
 * The job market candidates see and are matched against ({@code job-market.region}, env JOB_MARKET), e.g.
 * {@code IN} for India. A job is in the market when it came from that region's import (source tagged
 * {@code *_IN}, like JSEARCH_IN or ADZUNA_IN) or was posted in the app (no source). Blank means every job.
 * Jobs outside the market are kept, and stay visible to HR; changing the setting brings them back.
 */
@Component
public class JobMarket {

    private final String region;

    public JobMarket(@Value("${job-market.region:}") String region) {
        this.region = region == null ? "" : region.trim().toUpperCase(Locale.ROOT);
    }

    public boolean includes(JobAnalysis job) {
        if (region.isEmpty()) {
            return true;
        }
        String source = job.getSource();
        return source == null || source.isBlank() || source.trim().toUpperCase(Locale.ROOT).endsWith("_" + region);
    }

    public String region() {
        return region;
    }
}
