package io.hhplus.tdd.point;

import io.hhplus.tdd.point.reponse.PointHistory;
import io.hhplus.tdd.point.reponse.UserPoint;
import io.hhplus.tdd.point.repository.PointHistoryRepository;
import io.hhplus.tdd.point.repository.UserPointRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PointServiceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    @Autowired
    private PointService pointService;

    @Autowired
    private UserPointRepository userPointRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Test
    void 포인트_충전시_마이너스_포인트_입력_불가() {
        long chargeAmount = -10L;

        // mock 유저 생성: 초기 포인트: 0
        UserPoint userPoint = userPointRepository.findById(1L);

        // 예외 발생 및 검증
        assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(userPoint.id(), chargeAmount));
    }

    @Test
    void 충전시_결과값이_다른_케이스() {
        long illegalTotal = 11L;

        // mock 유저 생성: 초기 포인트: 0
        UserPoint userPoint = userPointRepository.findById(1L);

        // 10L 포인트 충전
        UserPoint updatedUserPoint = pointService.chargePoint(userPoint.id(), 10L);

        // 예상결과 - 10L, 잘못된 값 - 11L
        assertNotEquals(illegalTotal, updatedUserPoint.point());

        // 진짜 10L이 나왔었는지도 확인
        assertEquals(10L, updatedUserPoint.point());
    }

    @Test
    void 최대_포인트가_있고_충전시_넘는_케이스() {

        // mock 유저 생성: 초기 포인트: 0
        UserPoint userPoint = userPointRepository.findById(1L);

        // 최대 충전 가능 : 1000L, 그러나 충전 10000L 해서 에러 발생 필요
        assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(userPoint.id(), 10000L));
    }

    @Test
    void 포인트_충전_성공_케이스() {
        long userId = 1L;
        long amount = 10L;

        // mock 유저 생성: 초기 포인트: 0
        UserPoint userPoint = userPointRepository.findById(userId);

        // 포인트 10L 충전
        UserPoint updatedUserPoint = pointService.chargePoint(userId, amount);

        // 실체 충전 포인트가 10L인지 검증
        assertEquals(userPoint.point() + amount, updatedUserPoint.point());

        // 포인트 히스토리가 제대로 저장되는지 확인
        List<PointHistory> pointHistories = pointHistoryRepository.findAllById(userId);

        // 히스토리가 잘 쌓였는지 검증
        assertEquals(1, pointHistories.size());

        // 실제 해당하는 데이터로 쌓였는지 검증
        PointHistory history = pointHistories.get(0);
        assertEquals(userId, history.id());
        assertEquals(amount, history.amount());
        assertEquals(TransactionType.CHARGE, history.type());
    }

    @Test
    void 한유저_동시_포인트_충전_성공_케이스() throws InterruptedException {
        long userId = 1L;
        long initPoint = 100L;
        long chargeAmount = 1L;
        int numberOfCharges = 100;

        userPointRepository.upsert(userId, initPoint);

        // 스레드 풀 생성 (동시에 많은 요청 발생)
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // 100개의 충전 요청을 동시 실행
        for (int i = 0; i < numberOfCharges; i++) {
            executor.submit(() -> pointService.chargePoint(userId, chargeAmount));
        }

        // 스레드 작업 끝날 때까지 대기
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // 최종 포인트 검증
        UserPoint result = userPointRepository.findById(userId);
        assertEquals(initPoint + (chargeAmount * numberOfCharges), result.point(), "포인트가 예상과 다릅니다.");
    }

    @Test
    void 여러_사용자_동시_포인트_충전_테스트() throws InterruptedException {
        int numberOfUsers = 10;
        int numberOfCharges = 20;
        long initialPoint = 100L;
        long chargeAmount = 5L;

        // 사용자 ID 배열 생성 (1부터 100까지)
        long[] userIds = new long[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            userIds[i] = i + 1;
        }

        // 각 사용자 포인트 100으로 초기화
        for (long userId : userIds) {
            userPointRepository.upsert(userId, initialPoint);
        }

        // 스레드 풀 생성 (각 사용자에 대해 충전 요청을 동시에 실행)
        ExecutorService executor = Executors.newFixedThreadPool(userIds.length);

        // 사용자별로 충전
        for (long userId : userIds) {
            for (int i = 0; i < numberOfCharges; i++) {
                executor.submit(() -> {
                    try {
                        pointService.chargePoint(userId, chargeAmount);
                    } catch (Exception e) {
                        System.err.println("에러 발생: " + e.getMessage());
                    }
                });
            }
        }

        // 스레드 작업이 끝날 때까지 대기
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            executor.shutdownNow(); // 시간이 다 지나도 완료되지 않으면 강제 종료
        }

        // 각 사용자 포인트가 맞게 증가했는지 확인
        for (long userId : userIds) {
            UserPoint result = userPointRepository.findById(userId);
            assertEquals(100 + (5 * numberOfCharges), result.point(), "User ID " + userId + "의 포인트가 예상과 다릅니다.");
        }
    }
}
