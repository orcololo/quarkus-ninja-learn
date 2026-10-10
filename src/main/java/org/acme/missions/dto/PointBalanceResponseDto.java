package org.acme.missions.dto;

import java.util.List;

public record PointBalanceResponseDto(
    long balance,
    List<PointTransactionResponseDto> transactions,
    int page,
    int size,
    long totalTransactions) {
}
