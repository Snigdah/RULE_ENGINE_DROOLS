package com.example.droolspoc.repository;

import com.example.droolspoc.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySourceAccount(String sourceAccount);
}
