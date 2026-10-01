package com.crm.repository;

import com.crm.entity.DailyLoginCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface DailyLoginCountRepository extends JpaRepository<DailyLoginCount, LocalDate> {
}
