package org.vinod.interview.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "interview_questions")
public class InterviewQuestion {

    @Id
    private String id;

    private String question;
    private String category; // TECHNICAL, BEHAVIORAL, SYSTEM_DESIGN
    private String difficulty; // EASY, MEDIUM, HARD
    private String topic; // Java, Spring, React, etc.
    private String expectedAnswer;
    private List<String> keyPoints;
    private List<String> tags;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public InterviewQuestion() {
    }

    public InterviewQuestion(String id, String question, String category, String difficulty, String topic,
                             String expectedAnswer, List<String> keyPoints, List<String> tags,
                             String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.question = question;
        this.category = category;
        this.difficulty = difficulty;
        this.topic = topic;
        this.expectedAnswer = expectedAnswer;
        this.keyPoints = keyPoints;
        this.tags = tags;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public List<String> getKeyPoints() {
        return keyPoints;
    }

    public void setKeyPoints(List<String> keyPoints) {
        this.keyPoints = keyPoints;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}

