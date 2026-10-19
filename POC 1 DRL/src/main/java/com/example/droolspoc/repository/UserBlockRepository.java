package com.example.droolspoc.repository;

import com.example.droolspoc.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    List<UserBlock> findByBlockedTrue();
}
