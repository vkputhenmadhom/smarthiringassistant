package org.vinod.interview.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.vinod.interview.entity.InterviewQuestion;

import java.util.List;

@Repository
public interface InterviewQuestionRepository extends MongoRepository<InterviewQuestion, String> {
    List<InterviewQuestion> findByTopicIgnoreCase(String topic);
    List<InterviewQuestion> findByDifficultyIgnoreCase(String difficulty);
    List<InterviewQuestion> findByCategoryIgnoreCase(String category);
    List<InterviewQuestion> findByTopicIgnoreCaseAndDifficultyIgnoreCase(String topic, String difficulty);
}

