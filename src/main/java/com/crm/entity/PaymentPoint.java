package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** pt granted with a manual 入金 on ユーザー詳細 (added to the member's 所持ポイント). */
@Entity
@Table(name = "PAYMENT_POINT")
public class PaymentPoint {

    @Id
    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    @Column(name = "POINTS", nullable = false)
    private int points;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
}
