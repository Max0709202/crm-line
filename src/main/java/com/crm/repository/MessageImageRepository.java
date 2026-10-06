package com.crm.repository;

import com.crm.entity.MessageImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageImageRepository extends JpaRepository<MessageImage, Long> {

    boolean existsByImageId(Long imageId);

    List<MessageImage> findByOwnerTypeAndOwnerIdInOrderBySortNoAscIdAsc(String ownerType, Collection<Long> ownerIds);

    /** Was image {@code imageId} sent to user {@code userId} (on one of their messages, or the broadcast of one)? */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) FROM MESSAGE m JOIN MESSAGE_IMAGE mi"
            + " ON (mi.OWNER_TYPE = 'MESSAGE' AND mi.OWNER_ID = m.ID)"
            + " OR (mi.OWNER_TYPE = 'BROADCAST' AND mi.OWNER_ID = m.BROADCAST_ID)"
            + " WHERE m.USER_ID = :userId AND m.DIRECTION = 'OUT' AND mi.IMAGE_ID = :imageId")
    long countSentToUser(@Param("userId") Long userId, @Param("imageId") Long imageId);
}
