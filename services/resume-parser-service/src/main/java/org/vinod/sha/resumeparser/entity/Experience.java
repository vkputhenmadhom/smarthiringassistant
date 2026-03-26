package org.vinod.sha.resumeparser.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resume_experience")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String company;

    @Column
    private String jobTitle;

    @Column
    private String startDate;

    @Column
    private String endDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private Double durationYears;
}

