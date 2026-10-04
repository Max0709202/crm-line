package com.crm.repository;

import com.crm.entity.Chara;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CharaRepository extends JpaRepository<Chara, Long> {
    List<Chara> findAllByOrderByIdAsc();

    @Modifying
    @Query("UPDATE Chara c SET c.folderId = NULL WHERE c.folderId = :folderId")
    int clearFolder(@Param("folderId") Long folderId);
}
