package io.hhplus.tdd.point.reponse;

import io.hhplus.tdd.point.TransactionType;

public record PointHistory(
        long id,
        long userId,
        long amount,
        TransactionType type,
        long updateMillis
) {

    public static PointHistory create(Long id, Long userId, Long amount, TransactionType type) {
        return new PointHistory(
                id, userId, amount, type, System.currentTimeMillis()
        );
    }
}
