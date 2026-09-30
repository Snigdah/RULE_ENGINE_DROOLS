package com.example.droolspoc.repository;

import com.example.droolspoc.model.FlowGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowGroupRepository extends JpaRepository<FlowGroup, Long> {

    void deleteByFlowName(String flowName);
}
