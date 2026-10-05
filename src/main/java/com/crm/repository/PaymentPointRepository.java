package com.crm.repository;

import com.crm.entity.PaymentPoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentPointRepository extends JpaRepository<PaymentPoint, Long> {
}
