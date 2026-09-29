package com.example.droolspoc.repository;

import com.example.droolspoc.model.RuleFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleFileRepository extends JpaRepository<RuleFile, Long> {

    List<RuleFile> findByActiveTrue();

    Optional<RuleFile> findByFileName(String fileName);
}
