package com.example.droolspoc.lookup;

import com.example.droolspoc.model.Account;
import com.example.droolspoc.model.User;
import com.example.droolspoc.repository.AccountRepository;
import com.example.droolspoc.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Closure-flow lookups — each its own read-only transaction, safe to run in parallel. */
@Service
public class ClosureLookupService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public ClosureLookupService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Account account(String accountNo) {
        return accountRepository.findFirstByAccountNo(accountNo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No account for accountNo=" + accountNo));
    }

    @Transactional(readOnly = true)
    public User user(String userId) {
        return userRepository.findFirstByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No user for userId=" + userId));
    }
}
