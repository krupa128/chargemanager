package com.zynetic.ev.chargemanager.repository;

import com.zynetic.ev.chargemanager.entity.ChargingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChargingTransactionRepository extends JpaRepository<ChargingTransaction, Long> {
}
