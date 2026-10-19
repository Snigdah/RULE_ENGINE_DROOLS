package com.example.droolspoc.repository;

import com.example.droolspoc.model.UserLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLimitRepository extends JpaRepository<UserLimit, Long> {

    Optional<UserLimit> findFirstByUserId(String userId);
}
