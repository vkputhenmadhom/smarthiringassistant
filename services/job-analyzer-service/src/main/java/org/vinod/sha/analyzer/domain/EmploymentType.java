package org.vinod.sha.analyzer.domain;

/**
 * Java 21 Sealed Class: Represents employment/job type.
 * Restricts implementations to defined constants only, ensuring type safety.
 */
public sealed interface EmploymentType permits EmploymentType.FULL_TIME, EmploymentType.PART_TIME, 
                                                EmploymentType.CONTRACT, EmploymentType.TEMPORARY,
                                                EmploymentType.INTERNSHIP {
    String value();

    record FULL_TIME(String value) implements EmploymentType {
        public FULL_TIME() {
            this("FULL_TIME");
        }
    }

    record PART_TIME(String value) implements EmploymentType {
        public PART_TIME() {
            this("PART_TIME");
        }
    }

    record CONTRACT(String value) implements EmploymentType {
        public CONTRACT() {
            this("CONTRACT");
        }
    }

    record TEMPORARY(String value) implements EmploymentType {
        public TEMPORARY() {
            this("TEMPORARY");
        }
    }

    record INTERNSHIP(String value) implements EmploymentType {
        public INTERNSHIP() {
            this("INTERNSHIP");
        }
    }

    /**
     * Factory method: Parse string to EmploymentType
     */
    static EmploymentType from(String type) {
        return switch (type == null ? "" : type.toUpperCase()) {
            case "PART_TIME" -> new PART_TIME();
            case "CONTRACT" -> new CONTRACT();
            case "TEMPORARY" -> new TEMPORARY();
            case "INTERNSHIP" -> new INTERNSHIP();
            default -> new FULL_TIME();
        };
    }
}
