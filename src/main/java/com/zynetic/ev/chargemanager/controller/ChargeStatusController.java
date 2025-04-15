package com.zynetic.ev.chargemanager.controller;

import com.zynetic.ev.chargemanager.service.OcppService;
import com.zynetic.ev.chargemanager.webconfig.OcppWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chargers")
public class ChargeStatusController {

    private final OcppService ocppService;

    public ChargeStatusController(OcppService ocppService) {
        this.ocppService = ocppService;
    }

    // Get status of all chargers
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getAllChargerStatuses() {
        return ResponseEntity.ok(ocppService.getAllChargePointStatuses());
    }

    // Get status of a specific charger
    @GetMapping("/status/{chargePointId}")
    public ResponseEntity<String> getChargerStatus(@PathVariable String chargePointId) {
        String status = ocppService.getChargePointStatus(chargePointId);
        return (status != null) ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }
}
