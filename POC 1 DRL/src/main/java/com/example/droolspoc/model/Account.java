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
 * Account record for the closure flow. {@code balance} drives the CLOSURE rule.
 */
@Data
@Entity
@Table(name = "re_account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "re_account_seq")
    @SequenceGenerator(name = "re_account_seq", sequenceName = "re_account_seq", allocationSize = 1)
    private Long id;

    @Column(name = "account_no", nullable = false, unique = true)
    private String accountNo;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "status")
    private String status;
}
