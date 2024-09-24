package io.hhplus.tdd.point.repository;

import io.hhplus.tdd.point.reponse.UserPoint;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPointRepository {

    UserPoint findById(Long id);

    UserPoint upsert(Long id, Long amount);
}
