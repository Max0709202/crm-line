package com.crm.repository;

import com.crm.entity.SupportInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {
    List<SupportInquiry> findAllByOrderByReceivedAtDesc();
    boolean existsByMessageIdHeader(String messageIdHeader);
    /** 会員ページ サポート窓口 のやり取り: one member's inquiries of one channel, oldest first. */
    List<SupportInquiry> findByMemberIdAndChannelOrderByReceivedAtAsc(Long memberId, String channel);
}
