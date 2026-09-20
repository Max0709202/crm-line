package com.crm.repository;

import com.crm.entity.LineUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LineUserRepository extends JpaRepository<LineUser, Long> {

    Optional<LineUser> findByLineAccountIdAndLineUserId(Long lineAccountId, String lineUserId);

    List<LineUser> findByCrmUserIdIsNullOrderByLastMessageAtDesc();

    List<LineUser> findByCrmUserId(Long crmUserId);

    /** Batch lookup for broadcast targeting — avoids one query per recipient when checking
     *  which of a broadcast's target users are linked to the specific LineAccount being
     *  sent from (see BroadcastService.createAndQueueLine). */
    List<LineUser> findByLineAccountIdAndCrmUserIdIn(Long lineAccountId, java.util.Collection<Long> crmUserIds);

    /** Friend count shown per account on the LINE settings list. */
    long countByLineAccountId(Long lineAccountId);

    /** Which of these CrmUser ids have at least one linked LineUser row — drives the user
     *  list's キャリア=LINE badge/filter (client request 2026-09-20). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT lu.crmUserId FROM LineUser lu WHERE lu.crmUserId IN :crmUserIds")
    List<Long> findLinkedCrmUserIds(@org.springframework.data.repository.query.Param("crmUserIds") java.util.Collection<Long> crmUserIds);
}
