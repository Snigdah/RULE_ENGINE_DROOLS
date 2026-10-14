package com.example.droolspoc.repository;

import com.example.droolspoc.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findFirstByAccountNo(String accountNo);
}
