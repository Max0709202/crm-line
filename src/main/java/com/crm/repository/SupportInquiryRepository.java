package com.crm.repository;

import com.crm.entity.SupportInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {
    List<SupportInquiry> findAllByOrderByReceivedAtDesc();
    boolean existsByMessageIdHeader(String messageIdHeader);
}
