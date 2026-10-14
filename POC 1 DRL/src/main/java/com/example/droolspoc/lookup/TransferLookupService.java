package com.example.droolspoc.lookup;

import com.example.droolspoc.model.Product;
import com.example.droolspoc.model.User;
import com.example.droolspoc.model.UserLimit;
import com.example.droolspoc.repository.ProductRepository;
import com.example.droolspoc.repository.UserLimitRepository;
import com.example.droolspoc.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Transfer-flow lookups. Each method is its OWN read-only transaction, so they can safely run
 * on separate (virtual) threads in parallel — a JPA session is per-thread, never shared.
 */
@Service
public class TransferLookupService {

    private final UserLimitRepository userLimitRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public TransferLookupService(UserLimitRepository userLimitRepository,
                                 ProductRepository productRepository,
                                 UserRepository userRepository) {
        this.userLimitRepository = userLimitRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserLimit userLimit(String userId) {
        return userLimitRepository.findFirstByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No user limit for userId=" + userId));
    }

    @Transactional(readOnly = true)
    public Product product(String sourceAccount) {
        return productRepository.findBySourceAccount(sourceAccount)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No product for sourceAccount=" + sourceAccount));
    }

    @Transactional(readOnly = true)
    public User user(String userId) {
        return userRepository.findFirstByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "No user for userId=" + userId));
    }
}
