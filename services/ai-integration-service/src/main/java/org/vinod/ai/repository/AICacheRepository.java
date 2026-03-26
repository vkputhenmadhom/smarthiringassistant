package org.vinod.ai.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.vinod.ai.entity.AICache;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AICacheRepository extends MongoRepository<AICache, String> {
    Optional<AICache> findByCacheKey(String cacheKey);
    List<AICache> findByExpiresAtBefore(LocalDateTime now);
    void deleteByExpiresAtBefore(LocalDateTime now);
}

