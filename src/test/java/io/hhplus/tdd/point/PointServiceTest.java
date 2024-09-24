package io.hhplus.tdd.point;

import io.hhplus.tdd.point.reponse.PointHistory;
import io.hhplus.tdd.point.reponse.UserPoint;
import io.hhplus.tdd.point.repository.PointHistoryRepository;
import io.hhplus.tdd.point.repository.UserPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    private static final Logger log = LoggerFactory.getLogger(PointServiceTest.class);

    @Mock
    private UserPointRepository userPointRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private PointService pointService;

    private UserPoint mockPoint;

    @BeforeEach
    void initUserPoint() {
        mockPoint = new UserPoint(1L, 100L, System.currentTimeMillis());
    }

    /**
     * 유저의 포인트 조회 시, 존재하지 않는 유저 id에 대한 에러 케이스
     * @BeforEach에서 생성된 mockPoint의 id는 1L이나 2L를 조회하여 에러 발생되는 것을 확인
     */
    @Test
    void 포인트_조회시_미존재_유저_검사() {

        // 조회하고자 하는 유저가 없을 때 예외 발생 케이스
        when(pointService.getPointByUser(2L))
                .thenThrow(new IllegalArgumentException("User not found"));

        // 예외 발생 및 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.getPointByUser(2L));
    }

    @Test
    void 유저_포인트_조회_성공_케이스() {

        // userPointRepository가 Mock이기 때문에 Stubbing으로 결과 지정
        when(pointService.getPointByUser(1L)).thenReturn(mockPoint);

        // 유저 포인트 조회
        UserPoint result = pointService.getPointByUser(1L);

        // 맞는 유저의 포인트 및 수량인지 검증
        assertEquals(100L, result.point());
    }

    @Test
    void 포인트_충전할_유저_미존재() {
        long id = 777L;
        long amount = 100L;

        // 유저 미존재 시 예외 발생 stubbing
        when(userPointRepository.findById(id))
                .thenThrow(new IllegalArgumentException("User not found"));

        // 예외 발생 및 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(id, amount));
    }

    @Test
    void 포인트_충전시_마이너스_포인트_입력_불가() {
        long id = 1L;
        long amount = -1L;

        // 유저가 조회될 때 가짜 데이터 반환
        when(userPointRepository.findById(id)).thenReturn(mockPoint);

        // 포인트 충전 시, amount가 음수이므로 예외 발생 및 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(id, amount));
    }

    @Test
    void 충전시_결과값이_다른_케이스() {
        long id = 1L;
        long amount = 10L;
        long illegalTotal = 111L;

        // given: userPointRepository stubbing
        when(userPointRepository.findById(1L)).thenReturn(mockPoint);

        // when: 10L 포인트 충전
        UserPoint updatedUserPoint = pointService.chargePoint(id, amount);

        // then: 예상결과 - 110L, 잘못된 값 - 111L
        assertNotEquals(illegalTotal, updatedUserPoint.point(), "포인트 total이 올바르지 않음");

        // 실제 110L이 나왔었는지도 검증
        assertEquals(110L, updatedUserPoint.point(), "포인트 합계가 예상 값과 일치");
    }

    @Test
    void 최대_포인트가_있고_충전시_넘는_케이스() {
        long id = 1L;
        long amount = 1000L;

        // given: userPointRepository stubbing
        when(userPointRepository.findById(1L)).thenReturn(mockPoint);

        // 최대 포인트: 1000L / 예상 포인트 1100L -> 에러 발생 및 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(id, amount));
    }

    @Test
    void 포인트_히스토리가_적재되지_않은_케이스() {
        long id = 1L;
        long amount = -100L;

        // userPointRepository stubbing
        when(userPointRepository.findById(1L)).thenReturn(mockPoint);

        // 히스토리 저장 시 예외를 던지도록 설정
        doThrow(new RuntimeException("히스토리 누적 실패")).when(pointHistoryRepository)
                .insertPointHistory(id, amount, TransactionType.USE, System.currentTimeMillis());

        // 예외 발생 확인
        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> pointService.usePoint(id, amount));

        // runTimeException이 실제 났는지 검증
        assertEquals("포인트 히스토리 적재 실패", runtimeException.getMessage());
    }

    // 포인트 충전 완료
    // 포인트를 충전 + 히스토리 적재
    @Test
    void 포인트_충전_완료_케이스() {
        long id = 1L;
        long amount = 10L;

        // stubbing
        when(userPointRepository.findById(id)).thenReturn(mockPoint);

        // 포인트 10L 충전
        UserPoint updatedUserPoint = pointService.chargePoint(id, amount);

        // 실제 충전 포인트가 기대한 바와 같은지 검증
        assertEquals(mockPoint.point() + amount, updatedUserPoint.point());

        // 포인트 히스토리가 정상 저장되었는지 검증
        verify(pointHistoryRepository).insertPointHistory(eq(id), eq(amount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    void 포인트_차감할_유저_미존재() {
        long id = 777L;
        long amount = 100L;

        // 유저 미존재 시 예외 발생 stubbing
        when(userPointRepository.findById(id))
                .thenThrow(new IllegalArgumentException("User not found"));

        // 예외 발생 및 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(id, amount));
    }

    @Test
    void 포인트_사용시_플러스_포인트_입력_불가() {
        long id = 1L;
        long amount = 1L;

        // 유저가 조회될 때 가짜 데이터 반환
        when(userPointRepository.findById(id)).thenReturn(mockPoint);

        // 포인트 충전 시, amount가 음수이므로 예외 발생 및 확인
        assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(id, amount));
    }

    @Test
    void 포인트_사용시_잔고가_마이너스_케이스() {
        long id = 1L;
        long amount = -101L;

        // 포인트 사용 시, 토탈 금액이 마이너스인 경우 예외 발생 지정 (현재 포인트 100L)
        when(userPointRepository.findById(id)).thenReturn(mockPoint);

        // 예외 발생 및 확인 -> 현재 포인트 100 - 101 -> 음수가 나오므로 예외 발생
        assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(id, amount));
    }

    @Test
    void 포인트_차감_완료_케이스() {
        long id = 1L;
        long amount = -10L;

        // stubbing
        when(userPointRepository.findById(id)).thenReturn(mockPoint);

        // 포인트 10L 차감
        UserPoint updatedUserPoint = pointService.usePoint(id, amount);

        // 실제 충전 포인트가 기대한 바와 같은지 검증
        assertEquals(mockPoint.point() + amount, updatedUserPoint.point());

        // 포인트 히스토리가 정상 저장되었는지 검증
        verify(pointHistoryRepository).insertPointHistory(eq(id), eq(amount), eq(TransactionType.USE), anyLong());
    }

    @Test
    void 포인트_히스토리_조회_성공_케이스() {
        long id = 1L;

        // given: 가짜 포인트 히스토리 데이터 생성
        List<PointHistory> mockHistoryList = Arrays.asList(
                PointHistory.create(1L, id, 100L, TransactionType.CHARGE),
                PointHistory.create(2L, id, -50L, TransactionType.USE)
        );

        // pointHistoryRepository stubbing
        when(pointHistoryRepository.findAllById(id)).thenReturn(mockHistoryList);

        // when: 실제 리스트 get
        List<PointHistory> pointHistoriesByUser = pointService.getPointHistoriesByUser(id);

        // then: 리스트 같은지? 값 같은지 검증
        assertEquals(mockHistoryList.size(), pointHistoriesByUser.size());
        assertEquals(pointHistoriesByUser.get(0).amount(), 100L);
        assertEquals(pointHistoriesByUser.get(1).amount(), mockHistoryList.get(1).amount());

        // 원하는 메서드 호출했는지 검증
        verify(pointHistoryRepository, times(1)).findAllById(id);
    }
    
    // 동시에 여러 건의 포인트 충전, 이용 요청이 들어올 경우 순차적으로 처리 - 통합 테스트 진행
}