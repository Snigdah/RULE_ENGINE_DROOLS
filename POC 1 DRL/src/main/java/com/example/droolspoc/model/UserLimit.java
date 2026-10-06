package com.example.droolspoc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Per-user transaction limit. Business data owned by this POC, not the library.
 * PostgreSQL form of the Oracle RE_USER_LIMIT table.
 */
@Data
@Entity
@Table(name = "re_user_limit")
public class UserLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "re_user_limit_seq")
    @SequenceGenerator(name = "re_user_limit_seq", sequenceName = "re_user_limit_seq", allocationSize = 1)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "transaction_mode")
    private String transactionMode;

    @Column(name = "dr_cr_type")
    private String drCrType;

    @Column(name = "limit_amount", nullable = false)
    private BigDecimal limit;
}
