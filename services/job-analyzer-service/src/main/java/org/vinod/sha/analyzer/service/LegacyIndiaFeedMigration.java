package org.vinod.sha.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.vinod.sha.analyzer.config.JobSyncProperties;
import org.vinod.sha.analyzer.entity.JobAnalysis;

import java.util.List;

/**
 * Imports from before feeds existed are tagged only PROVIDER_MARKET (e.g. JSEARCH_IN). They move to the
 * feed named by job-sync.legacy-feed (job-feeds.yml). Feed imports never use that tag, so running this on
 * every start is harmless.
 */
@Slf4j
@Component
class LegacyIndiaFeedMigration implements ApplicationRunner {

    private static final List<String> PROVIDERS = List.of("jsearch", "adzuna");

    private final MongoTemplate mongoTemplate;
    private final JobSyncProperties props;
    private final JobFeeds jobFeeds;

    LegacyIndiaFeedMigration(MongoTemplate mongoTemplate, JobSyncProperties props, JobFeeds jobFeeds) {
        this.mongoTemplate = mongoTemplate;
        this.props = props;
        this.jobFeeds = jobFeeds;
    }

    @Override
    public void run(ApplicationArguments args) {
        String legacyFeed = props.getLegacyFeed();
        if (legacyFeed == null || legacyFeed.isBlank()) {
            return;
        }
        var feed = jobFeeds.find(legacyFeed).orElse(null);
        if (feed == null || feed.isPostedInApp()) {
            log.warn("[JobSync] job-sync.legacy-feed '{}' is not an import feed in job-feeds.yml; old imports left as they are", legacyFeed);
            return;
        }
        for (String provider : PROVIDERS) {
            String legacyTag = JobFeeds.externalIdPrefix(provider, feed);
            String feedTag = JobFeeds.sourceTag(provider, feed);
            long moved = mongoTemplate.updateMulti(
                    Query.query(Criteria.where("source").is(legacyTag)),
                    Update.update("source", feedTag),
                    JobAnalysis.class).getModifiedCount();
            if (moved > 0) {
                log.info("[JobSync] Moved {} {} jobs to the '{}' feed ({})", moved, legacyTag, feed.getKey(), feedTag);
            }
        }
    }
}
