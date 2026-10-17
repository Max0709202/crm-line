package com.crm.repository;

import com.crm.entity.CrmUserMemoSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CrmUserMemoSlotRepository extends JpaRepository<CrmUserMemoSlot, Long> {

    List<CrmUserMemoSlot> findByUserId(Long userId);

    Optional<CrmUserMemoSlot> findByUserIdAndSlotNo(Long userId, Integer slotNo);

    @Modifying
    @Query("delete from CrmUserMemoSlot s where s.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from CrmUserMemoSlot s where s.userId in :userIds")
    int deleteByUserIdIn(@Param("userIds") Collection<Long> userIds);
}
