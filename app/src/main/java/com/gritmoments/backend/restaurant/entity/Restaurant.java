package com.gritmoments.backend.restaurant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gritmoments.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 가게 엔티티 (세션 10: 아키텍처, 세션 01: 캐시)
 *
 * 음식을 판매하는 가게 정보입니다.
 * - 가게 주인(OWNER)이 등록
 * - 캐시 적용 대상 (자주 조회되지만 변경은 드물기 때문)
 */
@Entity
@Table(name = "restaurants", indexes = {
        @Index(name = "idx_restaurants_owner", columnList = "owner_id"),
        @Index(name = "idx_restaurants_category", columnList = "category")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(name = "min_order_amount")
    private Integer minOrderAmount = 0;

    @Column(name = "delivery_fee")
    private Integer deliveryFee = 0;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "is_open", nullable = false)
    private Boolean isOpen = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Restaurant(String name, String category, String address, String phone,
                      Integer minOrderAmount, Integer deliveryFee, User owner) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.phone = phone;
        this.minOrderAmount = minOrderAmount != null ? minOrderAmount : 0;
        this.deliveryFee = deliveryFee != null ? deliveryFee : 0;
        this.owner = owner;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void open() { this.isOpen = true; }
    public void close() { this.isOpen = false; }
}
