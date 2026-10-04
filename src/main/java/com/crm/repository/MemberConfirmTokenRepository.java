package com.crm.repository;

import com.crm.entity.MemberConfirmToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberConfirmTokenRepository extends JpaRepository<MemberConfirmToken, String> {
}
