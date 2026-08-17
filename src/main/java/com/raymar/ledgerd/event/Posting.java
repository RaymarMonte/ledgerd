package com.raymar.ledgerd.event;

import java.util.Objects;

public record Posting(String accountId, long amount) {
    
    public Posting {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId cannot be blank");
        }
    }
}
