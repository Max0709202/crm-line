package com.crm.repository;

import com.crm.entity.TelecomOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface TelecomOrderRepository extends JpaRepository<TelecomOrder, Long> {

    /** The order row locked for the notice being handled (a resent notice waits, then sees PAID). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM TelecomOrder o WHERE o.id = :id")
    Optional<TelecomOrder> findForUpdate(@Param("id") Long id);

    Optional<TelecomOrder> findBySettleUuid(String settleUuid);

    /** Latest orders, newest first (決済関連設定's 最近の決済). */
    java.util.List<TelecomOrder> findTop10ByOrderByIdDesc();
}
