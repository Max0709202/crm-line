package com.crm.repository;

import com.crm.entity.DiffStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiffStepRepository extends JpaRepository<DiffStep, Long> {

    /** Every 登録後(分後) step across all definitions — applied to each newly registered user. */
    List<DiffStep> findByOffsetModeOrderByDiffDefinitionIdAscStepOrderAsc(String offsetMode);
    List<DiffStep> findByDiffDefinitionIdOrderByStepOrderAsc(Long diffDefinitionId);
    long countByDiffDefinitionId(Long diffDefinitionId);
    void deleteByDiffDefinitionId(Long diffDefinitionId);
}
