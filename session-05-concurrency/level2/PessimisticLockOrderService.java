package com.gritmoments.backend.order.service;

import com.gritmoments.backend.common.exception.BusinessException;
import com.gritmoments.backend.common.exception.ResourceNotFoundException;
import com.gritmoments.backend.menu.entity.Menu;
import com.gritmoments.backend.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * [Session 05 - Level 2] 비관적 잠금과 분산 락 비교 실습
 *
 * 학습 목표:
 * 1. @Lock(PESSIMISTIC_WRITE)를 적용하여 동시성 문제 해결
 * 2. Redisson 분산 락을 적용하여 동일한 문제 해결
 * 3. 두 방식의 성능 차이 비교
 *
 * 시나리오:
 *   50개의 동시 요청이 메뉴 재고를 1개씩 차감 (총 50개 차감)
 *   - 잠금 없음: 경쟁 상태로 일부 차감 누락 (예: 85개 남음)
 *   - 비관적 잠금: 정확히 50개 차감 (50개 남음, DB 락 사용)
 *   - 분산 락: 정확히 50개 차감 (50개 남음, Redis 락 사용)
 *
 * TODO: 아래의 TODO 부분을 채워서 두 가지 잠금 방식을 구현하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PessimisticLockOrderService {

    private final MenuRepository menuRepository;
    private final RedissonClient redissonClient;

    /** 락 키 접두사 */
    private static final String LOCK_PREFIX = "lock:menu:";

    /** 락 대기 시간 (초) */
    private static final long WAIT_TIME = 5L;

    /** 락 점유 시간 (초) */
    private static final long LEASE_TIME = 3L;

    /**
     * 방법 1: 비관적 잠금 (PESSIMISTIC_WRITE)으로 재고 차감
     *
     * 동작 원리:
     *   1. menuRepository.findByIdWithPessimisticLock() 호출
     *   2. DB에서 SELECT ... FOR UPDATE 실행 (행 잠금)
     *   3. 다른 트랜잭션은 이 행을 읽거나 수정할 수 없음 (대기)
     *   4. 재고 차감 후 트랜잭션 커밋 시 락 해제
     *
     * TODO 1: @Transactional 어노테이션을 추가하세요
     *
     * 힌트: 이 메서드는 데이터를 변경하므로 트랜잭션이 필요합니다.
     */
    // TODO: @Transactional 추가
    public void decreaseStockWithPessimisticLock(Long menuId, int quantity) {
        // TODO 2: 비관적 잠금으로 메뉴 조회
        //
        // 처리 흐름:
        //   1. menuRepository.findByIdWithPessimisticLock(menuId)로 조회
        //   2. 메뉴가 없으면 ResourceNotFoundException 발생
        //   3. 로그 출력: 현재 재고, 차감할 수량
        //   4. menu.decreaseStock(quantity) 호출
        //   5. 로그 출력: 차감 후 재고
        //
        // 힌트:
        //   Menu menu = menuRepository.findByIdWithPessimisticLock(menuId)
        //       .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));
        //
        //   log.info("[비관적 잠금] 메뉴 ID: {}, 현재 재고: {}, 차감: {}",
        //       menuId, menu.getStock(), quantity);
        //
        //   menu.decreaseStock(quantity);
        //
        //   log.info("[비관적 잠금 완료] 메뉴 ID: {}, 남은 재고: {}", menuId, menu.getStock());

        // TODO: 위 힌트를 참고하여 코드를 작성하세요
    }

    /**
     * 방법 2: Redisson 분산 락으로 재고 차감
     *
     * 동작 원리:
     *   1. Redis에서 락 키로 잠금 획득 시도
     *   2. 락을 획득한 스레드만 재고 차감 실행
     *   3. 다른 스레드는 락이 해제될 때까지 대기
     *   4. 재고 차감 후 락 해제
     *
     * 비관적 잠금과의 차이:
     *   - 비관적 잠금: DB 커넥션을 점유, DB 부하 증가
     *   - 분산 락: Redis 메모리 기반, DB 부하 감소, 애플리케이션 레벨 제어
     *
     * TODO 3: 분산 락을 구현하세요
     */
    public void decreaseStockWithDistributedLock(Long menuId, int quantity) {
        // TODO 4: 락 키 생성
        //
        // 힌트:
        //   String lockKey = LOCK_PREFIX + menuId;

        // TODO 5: RLock 객체 가져오기
        //
        // 힌트:
        //   RLock lock = redissonClient.getLock(lockKey);

        // TODO 6: 락 획득 시도 및 재고 차감
        //
        // 처리 흐름:
        //   1. try-catch-finally 블록 작성
        //   2. lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS)로 락 획득 시도
        //   3. 락 획득 실패 시 BusinessException 발생
        //   4. 락 획득 성공 시 decreaseStockInTransaction() 호출
        //   5. finally 블록에서 락 해제 (중요!)
        //
        // 힌트:
        //   boolean isLocked = false;
        //   try {
        //       isLocked = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);
        //
        //       if (!isLocked) {
        //           log.warn("[분산 락 실패] 메뉴 {} 락 획득 대기 시간 초과", menuId);
        //           throw new BusinessException("현재 다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요.");
        //       }
        //
        //       log.info("[분산 락 획득] 메뉴 {} 재고 차감 시작", menuId);
        //       decreaseStockInTransaction(menuId, quantity);
        //       log.info("[분산 락 완료] 메뉴 {} 재고 차감 완료", menuId);
        //
        //   } catch (InterruptedException e) {
        //       Thread.currentThread().interrupt();
        //       throw new BusinessException("재고 차감 처리 중 오류가 발생했습니다.");
        //   } finally {
        //       if (isLocked && lock.isHeldByCurrentThread()) {
        //           lock.unlock();
        //           log.info("[분산 락 해제] 메뉴 {} 락 해제 완료", menuId);
        //       }
        //   }

        // TODO: 위 힌트를 참고하여 코드를 작성하세요
    }

    /**
     * 트랜잭션 내에서 재고 차감 (분산 락용 헬퍼 메서드)
     *
     * 주의: 분산 락과 DB 트랜잭션의 범위를 분리합니다.
     * - 분산 락: decreaseStockWithDistributedLock()에서 관리
     * - DB 트랜잭션: 이 메서드에서 관리
     */
    @Transactional
    public void decreaseStockInTransaction(Long menuId, int quantity) {
        // TODO 7: 메뉴 조회 및 재고 차감
        //
        // 힌트:
        //   Menu menu = menuRepository.findById(menuId)
        //       .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));
        //
        //   log.info("[트랜잭션] 메뉴 ID: {}, 현재 재고: {}, 차감: {}",
        //       menuId, menu.getStock(), quantity);
        //
        //   menu.decreaseStock(quantity);
        //
        //   log.info("[트랜잭션 완료] 메뉴 ID: {}, 남은 재고: {}", menuId, menu.getStock());

        // TODO: 위 힌트를 참고하여 코드를 작성하세요
    }

    /**
     * 현재 재고 확인 (읽기 전용)
     */
    @Transactional(readOnly = true)
    public int getCurrentStock(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));
        return menu.getStock();
    }
}
