package com.example.droolspoc.repository;

import com.example.droolspoc.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByUserId(String userId);
}
