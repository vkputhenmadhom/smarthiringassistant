package org.vinod.sha.resumeparser.entity;

import jakarta.persistence.*;
import lombok.*;
import org.vinod.sha.resumeparser.security.EncryptedStringConverter;

import java.util.List;

@Entity
@Table(name = "parsed_resume_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedResumeData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    @Convert(converter = EncryptedStringConverter.class)
    private String fullName;

    @Column
    @Convert(converter = EncryptedStringConverter.class)
    private String email;

    @Column
    @Convert(converter = EncryptedStringConverter.class)
    private String phone;

    @Column
    @Convert(converter = EncryptedStringConverter.class)
    private String location;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String summary;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_skills", joinColumns = @JoinColumn(name = "parsed_data_id"))
    @Column(name = "skill")
    private List<String> skills;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_certifications", joinColumns = @JoinColumn(name = "parsed_data_id"))
    @Column(name = "certification")
    private List<String> certifications;

    @Column
    private Double totalExperienceYears;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "parsed_data_id")
    private List<Experience> experiences;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "parsed_data_id")
    private List<Education> educations;
}

