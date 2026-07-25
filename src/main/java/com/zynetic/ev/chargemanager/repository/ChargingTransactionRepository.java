package com.zynetic.ev.chargemanager.repository;

import com.zynetic.ev.chargemanager.entity.ChargingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingTransactionRepository extends JpaRepository<ChargingTransaction, Long> {

    // Used to look up the transaction a StopTransaction message refers to
    Optional<ChargingTransaction> findByTransactionId(int transactionId);

    // Powers GET /api/chargers/{chargerId}/transactions, optionally filtered by time range
    List<ChargingTransaction> findByCharger_IdAndStartTimeBetween(String chargerId, LocalDateTime from, LocalDateTime to);

    List<ChargingTransaction> findByCharger_Id(String chargerId);
}