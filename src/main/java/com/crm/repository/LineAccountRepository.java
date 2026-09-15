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
}
