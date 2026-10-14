package com.example.droolspoc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Blocked-user flag. Loaded at startup into the library GlobalContext.
 * PostgreSQL form of the Oracle RE_USER_BLOCK table.
 */
@Data
@Entity
@Table(name = "re_user_block")
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "re_user_block_seq")
    @SequenceGenerator(name = "re_user_block_seq", sequenceName = "re_user_block_seq", allocationSize = 1)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;
}
