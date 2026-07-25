package com.zynetic.ev.chargemanager.controller;

import com.zynetic.ev.chargemanager.entity.ChargingTransaction;
import com.zynetic.ev.chargemanager.service.OcppService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
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

    // Transaction history for a charger, optionally filtered by [from, to].
    // Example: /api/chargers/CHARGER_001/transactions?from=2026-07-01T00:00:00&to=2026-07-25T23:59:59
    @GetMapping("/{chargePointId}/transactions")
    public ResponseEntity<List<ChargingTransaction>> getTransactionHistory(
            @PathVariable String chargePointId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ocppService.getTransactionHistory(chargePointId, from, to));
    }
}