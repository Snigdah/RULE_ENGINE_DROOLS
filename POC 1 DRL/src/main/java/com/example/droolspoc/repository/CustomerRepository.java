package com.example.droolspoc.repository;

import com.example.droolspoc.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findFirstByUserId(String userId);
}
