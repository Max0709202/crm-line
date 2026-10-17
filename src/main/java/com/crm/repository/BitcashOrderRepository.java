package com.crm.repository;

import com.crm.entity.BitcashOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BitcashOrderRepository extends JpaRepository<BitcashOrder, Long> {

    /** The order row locked while a 完了通知 / 決済確認 credits it (a second one waits, then sees PAID). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM BitcashOrder o WHERE o.id = :id")
    Optional<BitcashOrder> findForUpdate(@Param("id") Long id);

    Optional<BitcashOrder> findByTranId(String tranId);

    /** A member's orders still waiting for payment (checked with 決済確認処理 when they come back). */
    List<BitcashOrder> findTop5ByUserIdAndStatusAndCreatedAtAfterOrderByIdDesc(Long userId, String status, LocalDateTime after);

    /** Latest orders, newest first (決済関連設定's 最近の決済). */
    List<BitcashOrder> findTop10ByOrderByIdDesc();
}
