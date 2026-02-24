package com.gritmoments.backend.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gritmoments.backend.menu.entity.Menu;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목 엔티티
 *
 * 하나의 주문(Order)에 여러 메뉴가 포함될 수 있습니다.
 * 주문 시점의 가격을 별도 저장합니다 (메뉴 가격이 나중에 변경되어도 주문 기록 유지).
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    /** 주문 시점의 가격 (메뉴 가격이 변경되어도 영향 없음) */
    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer quantity;

    @Builder
    public OrderItem(Menu menu, Integer quantity) {
        this.menu = menu;
        this.menuName = menu.getName();
        this.price = menu.getPrice();
        this.quantity = quantity;
    }

    /** Order에서 호출 (양방향 관계 설정) */
    void setOrder(Order order) {
        this.order = order;
    }
}
