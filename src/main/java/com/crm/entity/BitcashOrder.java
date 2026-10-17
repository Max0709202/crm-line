package com.crm.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

/** A ポイント購入 sent to ビットキャッシュ's 決済画面 (see BitcashService). */
@Entity
@Table(name = "BITCASH_ORDER")
public class BitcashOrder {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "METHOD", nullable = false)
    private String method;

    @Column(name = "AMOUNT", nullable = false)
    private Integer amount;

    @Column(name = "POINTS", nullable = false)
    private Integer points;

    @Column(name = "STATUS", nullable = false)
    private String status;

    /** 決済トランザクションID returned by 決済開始処理. */
    @Column(name = "TRAN_ID")
    private String tranId;

    /** 管理番号 (from 決済確認処理; the number a member quotes to ビットキャッシュ). */
    @Column(name = "CNO")
    private String cno;

    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    @Column(name = "RESULT_PARAMS", columnDefinition = "TEXT")
    private String resultParams;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "PAID_AT")
    private LocalDateTime paidAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = STATUS_PENDING;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTranId() { return tranId; }
    public void setTranId(String tranId) { this.tranId = tranId; }
    public String getCno() { return cno; }
    public void setCno(String cno) { this.cno = cno; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public String getResultParams() { return resultParams; }
    public void setResultParams(String resultParams) { this.resultParams = resultParams; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
