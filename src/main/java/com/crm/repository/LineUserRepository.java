package com.crm.repository;

import com.crm.entity.LineUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LineUserRepository extends JpaRepository<LineUser, Long> {

    Optional<LineUser> findByLineAccountIdAndLineUserId(Long lineAccountId, String lineUserId);

    List<LineUser> findByCrmUserIdIsNullOrderByLastMessageAtDesc();

    List<LineUser> findByCrmUserId(Long crmUserId);

    /** Same LINE person's already-linked friend rows on our other accounts — lets a new
     *  friend-add join the existing customer instead of creating a duplicate one. */
    List<LineUser> findByLineUserIdAndCrmUserIdIsNotNullOrderByIdAsc(String lineUserId);

    /** Batch version of {@link #findByCrmUserId} for resolving each recipient's currently-
     *  linked LINE account at diff-step fire time ("紐づきアカ" — a recipient friended via
     *  more than one child account resolves to whichever they messaged most recently). */
    List<LineUser> findByCrmUserIdInOrderByLastMessageAtDesc(java.util.Collection<Long> crmUserIds);

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

    /** Resolves a partial LINE display-name match to linked CrmUser ids — drives 差分スケジュール
     *  の「LINE名前縛り」(client request 2026-09-25). Same partial/case-insensitive convention
     *  as the phone/email target-type matching in CrmUserService. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT lu.crmUserId FROM LineUser lu " +
            "WHERE lu.crmUserId IS NOT NULL AND LOWER(lu.lineDisplayName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Long> findLinkedCrmUserIdsByDisplayNameContaining(@org.springframework.data.repository.query.Param("name") String name);
}
