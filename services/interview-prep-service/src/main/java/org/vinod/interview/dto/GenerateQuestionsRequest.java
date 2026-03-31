package org.vinod.interview.dto;

import java.util.List;

public class GenerateQuestionsRequest {
    private String userId;
    private String role;
    private String category;
    private String difficulty;
    private List<String> skills;
    private Integer count;

    public GenerateQuestionsRequest() {
    }

    public GenerateQuestionsRequest(String userId, String role, String category, String difficulty, List<String> skills, Integer count) {
        this.userId = userId;
        this.role = role;
        this.category = category;
        this.difficulty = difficulty;
        this.skills = skills;
        this.count = count;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}

