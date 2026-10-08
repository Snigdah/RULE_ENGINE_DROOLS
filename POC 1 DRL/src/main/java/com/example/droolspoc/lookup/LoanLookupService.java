package com.example.droolspoc.lookup;

import com.example.droolspoc.model.Customer;
import com.example.droolspoc.model.User;
import com.example.droolspoc.repository.CustomerRepository;
import com.example.droolspoc.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Loan-flow lookups — each its own read-only transaction, safe to run in parallel. */
@Service
public class LoanLookupService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public LoanLookupService(CustomerRepository customerRepository, UserRepository userRepository) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Customer customer(String userId) {
        return customerRepository.findFirstByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No customer for userId=" + userId));
    }

    @Transactional(readOnly = true)
    public User user(String userId) {
        return userRepository.findFirstByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No user for userId=" + userId));
    }
}
