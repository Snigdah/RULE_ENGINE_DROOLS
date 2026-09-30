package com.example.droolspoc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * One agenda group that a flow runs, with an order (lower runs first).
 * A flow has many of these rows.
 */
@Data
@Entity
@Table(name = "flow_group")
public class FlowGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_name", nullable = false)
    private String flowName;

    @Column(name = "agenda_group", nullable = false)
    private String agendaGroup;

    @Column(name = "order_no", nullable = false)
    private Integer orderNo;
}
