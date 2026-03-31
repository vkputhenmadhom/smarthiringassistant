package org.vinod.interview.dto;

import java.util.List;

public class AnswerFeedbackResponse {
    private String questionId;
    private int score;
    private String summary;
    private List<String> strengths;
    private List<String> improvements;

    public AnswerFeedbackResponse() {
    }

    public AnswerFeedbackResponse(String questionId, int score, String summary, List<String> strengths, List<String> improvements) {
        this.questionId = questionId;
        this.score = score;
        this.summary = summary;
        this.strengths = strengths;
        this.improvements = improvements;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getImprovements() {
        return improvements;
    }

    public void setImprovements(List<String> improvements) {
        this.improvements = improvements;
    }
}

