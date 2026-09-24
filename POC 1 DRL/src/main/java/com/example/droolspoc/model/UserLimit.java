package com.example.droolspoc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(
        name = "user_limit",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_limit",
                columnNames = {"user_id", "transaction_mode", "dr_cr_type"})
)
public class UserLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;           // user_id
    private String transactionMode;  // transaction_mode : TRANSFER | CREDIT
    private String drCrType;         // dr_cr_type       : DR | CR

    @Column(name = "limit_amount")
    private BigDecimal limit;
}
