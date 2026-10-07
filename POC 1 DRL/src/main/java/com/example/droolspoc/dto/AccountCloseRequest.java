package com.example.droolspoc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Account-closure payload. {@code accountNo} looks up the Account (balance);
 * {@code userId} looks up the User (KYC) and is the blocked-user check key.
 */
@Getter
@Setter
public class AccountCloseRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "accountNo is required")
    private String accountNo;

    private String reason;
}
