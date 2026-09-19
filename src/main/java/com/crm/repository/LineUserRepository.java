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
}
