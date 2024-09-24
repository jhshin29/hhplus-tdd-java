package io.hhplus.tdd.point.repository;

import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.reponse.PointHistory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointHistoryRepository {

    List<PointHistory> findAllById(Long id);

    PointHistory insertPointHistory(Long id, Long amount, TransactionType type, long updateMillis);

}
