package com.zynetic.ev.chargemanager.repository;

import com.zynetic.ev.chargemanager.entity.Charger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChargerRepository extends JpaRepository<Charger, String> {
}
