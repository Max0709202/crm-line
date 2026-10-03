package com.crm.repository;

import com.crm.entity.SupportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTemplateRepository extends JpaRepository<SupportTemplate, Long> {
    List<SupportTemplate> findAllByOrderBySortOrderAscIdAsc();
}
