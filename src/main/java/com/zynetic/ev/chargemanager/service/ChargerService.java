//package com.zynetic.ev.chargemanager.service;
//
//import com.zynetic.ev.chargemanager.entity.Charger;
//import com.zynetic.ev.chargemanager.entity.ChargerStatus;
//import com.zynetic.ev.chargemanager.repository.ChargerRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@Service
//public class ChargerService {
//
//    @Autowired
//    private ChargerRepository chargerRepository;
//
//    public Charger getOrCreateCharger(String chargePointId) {
//        return chargerRepository.findById(chargePointId)
//                .orElseGet(() -> {
//                    Charger newCharger = new Charger();
//                    newCharger.setId(chargePointId);
//                    newCharger.setStatus(ChargerStatus.UNAVAILABLE);
//                    newCharger.setLastHeartbeat(LocalDateTime.now());
//                    newCharger.setVendor("Unknown");
//                    newCharger.setModel("Unknown");
//                    return chargerRepository.save(newCharger);
//                });
//    }
//
//    public void updateChargerStatus(String chargePointId, ChargerStatus status) {
//        Charger charger = getOrCreateCharger(chargePointId);
//        charger.setStatus(status);
//        charger.setLastHeartbeat(LocalDateTime.now());
//        chargerRepository.save(charger);
//    }
//
//    public Map<String, String> getAllChargePointStatuses() {
//        Map<String, String> statuses = new HashMap<>();
//        List<Charger> chargers = chargerRepository.findAll();
//        for (Charger charger : chargers) {
//            statuses.put(charger.getId(), charger.getStatus().name());
//        }
//        return statuses;
//    }
//}
//
