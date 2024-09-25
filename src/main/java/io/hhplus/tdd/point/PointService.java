package io.hhplus.tdd.point;

import io.hhplus.tdd.point.reponse.PointHistory;
import io.hhplus.tdd.point.reponse.UserPoint;
import io.hhplus.tdd.point.repository.PointHistoryRepository;
import io.hhplus.tdd.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointRepository userPointRepository;
    private final PointHistoryRepository pointHistoryRepository;

    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

    public UserPoint getPointByUser(long id) {
        return userPointRepository.findById(id);
    }

    public UserPoint chargePoint(long userId, long amount) {
        // 최대 보유 가능 포인트값 지정
        long maxPoint = 1000L;

        Lock lock = userLocks.computeIfAbsent(userId, id -> new ReentrantLock());
        lock.lock();

        try {
            // 유저 포인트 조회
            UserPoint userPoint = userPointRepository.findById(userId);

            if (amount < 0) {
                throw new IllegalArgumentException("충전 시 입력 포인트는 마이너스일 수 없습니다.");
            }

            // 포인트 더하고 저장
            UserPoint updateUserPoint = userPoint.chargeOrUsePoint(amount);

            if (maxPoint < updateUserPoint.point()) {
                throw new IllegalArgumentException("최대 보유 가능 포인트를 넘어섰습니다.");
            }

            userPointRepository.upsert(userId, updateUserPoint.point());

            // 히스토리 누적 시도, 실패하면 예외 발생
            pointHistoryRepository.insertPointHistory(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

            return updateUserPoint;
        } catch (Exception e) {
            throw new RuntimeException("포인트 히스토리 적재 실패");
        } finally {
            lock.unlock();
        }
    }

    public UserPoint usePoint(long id, long amount) {
        UserPoint userPoint = userPointRepository.findById(id);

        if (amount > 0) {
            throw new IllegalArgumentException("사용 시 입력 포인트는 플러스일 수 없습니다.");
        }

        // 포인트 차감하고 저장
        UserPoint updateUserPoint = userPoint.chargeOrUsePoint(amount);

        if (updateUserPoint.point() < 0) {
            throw new IllegalArgumentException("포인트 총 금액은 음수일 수 없습니다.");
        }
        userPointRepository.upsert(id, updateUserPoint.point());

        // 히스토리 누적 시도, 실패하면 예외 발생
        try {
            pointHistoryRepository.insertPointHistory(id, amount, TransactionType.USE, System.currentTimeMillis());
        } catch (Exception e) {
            throw new RuntimeException("포인트 히스토리 적재 실패");
        }

        return updateUserPoint;
    }

    // 포인트 충전/사용 내역 확인
    public List<PointHistory> getPointHistoriesByUser(long id) {
        return pointHistoryRepository.findAllById(id);
    }
}
