package com.crm.repository;

import com.crm.entity.CharaRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CharaRefRepository extends JpaRepository<CharaRef, Long> {
    Optional<CharaRef> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);

    List<CharaRef> findByOwnerTypeAndOwnerIdIn(String ownerType, Collection<Long> ownerIds);

    @Modifying
    @Query("DELETE FROM CharaRef r WHERE r.ownerType = :ownerType AND r.ownerId IN :ownerIds")
    int deleteOwners(@Param("ownerType") String ownerType, @Param("ownerIds") Collection<Long> ownerIds);

    /** キャラ of the user's newest outbound message that was sent as one (own ref, else its broadcast's). */
    @Query(nativeQuery = true, value =
            "SELECT COALESCE(rm.CHARA_ID, rb.CHARA_ID) FROM MESSAGE m"
            + " LEFT JOIN CHARA_REF rm ON rm.OWNER_TYPE = 'MESSAGE' AND rm.OWNER_ID = m.ID"
            + " LEFT JOIN CHARA_REF rb ON rb.OWNER_TYPE = 'BROADCAST' AND rb.OWNER_ID = m.BROADCAST_ID"
            + " WHERE m.USER_ID = :userId AND m.DIRECTION = 'OUT'"
            + " AND (rm.CHARA_ID IS NOT NULL OR rb.CHARA_ID IS NOT NULL)"
            + " ORDER BY m.ID DESC LIMIT 1")
    List<Number> latestOutboundCharaId(@Param("userId") Long userId);
}
