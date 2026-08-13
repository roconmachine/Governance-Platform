package com.acme.payment.dto;

import java.time.Instant;

public class TransferResult {

    private final String transferId;
    private final String status;
    private final Instant completedAt;

    public TransferResult(String transferId, String status, Instant completedAt) {
        this.transferId = transferId;
        this.status = status;
        this.completedAt = completedAt;
    }

    public String getTransferId() { return transferId; }
    public String getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
}
