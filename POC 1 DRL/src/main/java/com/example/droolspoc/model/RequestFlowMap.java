package com.example.droolspoc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Maps a request DTO class to the decision flow it runs.
 * e.g. com.example.droolspoc.dto.TransactionRequest -> TRANSFER_TRANSACTION.
 */
@Data
@Entity
@Table(name = "request_flow_map")
public class RequestFlowMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_class", nullable = false, unique = true)
    private String requestClass;

    @Column(name = "flow_name", nullable = false)
    private String flowName;
}
