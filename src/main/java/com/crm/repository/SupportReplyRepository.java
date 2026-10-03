package com.crm.repository;

import com.crm.entity.SupportReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SupportReplyRepository extends JpaRepository<SupportReply, Long> {
    List<SupportReply> findByInquiryIdInOrderByCreatedAtAsc(Collection<Long> inquiryIds);
    void deleteByInquiryIdIn(Collection<Long> inquiryIds);
}
