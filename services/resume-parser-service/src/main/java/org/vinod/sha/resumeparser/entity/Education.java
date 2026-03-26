package org.vinod.sha.resumeparser.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resume_education")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String institution;

    @Column
    private String degree;

    @Column
    private String fieldOfStudy;

    @Column
    private String graduationDate;

    @Column
    private String gpa;
}

