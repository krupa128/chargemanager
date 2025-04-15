package com.zynetic.ev.chargemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zynetic.ev.chargemanager.entity.Charger;
import com.zynetic.ev.chargemanager.entity.ChargerStatus;
import com.zynetic.ev.chargemanager.entity.User;
import com.zynetic.ev.chargemanager.repository.ChargerRepository;
import com.zynetic.ev.chargemanager.repository.UserRepository;
import com.zynetic.ev.chargemanager.utility.WebSocketUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OcppService {

    private final ChargerRepository chargerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final ConcurrentHashMap<String, WebSocketSession> chargePointSessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> chargePointStatuses = new ConcurrentHashMap<>();

    public OcppService(ChargerRepository chargerRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.chargerRepository = chargerRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Handles incoming WebSocket messages from charge points.
     */
    public void processMessage(WebSocketSession session, String chargePointId, String messageType, JsonNode jsonNode) throws Exception {
        switch (messageType) {
            case "Authorize":
                handleAuthorize(session, jsonNode);
                break;
            case "StatusNotification":
                handleStatusNotification(session, jsonNode);
                break;
            default:
                System.out.println("Unsupported message type: " + messageType);
        }
    }

    /**
     * Handles status notifications from charge points.
     */
    private void handleStatusNotification(WebSocketSession session, JsonNode jsonNode) throws Exception {
        String chargePointId = WebSocketUtil.extractChargePointId(session);
        int connectorId = jsonNode.get(3).get("connectorId").asInt();
        String status = jsonNode.get(3).get("status").asText();

        chargePointStatuses.put(chargePointId, status);
        updateChargerStatus(chargePointId, ChargerStatus.valueOf(status));

        System.out.println("Charge Point " + chargePointId + " (Connector " + connectorId + ") is now " + status);

        String response = "[3, \"" + jsonNode.get(1).asText() + "\", {}]";
        session.sendMessage(new TextMessage(response));
    }

    /**
     * Handles authentication of charge points.
     */
    private void handleAuthorize(WebSocketSession session, JsonNode jsonNode) throws Exception {
        String username = jsonNode.get(3).get("username").asText();
        String password = jsonNode.get(3).get("password").asText();
        System.out.println("Testing 2.........");
        Optional<User> user = userRepository.findByUsername(username);
        boolean isValid = user.isPresent() && passwordEncoder.matches(password, user.get().getPassword());

        String response = isValid
                ? "[3, \"" + jsonNode.get(1).asText() + "\", {\"username\": {\"status\": \"Accepted\"}}]"
                : "[3, \"" + jsonNode.get(1).asText() + "\", {\"username\": {\"status\": \"Invalid\"}}]";

        session.sendMessage(new TextMessage(response));

        if (isValid) {
            sendStatusNotification(session, username);
        }
    }

    /**
     * Sends a status notification to a charge point.
     */
    private void sendStatusNotification(WebSocketSession session, String chargePointId) throws Exception {
        String statusNotification = "[2, \"67890\", \"StatusNotification\", {"
                + "\"connectorId\": 1, "
                + "\"errorCode\": \"NoError\", "
                + "\"status\": \"Available\", "
                + "\"timestamp\": \"" + Instant.now().toString() + "\""
                + "}]";

        chargePointStatuses.put(chargePointId, "Available");
        session.sendMessage(new TextMessage(statusNotification));

        updateChargerStatus(chargePointId, ChargerStatus.AVAILABLE);
    }
    private void validateCharger(Charger charger) {
        if (charger.getModel() == null || charger.getModel().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }
        // Add other field validations if necessary
    }

    /**
     * Retrieves the current statuses of all charge points.
     */
    public Map<String, String> getAllChargePointStatuses() {
        Map<String, String> statuses = new HashMap<>();
        List<Charger> chargers = chargerRepository.findAll();
        for (Charger charger : chargers) {
            statuses.put(charger.getId(), charger.getStatus().name());
        }
        return statuses;
    }

    /**
     * Retrieves a charge point's status.
     */
    public String getChargePointStatus(String chargePointId) {
        return chargePointStatuses.getOrDefault(chargePointId, "Unknown");
    }

    /**
     * Updates a charge point's status.
     */
    public void updateChargerStatus(String chargePointId, ChargerStatus status) {
        System.out.println("Testing 3.....");
        Charger charger = chargerRepository.findById(chargePointId)
                .orElseGet(() -> {
                    Charger newCharger = new Charger();
                    newCharger.setId(chargePointId);

                    // Ensure all required fields are properly initialized
                    newCharger.setVendor("Unknown"); // Default value for vendor
                    newCharger.setModel("JIO"); // Default value for model
                    newCharger.setStatus(ChargerStatus.UNAVAILABLE);
                    newCharger.setLastHeartbeat(LocalDateTime.now());
                    newCharger.setCreatedAt(LocalDateTime.now());
                    validateCharger(newCharger);
                    System.out.println("Testing 4.....");
                    return chargerRepository.save(newCharger);
                });

        // Update the existing charger's status
        charger.setStatus(status);
        charger.setLastHeartbeat(LocalDateTime.now());
        System.out.println("Testing 5.....");
        validateCharger(charger);
        System.out.println("Testing 6.....");
        chargerRepository.save(charger);
    }

    /**
     * Removes a charge point when disconnected.
     */
    public void removeChargePoint(String chargePointId) {
        chargePointStatuses.remove(chargePointId);
        chargePointSessions.remove(chargePointId);
    }

}
