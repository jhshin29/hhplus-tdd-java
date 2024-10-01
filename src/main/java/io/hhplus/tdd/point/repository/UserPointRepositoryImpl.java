package io.hhplus.tdd.point.repository;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.reponse.UserPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserPointRepositoryImpl implements UserPointRepository {

    private final UserPointTable userPointTable;

    @Override
    public UserPoint findById(Long id) {
        return userPointTable.selectById(id);
    }

    @Override
    public synchronized UserPoint upsert(Long id, Long amount) {
        return userPointTable.insertOrUpdate(id, amount);
    }
}
