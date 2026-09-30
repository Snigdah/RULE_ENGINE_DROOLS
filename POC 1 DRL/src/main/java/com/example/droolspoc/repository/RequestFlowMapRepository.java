package com.example.droolspoc.repository;

import com.example.droolspoc.model.RequestFlowMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequestFlowMapRepository extends JpaRepository<RequestFlowMap, Long> {

    Optional<RequestFlowMap> findByRequestClass(String requestClass);
}
