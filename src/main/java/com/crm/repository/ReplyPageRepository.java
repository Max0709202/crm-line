package com.crm.repository;

import com.crm.entity.ReplyPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReplyPageRepository extends JpaRepository<ReplyPage, Long> {
    Optional<ReplyPage> findByToken(String token);
    boolean existsByToken(String token);
    /** The user's newest reply page (管理画面の「表示画面を確認」). */
    Optional<ReplyPage> findFirstByUserIdOrderByIdDesc(Long userId);
}
