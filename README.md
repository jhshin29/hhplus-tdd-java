# 동시성 제어 방식 분석 보고서

## 동시성 제어(Concurrency Control)는 무엇일까?

여러 스레드나 프로세스가 동시에 실행될 때 발생할 수 있는 충돌이나 예기치 않은 동작을 방지하는 방법
동시에 실행되는 작업들이 서로 충돌하지 않고 올바르게 수행할 수 있도록 한다.

## 동시성 문제의 원인은?

여러 스레드가 동시에 공유 자원에 접근할 때 발생한다. (변수, 메모리, DB 등)
동시적으로 같은 자원에 접근하거나 변경하려는 작업이 있을 경우, 원래 생각했던 결과가 나오지 않을 수 있다.
즉, 경쟁 상태(Race Condition) 나 데드락(Deadlock), 기아 상태(Starvation)에 빠질 수 있으며 이러한 문제를 해결하기 위해 동시성 제어가 필요하다.

## Java 동시성 제어 기법의 몇 가지 예시
* synchronized
  * 특정 메서드나 블록을 `synchronized`로 선언하면, 해당 코드에 동시에 한 스레드만 접근할 수 있도록 한다.
  * `synchronized`는 객체의 모니터 락(Monitor Lock)을 사용하여 스레드 간의 동시성을 제어합니다.
* ReentrantLock (재진입 락)
  * 동일한 스레드가 이미 락을 가지고 있을 때 다시 락을 획득 가능.
  * 락을 걸 때 `lock()`을 호출하고, `finally` 블록에서 `unlock()`을 호출하여 해제해야 함.
* CountDownLatch
  * N개의 스레드가 작업을 완료할 때까지 기다린 후, 작업을 실행
  * 이번 테스트에서 아래와 같이 사용한 부분 있음 (준비 될 때까지 대기 + 한 번에 실행)
    ```
    // CountDownLatch 생성 (각 충전 및 차감 작업이 준비될 때까지 대기)
    CountDownLatch readyLatch = new CountDownLatch(numberOfUsers * 2); // 충전 및 차감 작업을 모두 준비시키기 위해 2배
    CountDownLatch startLatch = new CountDownLatch(1);
    
    ... 
    readyLatch.countDown();
    startLatch.await();
    ...
    readyLatch.await();
    startLatch.countDown();
    ```
* Atomic 클래스들
  * `AtomicInteger`, `AtomicLong` 등의 원자성 클래스를 사용하면 락을 걸지 않고도 안전한 연산 수행 가능
  * 내부적으로 CAS(Compare-And-Swap) 연산을 제공하며 락 없이도 동시성 보장
* 이외에도 다양한 동시성 제어 기법들이 있음.

### 추가적인 스터디 필요 부분
* 이번 테스트 코드는 단일 서버 환경에서 동시성 및 순서 보장을 고려하여 작성함.
* 멀티 인스턴스 또는 분산 시스템 환경에서 여러 서버 인스턴스에서 동시 요청이 들어올 때 발생할 수 있는 동시성 문제와 순서 보장 문제에 대한 추가적인 전략 필요.
  * Redis 를 사용한 분산 락 서비스 또는 DB 트랜잭션 관리 등의 방법을 취할 수 있을 것으로 보임
  * 분산 환경에서의 정합성 + 성능 효율화도 고려해야 할 것으로 보임