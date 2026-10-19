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
 * Account/product data. {@code drRes} = debit-restricted flag (1 = restricted).
 * PostgreSQL form of the Oracle RE_PRODUCT table.
 */
@Data
@Entity
@Table(name = "re_product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "re_product_seq")
    @SequenceGenerator(name = "re_product_seq", sequenceName = "re_product_seq", allocationSize = 1)
    private Long id;

    @Column(name = "source_account", nullable = false, unique = true)
    private String sourceAccount;

    @Column(name = "dr_res")
    private Integer drRes;
}
