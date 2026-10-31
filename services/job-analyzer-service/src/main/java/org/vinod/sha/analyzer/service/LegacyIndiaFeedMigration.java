package org.vinod.sha.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.vinod.sha.analyzer.entity.JobAnalysis;

import java.util.Map;

/**
 * India imports used to be tagged JSEARCH_IN / ADZUNA_IN and came only from government / PSU searches.
 * They now belong to the government feed. Nothing creates those tags any more, so running this on every
 * start is harmless.
 */
@Slf4j
@Component
class LegacyIndiaFeedMigration implements ApplicationRunner {

    private static final Map<String, String> LEGACY_TO_GOVT = Map.of(
            "JSEARCH_IN", "JSEARCH_GOVT_IN",
            "ADZUNA_IN", "ADZUNA_GOVT_IN");

    private final MongoTemplate mongoTemplate;

    LegacyIndiaFeedMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        LEGACY_TO_GOVT.forEach((legacy, govt) -> {
            long moved = mongoTemplate.updateMulti(
                    Query.query(Criteria.where("source").is(legacy)),
                    Update.update("source", govt),
                    JobAnalysis.class).getModifiedCount();
            if (moved > 0) {
                log.info("[JobSync] Moved {} {} jobs to the government feed ({})", moved, legacy, govt);
            }
        });
    }
}
