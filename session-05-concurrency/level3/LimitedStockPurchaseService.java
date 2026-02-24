package com.gritmoments.backend.menu.service;

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
 * [Session 05 - Level 3] Redisson 분산 락을 이용한 한정판 메뉴 구매 서비스
 *
 * 시나리오: 한정 수량 10개인 특별 메뉴를 100명이 동시에 구매하려 합니다.
 *
 * 문제 상황 (락 없이):
 *   Thread A: stock=10 읽음 -> stock=9 저장
 *   Thread B: stock=10 읽음 -> stock=9 저장  <-- 같은 재고를 읽어서 중복 차감!
 *   결과: 2명이 구매했는데 재고는 1개만 줄어듦 (정합성 깨짐)
 *
 * 해결 (분산 락):
 *   Thread A: 락 획득 -> stock=10 읽음 -> stock=9 저장 -> 락 해제
 *   Thread B: 락 대기 ------> 락 획득 -> stock=9 읽음 -> stock=8 저장 -> 락 해제
 *   결과: 순차 처리로 정합성 보장
 *
 * DB 비관적 잠금 vs 분산 락:
 *   - DB 비관적 잠금: SELECT FOR UPDATE -> DB 커넥션을 점유, DB 부하 증가
 *   - 분산 락 (Redis): 애플리케이션 레벨에서 락 -> DB 부하 감소, 더 유연
 *
 * TODO: 아래의 TODO 부분을 채워서 분산 락 기반 구매를 완성하세요.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LimitedStockPurchaseService {

    private final MenuRepository menuRepository;
    private final RedissonClient redissonClient;

    /** 락 키 접두사 */
    private static final String LOCK_PREFIX = "lock:menu:stock:";

    /** 락 대기 시간 (초) - 이 시간 내에 락을 얻지 못하면 실패 */
    private static final long WAIT_TIME = 5L;

    /** 락 점유 시간 (초) - 이 시간이 지나면 자동으로 락 해제 (데드락 방지) */
    private static final long LEASE_TIME = 3L;

    /**
     * 한정판 메뉴 구매 (분산 락 적용)
     *
     * @param menuId 구매할 메뉴 ID
     * @param quantity 구매 수량
     * @param userId 구매자 ID
     */
    public void purchaseLimitedMenu(Long menuId, int quantity, Long userId) {
        // 락 키: 메뉴별로 별도의 락을 사용 (메뉴 A의 구매가 메뉴 B를 차단하지 않도록)
        String lockKey = LOCK_PREFIX + menuId;

        // TODO 1: Redisson에서 RLock 객체를 가져오세요
        //
        // 힌트:
        //   RLock lock = redissonClient.getLock(lockKey);

        // TODO 2: 분산 락 획득을 시도하세요
        //
        // tryLock 메서드:
        //   - waitTime: 락을 얻기 위해 최대 대기하는 시간
        //   - leaseTime: 락을 보유할 최대 시간 (데드락 방지)
        //   - timeUnit: 시간 단위
        //   - 반환값: true(락 획득 성공), false(시간 내 실패)
        //
        // 힌트:
        //   boolean isLocked = false;
        //   try {
        //       isLocked = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);
        //
        //       if (!isLocked) {
        //           // 락 획득 실패 - 다른 사용자가 구매 중
        //           log.warn("[락 실패] 메뉴 {} 구매 대기 시간 초과. 사용자: {}", menuId, userId);
        //           throw new BusinessException("현재 다른 사용자가 구매 중입니다. 잠시 후 다시 시도해주세요.");
        //       }
        //
        //       // 락 획득 성공 - 재고 차감 실행
        //       log.info("[락 획득] 메뉴 {} 재고 차감 시작. 사용자: {}", menuId, userId);
        //       decreaseStockInTransaction(menuId, quantity);
        //       log.info("[구매 완료] 메뉴 {} 수량 {} 구매 성공. 사용자: {}", menuId, quantity, userId);
        //
        //   } catch (InterruptedException e) {
        //       Thread.currentThread().interrupt();
        //       throw new BusinessException("구매 처리 중 오류가 발생했습니다.");
        //   } finally {
        //       // TODO 3: 락 해제
        //       // 반드시 finally 블록에서 해제해야 합니다!
        //       // 예외가 발생해도 락이 해제되어야 다른 요청이 처리됩니다.
        //       //
        //       // 힌트:
        //       //   if (lock.isHeldByCurrentThread()) {
        //       //       lock.unlock();
        //       //       log.info("[락 해제] 메뉴 {} 락 해제 완료", menuId);
        //       //   }
        //   }
    }

    /**
     * 트랜잭션 내에서 재고 차감
     *
     * 주의: 분산 락과 DB 트랜잭션의 범위를 분리합니다.
     * - 분산 락: purchaseLimitedMenu() 메서드에서 관리
     * - DB 트랜잭션: 이 메서드에서 관리
     *
     * 왜 분리하나요?
     * 트랜잭션이 커밋되기 전에 락이 해제되면, 다른 스레드가
     * 아직 커밋되지 않은 데이터를 읽을 수 있습니다.
     * 따라서 트랜잭션 범위를 락 범위보다 작게 유지합니다.
     */
    @Transactional
    public void decreaseStockInTransaction(Long menuId, int quantity) {
        // TODO 4: 메뉴를 조회하고 재고를 차감하세요
        //
        // 처리 흐름:
        //   1. menuRepository.findById(menuId)로 메뉴 조회
        //   2. 메뉴가 없으면 ResourceNotFoundException 발생
        //   3. 현재 재고를 로그로 출력
        //   4. menu.decreaseStock(quantity) 호출하여 재고 차감
        //   5. 차감 후 재고를 로그로 출력
        //
        // 힌트:
        //   Menu menu = menuRepository.findById(menuId)
        //       .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));
        //   log.info("[재고 차감] 메뉴: {}, 현재 재고: {}, 차감: {}",
        //       menu.getName(), menu.getStock(), quantity);
        //   menu.decreaseStock(quantity);
        //   log.info("[재고 차감 완료] 메뉴: {}, 남은 재고: {}", menu.getName(), menu.getStock());
    }

    /**
     * 현재 재고 확인 (락 없이 조회만)
     *
     * 재고 확인은 정확할 필요가 없으므로 락 없이 조회합니다.
     * ("대략 몇 개 남았는지"만 보여주는 용도)
     */
    @Transactional(readOnly = true)
    public int getCurrentStock(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu", menuId));
        return menu.getStock();
    }
}
