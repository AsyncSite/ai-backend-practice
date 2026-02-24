package com.gritmoments.backend.menu.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gritmoments.backend.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메뉴 엔티티 (세션 01: 캐시, 세션 05: 동시성)
 *
 * 가게에서 판매하는 메뉴 항목입니다.
 * - stock(재고): 세션 05 동시성 실습에서 핵심 필드
 *   동시 주문 시 재고 정합성을 보장하는 것이 핵심 과제
 * - 캐시 적용 대상: 메뉴 목록은 자주 조회됨
 */
@Entity
@Table(name = "menus", indexes = {
        @Index(name = "idx_menus_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_menus_restaurant_available", columnList = "restaurant_id, is_available")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer price;

    /**
     * 재고 수량 (세션 05: 동시성 제어의 핵심)
     * 여러 요청이 동시에 재고를 차감할 때 정합성 문제가 발생할 수 있음
     */
    @Column(nullable = false)
    private Integer stock = 100;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    /**
     * 낙관적 잠금용 버전 필드 (세션 05)
     * JPA가 UPDATE 시 WHERE version = ? 조건을 자동 추가
     */
    @Version
    private Long version;

    @Builder
    public Menu(Restaurant restaurant, String name, String description, Integer price, Integer stock) {
        this.restaurant = restaurant;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock != null ? stock : 100;
    }

    /**
     * 재고 차감 (세션 05: 동시성 문제 발생 가능)
     * 잠금 없이 호출하면 경쟁 상태(Race Condition) 발생
     */
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException("재고가 부족합니다. 현재 재고: " + this.stock);
        }
        this.stock -= quantity;
    }

    /** 재고 복원 (주문 취소 시) */
    public void increaseStock(int quantity) {
        this.stock += quantity;
    }
}
