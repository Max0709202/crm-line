package com.crm.repository;

import com.crm.entity.LineUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LineUserRepository extends JpaRepository<LineUser, Long> {

    Optional<LineUser> findByLineAccountIdAndLineUserId(Long lineAccountId, String lineUserId);

    List<LineUser> findByCrmUserIdIsNullOrderByLastMessageAtDesc();

    List<LineUser> findByCrmUserId(Long crmUserId);
}
