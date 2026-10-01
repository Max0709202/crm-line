package com.crm.repository;

import com.crm.entity.UserAccessLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface UserAccessLogRepository extends JpaRepository<UserAccessLog, Long> {

    List<UserAccessLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByDomainHost(String domainHost);

    List<UserAccessLog> findByDomainHostOrderByCreatedAtDesc(String domainHost, Pageable pageable);

    @Query("select distinct l.userId from UserAccessLog l where l.domainHost = :domainHost")
    List<Long> findDistinctUserIdsByDomainHost(@Param("domainHost") String domainHost);

    /** 正規のアクセス数: distinct users, not raw hit count — a user who clicks twice still
     *  counts once, matching what the アクセスユーザー drilldown lists. */
    @Query("select count(distinct l.userId) from UserAccessLog l where l.domainHost = :domainHost")
    long countDistinctUserIdsByDomainHost(@Param("domainHost") String domainHost);

    /** 入金レポート ログイン数: distinct users per day, as [yyyy-MM-dd, count] rows. */
    @Query(value = "SELECT DATE_FORMAT(CREATED_AT, '%Y-%m-%d') AS d, COUNT(DISTINCT USER_ID) " +
                   "FROM USER_ACCESS_LOG GROUP BY d", nativeQuery = true)
    List<Object[]> countDistinctUsersByDay();

    /** 入金レポート ログイン数: distinct users per month, as [yyyy-MM, count] rows. */
    @Query(value = "SELECT DATE_FORMAT(CREATED_AT, '%Y-%m') AS m, COUNT(DISTINCT USER_ID) " +
                   "FROM USER_ACCESS_LOG GROUP BY m", nativeQuery = true)
    List<Object[]> countDistinctUsersByMonth();

    @Query("select count(distinct l.userId) from UserAccessLog l")
    long countDistinctUsers();

    @Modifying
    @Transactional
    @Query("delete from UserAccessLog l where l.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query("delete from UserAccessLog l where l.domainHost = :domainHost")
    int deleteByDomainHost(@Param("domainHost") String domainHost);
}
