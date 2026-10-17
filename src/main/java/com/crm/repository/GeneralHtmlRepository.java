package com.crm.repository;

import com.crm.entity.GeneralHtml;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneralHtmlRepository extends JpaRepository<GeneralHtml, Integer> {

    Optional<GeneralHtml> findByUrlKey(String urlKey);

    List<GeneralHtml> findAllByOrderBySlotNoAsc();
}
