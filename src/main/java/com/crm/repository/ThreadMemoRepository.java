package com.crm.repository;

import com.crm.entity.ThreadMemo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThreadMemoRepository extends JpaRepository<ThreadMemo, Long> {
    Optional<ThreadMemo> findByUserIdAndTargetAndStaffId(Long userId, String target, Long staffId);
}
