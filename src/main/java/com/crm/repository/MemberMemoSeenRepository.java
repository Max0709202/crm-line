package com.crm.repository;

import com.crm.entity.MemberMemoSeen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface MemberMemoSeenRepository extends JpaRepository<MemberMemoSeen, Long> {

    /** 専用HTMLを切り替えた: お知らせ gets NEW again until the member views it — even when the new slot's
     *  HTML is one they already saw (a NEW from changed HTML is MemberNoticeService's hash check). */
    default void markSwitched(Collection<Long> userIds) {
        if (userIds != null && !userIds.isEmpty()) deleteAllByIdInBatch(userIds);
    }
}
