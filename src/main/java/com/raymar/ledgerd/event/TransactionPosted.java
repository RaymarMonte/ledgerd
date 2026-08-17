package com.raymar.ledgerd.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TransactionPosted(UUID transactionId, List<Posting> postings, Instant effectiveTime,
        Instant recordedTime) {

    public TransactionPosted {
        postings = List.copyOf(postings);

        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(effectiveTime, "effectiveTime cannot be null");
        Objects.requireNonNull(recordedTime, "recordedTime cannot be null");
        if (postings.size() < 2) {
            throw new IllegalArgumentException("Postings must have 2 or more entries");
        }
        long postingsSum = 0;
        for (Posting posting : postings) {
            postingsSum = Math.addExact(postingsSum, posting.amount());
        }
        if (postingsSum != 0) {
            throw new IllegalArgumentException("Postings must sum up to zero");
        }
    }

}
