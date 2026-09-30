package com.crm.repository;

import com.crm.entity.LineAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LineAccountRepository extends JpaRepository<LineAccount, Long> {

    List<LineAccount> findByParentAccountIdIsNullOrderByNameAsc();

    List<LineAccount> findByParentAccountIdOrderByNameAsc(Long parentAccountId);

    long countByParentAccountId(Long parentAccountId);

    boolean existsByChannelId(String channelId);

    boolean existsByWebhookToken(String webhookToken);

    Optional<LineAccount> findByWebhookToken(String webhookToken);

    /** 接続エラー accounts for the dashboard warning, and the auto connection-check targets. */
    List<LineAccount> findByStatusOrderByNameAsc(String status);

    List<LineAccount> findByStatusIn(java.util.Collection<String> statuses);
}
