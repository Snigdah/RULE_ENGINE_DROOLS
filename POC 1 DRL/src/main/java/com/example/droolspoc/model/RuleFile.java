package com.example.droolspoc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * A DRL file stored in the database. The engine is built from all rows where
 * active = true, so uploading/updating a row (then reloading) changes the rules
 * at runtime without an application restart.
 */
@Data
@Entity
@Table(name = "rule_file")
public class RuleFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", unique = true, nullable = false)
    private String fileName;

    @Column(name = "drl_text", nullable = false, columnDefinition = "text")
    private String drlText;

    private boolean active = true;

    private Integer version = 1;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
