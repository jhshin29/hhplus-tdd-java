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
import java.util.concurrent.CountDownLatch;
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
    void 포인트_차감시_플러스_포인트_입력_불가() {
        long useAmount = 10L;

        // mock 유저 생성: 초기 포인트: 0
        UserPoint userPoint = userPointRepository.findById(1L);

        // 예외 발생 및 검증
        assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(userPoint.id(), useAmount));
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

    // 의도된 케이스라고 가정 (정말 한 사람에게 여러 번 충전)
    @Test
    void 한유저_동시_포인트_충전_성공_케이스() throws InterruptedException {
        long userId = 1L;
        long initPoint = 100L;
        long chargeAmount = 1L;
        int numberOfCharges = 10;

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
    void 여러_사용자_동시_포인트_충전_테스트_순서보장() throws InterruptedException {
        int numberOfUsers = 3;
        int numberOfCharges = 3;
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

        // CountDownLatch 생성 (각 충전 작업이 준비될 때까지 대기)
        CountDownLatch readyLatch = new CountDownLatch(numberOfUsers);
        CountDownLatch startLatch = new CountDownLatch(1);

        // 스레드 풀 생성 (각 사용자에 대해 충전 요청을 동시에 실행)
        ExecutorService executor = Executors.newFixedThreadPool(userIds.length);

        // 사용자별로 충전
        for (long userId : userIds) {
            for (int i = 0; i < numberOfCharges; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown(); // 모든 스레드가 준비될 떄 까지 대기
                        startLatch.await();
                        System.out.printf("Thread for User %s - 충전 시작%n", userId);
                        UserPoint userPoint = pointService.chargePoint(userId, chargeAmount);
                        System.out.printf("Thread for User %s, 충전 완료 후 포인트: %s%n", userId, userPoint.point());
                    } catch (Exception e) {
                        System.err.println("에러 발생: " + e.getMessage());
                    }
                });
            }
        }

        readyLatch.await();
        startLatch.countDown();

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

    @Test
    void 여러_사용자_동시_포인트_충전_최대포인트_초과_실패_케이스() throws InterruptedException {
        int numberOfUsers = 3;
        int numberOfCharges = 3;
        long initialPoint = 100L;
        long chargeAmount = 500L;

        // 사용자 ID 배열 생성 (1부터 100까지)
        long[] userIds = new long[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            userIds[i] = i + 1;
        }

        // 각 사용자 포인트 100으로 초기화
        for (long userId : userIds) {
            userPointRepository.upsert(userId, initialPoint);
        }

        // CountDownLatch 생성 (각 충전 작업이 준비될 때까지 대기)
        CountDownLatch readyLatch = new CountDownLatch(numberOfUsers);
        CountDownLatch startLatch = new CountDownLatch(1);

        // 스레드 풀 생성 (각 사용자에 대해 충전 요청을 동시에 실행)
        ExecutorService executor = Executors.newFixedThreadPool(userIds.length);

        // 사용자별로 충전
        for (long userId : userIds) {
            for (int i = 0; i < numberOfCharges; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown(); // 모든 스레드가 준비될 떄 까지 대기
                        startLatch.await();
                        System.out.printf("Thread for User %s - 충전 시작%n", userId);
                        assertThrows(IllegalArgumentException.class, () -> {
                            pointService.chargePoint(userId, chargeAmount); // 충전 시 예외 발생을 기대
                        }, "최대 포인트 초과 예외 발생");
                    } catch (Exception e) {
                        System.err.println("에러 발생: " + e.getMessage());
                    }
                });
            }
        }

        readyLatch.await();
        startLatch.countDown();

        // 스레드 작업이 끝날 때까지 대기
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            executor.shutdownNow(); // 시간이 다 지나도 완료되지 않으면 강제 종료
        }
    }

    @Test
    void 여러_사용자_동시_포인트_차감_테스트_순서보장() throws InterruptedException {
        int numberOfUsers = 3;
        int numberOfCharges = 3;
        long initialPoint = 100L;
        long useAmount = -5L;

        // 사용자 ID 배열 생성 (1부터 100까지)
        long[] userIds = new long[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            userIds[i] = i + 1;
        }

        // 각 사용자 포인트 100으로 초기화
        for (long userId : userIds) {
            userPointRepository.upsert(userId, initialPoint);
        }

        // CountDownLatch 생성 (각 충전 작업이 준비될 때까지 대기)
        CountDownLatch readyLatch = new CountDownLatch(numberOfUsers);
        CountDownLatch startLatch = new CountDownLatch(1);

        // 스레드 풀 생성 (각 사용자에 대해 충전 요청을 동시에 실행)
        ExecutorService executor = Executors.newFixedThreadPool(userIds.length);

        // 사용자별로 충전
        for (long userId : userIds) {
            for (int i = 0; i < numberOfCharges; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown(); // 모든 스레드가 준비될 떄 까지 대기
                        startLatch.await();
                        System.out.printf("Thread for User %s - 차감 시작%n", userId);
                        UserPoint userPoint = pointService.usePoint(userId, useAmount);
                        System.out.printf("Thread for User %s, 차감 완료 후 포인트: %s%n", userId, userPoint.point());
                    } catch (Exception e) {
                        System.err.println("에러 발생: " + e.getMessage());
                    }
                });
            }
        }

        readyLatch.await();
        startLatch.countDown();

        // 스레드 작업이 끝날 때까지 대기
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            executor.shutdownNow(); // 시간이 다 지나도 완료되지 않으면 강제 종료
        }

        // 각 사용자 포인트가 맞게 증가했는지 확인
        for (long userId : userIds) {
            UserPoint result = userPointRepository.findById(userId);
            assertEquals(100 - (5 * numberOfCharges), result.point(), "User ID " + userId + "의 포인트가 예상과 다릅니다.");
        }
    }

    @Test
    void 여러_사용자_동시_포인트_차감_잔고_0미만_실패_케이스() throws InterruptedException {
        int numberOfUsers = 3;
        int numberOfCharges = 3;
        long initialPoint = 100L;
        long useAmount = -500L;

        // 사용자 ID 배열 생성 (1부터 100까지)
        long[] userIds = new long[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            userIds[i] = i + 1;
        }

        // 각 사용자 포인트 100으로 초기화
        for (long userId : userIds) {
            userPointRepository.upsert(userId, initialPoint);
        }

        // CountDownLatch 생성 (각 충전 작업이 준비될 때까지 대기)
        CountDownLatch readyLatch = new CountDownLatch(numberOfUsers);
        CountDownLatch startLatch = new CountDownLatch(1);

        // 스레드 풀 생성 (각 사용자에 대해 충전 요청을 동시에 실행)
        ExecutorService executor = Executors.newFixedThreadPool(userIds.length);

        // 사용자별로 충전
        for (long userId : userIds) {
            for (int i = 0; i < numberOfCharges; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown(); // 모든 스레드가 준비될 떄 까지 대기
                        startLatch.await();
                        System.out.printf("Thread for User %s - 차감 시작%n", userId);
                        assertThrows(IllegalArgumentException.class, () -> {
                            pointService.usePoint(userId, useAmount);
                        }, "최소 포인트 미만 예외 발생");
                    } catch (Exception e) {
                        System.err.println("에러 발생: " + e.getMessage());
                    }
                });
            }
        }

        readyLatch.await();
        startLatch.countDown();

        // 스레드 작업이 끝날 때까지 대기
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            executor.shutdownNow(); // 시간이 다 지나도 완료되지 않으면 강제 종료
        }
    }

    @Test
    void 여러_사용자_동시_포인트_충전_차감_테스트_순서보장() throws InterruptedException {
        int numberOfUsers = 3;
        int numberOfOperations = 3; // 충전 및 차감 작업의 반복 횟수
        long initialPoint = 100L;
        long chargeAmount = 5L;
        long useAmount = -3L;

        // 사용자 ID 배열 생성 (1부터 3까지)
        long[] userIds = new long[numberOfUsers];
        for (int i = 0; i < numberOfUsers; i++) {
            userIds[i] = i + 1;
        }

        // 각 사용자 포인트 100으로 초기화
        for (long userId : userIds) {
            userPointRepository.upsert(userId, initialPoint);
        }

        // CountDownLatch 생성 (각 충전 및 차감 작업이 준비될 때까지 대기)
        CountDownLatch readyLatch = new CountDownLatch(numberOfUsers * 2); // 충전 및 차감 작업을 모두 준비시키기 위해 2배
        CountDownLatch startLatch = new CountDownLatch(1);

        // 스레드 풀 생성 (각 사용자에 대해 충전 및 차감 요청을 동시에 실행)
        ExecutorService executor = Executors.newFixedThreadPool(userIds.length * 2); // 충전 및 차감 모두 처리하기 위해 2배 크기의 스레드 풀

        // 사용자별로 충전 및 차감 작업을 동시에 실행
        for (long userId : userIds) {
            for (int i = 0; i < numberOfOperations; i++) {
                // 충전 작업
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        System.out.printf("Thread for User %s - 충전 시작%n", userId);
                        UserPoint userPoint = pointService.chargePoint(userId, chargeAmount);
                        System.out.printf("Thread for User %s, 충전 완료 후 포인트: %s%n", userId, userPoint.point());
                    } catch (Exception e) {
                        System.err.println("에러 발생 (충전): " + e.getMessage());
                    }
                });

                // 차감 작업
                executor.submit(() -> {
                    try {
                        readyLatch.countDown(); // 모든 스레드가 준비될 때까지 대기
                        startLatch.await(); // 동시에 시작되도록 대기
                        System.out.printf("Thread for User %s - 차감 시작%n", userId);
                        UserPoint userPoint = pointService.usePoint(userId, useAmount);
                        System.out.printf("Thread for User %s, 차감 완료 후 포인트: %s%n", userId, userPoint.point());
                    } catch (Exception e) {
                        System.err.println("에러 발생 (차감): " + e.getMessage());
                    }
                });
            }
        }

        readyLatch.await();
        startLatch.countDown();

        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            executor.shutdownNow();
        }

        // 각 사용자 포인트가 예상대로 남아있는지 확인
        for (long userId : userIds) {
            UserPoint result = userPointRepository.findById(userId);
            long expectedFinalPoint = initialPoint + (chargeAmount * numberOfOperations) + (useAmount * numberOfOperations);
            assertEquals(expectedFinalPoint, result.point(), "User ID " + userId + "의 포인트가 예상과 다릅니다.");
        }
    }
}
