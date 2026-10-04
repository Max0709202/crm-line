package com.crm.repository;

import com.crm.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {
    List<AdminRole> findAllByOrderBySortOrderAscIdAsc();
    Optional<AdminRole> findByAdminUserId(Long adminUserId);
    Optional<AdminRole> findByLoginIdIgnoreCase(String loginId);
}
