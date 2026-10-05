package com.crm.repository;

import com.crm.entity.UserCharaLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserCharaLinkRepository extends JpaRepository<UserCharaLink, Long> {
    List<UserCharaLink> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserCharaLink> findByUserIdAndCharaId(Long userId, Long charaId);

    @Modifying
    @Query("DELETE FROM UserCharaLink l WHERE l.userId = :userId AND l.charaId = :charaId")
    int deleteLink(@Param("userId") Long userId, @Param("charaId") Long charaId);
}
