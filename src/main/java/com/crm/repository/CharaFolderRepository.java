package com.crm.repository;

import com.crm.entity.CharaFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharaFolderRepository extends JpaRepository<CharaFolder, Long> {
    List<CharaFolder> findAllByOrderByIdAsc();
}
