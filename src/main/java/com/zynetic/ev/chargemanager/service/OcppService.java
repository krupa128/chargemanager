package com.zynetic.ev.chargemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zynetic.ev.chargemanager.entity.Charger;
import com.zynetic.ev.chargemanager.entity.ChargerStatus;
import com.zynetic.ev.chargemanager.entity.ChargingTransaction;
import com.zynetic.ev.chargemanager.entity.TransactionStatus;
import com.zynetic.ev.chargemanager.entity.User;
import com.zynetic.ev.chargemanager.repository.ChargerRepository;
import com.zynetic.ev.chargemanager.repository.ChargingTransactionRepository;
import com.zynetic.ev.chargemanager.repository.UserRepository;
import com.zynetic.ev.chargemanager.utility.WebSocketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central handler for all OCPP 1.6 messages received from charge points.
 * Each branch in processMessage corresponds to one OCPP 1.6 "Call" action
 * (see OCPP 1.6 spec section 4: BootNotification, Heartbeat, StatusNotification,
 * Authorize, StartTransaction, StopTransaction).
 *
 * Also runs a scheduled job that marks chargers Unavailable once their
 * heartbeat has gone stale, satisfying the "auto-mark unavailable after
 * 5+ minutes of silence" requirement.
 */
@Service
public class OcppService {

    private static final Logger log = LoggerFactory.getLogger(OcppService.class);

    // A charger is considered stale (and marked UNAVAILABLE) if we haven't heard
    // a Heartbeat/BootNotification/StatusNotification from it in this long.
    private static final long HEARTBEAT_TIMEOUT_MINUTES = 5;

    private final ChargerRepository chargerRepository;
    private final UserRepository userRepository;
    private final ChargingTransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    // In-memory cache of last-known status per charge point, kept in sync with the DB.
    // Serves fast reads (getChargePointStatus) without a DB round trip.
    private static final ConcurrentHashMap<String, WebSocketSession> chargePointSessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> chargePointStatuses = new ConcurrentHashMap<>();

    // Generates OCPP transactionId values for StartTransaction responses.
    // In-memory only (resets on restart) â fine for this assignment's scope;
    // a production system would back this with a DB sequence.
    private static final AtomicInteger transactionIdGenerator = new AtomicInteger(1);

    public OcppService(ChargerRepository chargerRepository,
                        UserRepository userRepository,
                        ChargingTransactionRepository transactionRepository,
                        PasswordEncoder passwordEncoder) {
        this.chargerRepository = chargerRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Handles incoming WebSocket messages from charge points.
     */
    public void processMessage(WebSocketSession session, String chargePointId, String messageType, JsonNode jsonNode) throws Exception {
        switch (messageType) {
            case "BootNotification":
                handleBootNotification(session, chargePointId, jsonNode);
                break;
            case "Heartbeat":
                handleHeartbeat(session, chargePointId, jsonNode);
                break;
            case "StatusNotification":
                handleStatusNotification(session, jsonNode);
                break;
            case "Authorize":
                handleAuthorize(session, jsonNode);
                break;
            case "StartTransaction":
                handleStartTransaction(session, chargePointId, jsonNode);
                break;
            case "StopTransaction":
                handleStopTransaction(session, jsonNode);
                break;
            default:
                log.warn("Unsupported OCPP message type: {}", messageType);
        }
    }

    /**
     * Handles BootNotification â sent once when a charge point first connects
     * (or reconnects) to register itself and its hardware details. We upsert
     * the Charger record here rather than lazily on first status update, so
     * vendor/model/firmware are captured from the source of truth.
     */
    private void handleBootNotification(WebSocketSession session, String chargePointId, JsonNode jsonNode) throws Exception {
        JsonNode payload = jsonNode.get(3);
        String vendor = payload.hasNonNull("chargePointVendor") ? payload.get("chargePointVendor").asText() : "Unknown";
        String model = payload.hasNonNull("chargePointModel") ? payload.get("chargePointModel").asText() : "Unknown";
        String firmwareVersion = payload.hasNonNull("firmwareVersion") ? payload.get("firmwareVersion").asText() : null;

        Charger charger = chargerRepository.findById(chargePointId).orElseGet(Charger::new);
        charger.setId(chargePointId);
        charger.setVendor(vendor);
        charger.setModel(model);
        charger.setFirmwareVersion(firmwareVersion);
        charger.setStatus(ChargerStatus.AVAILABLE);
        charger.setLastHeartbeat(LocalDateTime.now());
        if (charger.getCreatedAt() == null) {
            charger.setCreatedAt(LocalDateTime.now());
        }
        chargerRepository.save(charger);
        chargePointStatuses.put(chargePointId, ChargerStatus.AVAILABLE.name());

        log.info("BootNotification from {} (vendor={}, model={})", chargePointId, vendor, model);

        String response = "[3, \"" + jsonNode.get(1).asText() + "\", {"
                + "\"status\": \"Accepted\", "
                + "\"currentTime\": \"" + Instant.now() + "\", "
                + "\"interval\": 300"
                + "}]";
        session.sendMessage(new TextMessage(response));
    }

    /**
     * Handles Heartbeat â the charger's periodic "I'm still alive" ping.
     * Refreshes lastHeartbeat so the stale-charger sweep doesn't mark it Unavailable.
     */
    private void handleHeartbeat(WebSocketSession session, String chargePointId, JsonNode jsonNode) throws Exception {
        chargerRepository.findById(chargePointId).ifPresent(charger -> {
            charger.setLastHeartbeat(LocalDateTime.now());
            // A charger that was auto-marked Unavailable due to a missed heartbeat
            // window recovers to Available once heartbeats resume.
            if (charger.getStatus() == ChargerStatus.UNAVAILABLE) {
                charger.setStatus(ChargerStatus.AVAILABLE);
                chargePointStatuses.put(chargePointId, ChargerStatus.AVAILABLE.name());
            }
            chargerRepository.save(charger);
        });

        String response = "[3, \"" + jsonNode.get(1).asText() + "\", {"
                + "\"currentTime\": \"" + Instant.now() + "\""
                + "}]";
        session.sendMessage(new TextMessage(response));
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

        log.info("Charge Point {} (Connector {}) is now {}", chargePointId, connectorId, status);

        String response = "[3, \"" + jsonNode.get(1).asText() + "\", {}]";
        session.sendMessage(new TextMessage(response));
    }

    /**
     * Handles authentication of charge points.
     * Note: idTag here is the OCPP-level identifier used to authorize charging
     * sessions â a separate concern from the JWT auth used on the REST API.
     */
    private void handleAuthorize(WebSocketSession session, JsonNode jsonNode) throws Exception {
        String username = jsonNode.get(3).get("username").asText();
        String password = jsonNode.get(3).get("password").asText();

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
                + "\"timestamp\": \"" + Instant.now() + "\""
                + "}]";

        chargePointStatuses.put(chargePointId, "Available");
        session.sendMessage(new TextMessage(statusNotification));

        updateChargerStatus(chargePointId, ChargerStatus.AVAILABLE);
    }

    /**
     * Handles StartTransaction â sent when a driver begins charging. Persists a
     * new ChargingTransaction and flips the charger to CHARGING. idTag is looked
     * up against registered Users on a best-effort basis; an unrecognized idTag
     * (e.g. an RFID card not tied to a dashboard account) still starts the
     * transaction with a null user rather than being rejected, since the OCPP
     * spec's authorization step (Authorize) is a separate concern from this one.
     */
    private void handleStartTransaction(WebSocketSession session, String chargePointId, JsonNode jsonNode) throws Exception {
        JsonNode payload = jsonNode.get(3);
        int connectorId = payload.get("connectorId").asInt();
        String idTag = payload.get("idTag").asText();
        int meterStart = payload.get("meterStart").asInt();

        Charger charger = chargerRepository.findById(chargePointId)
                .orElseThrow(() -> new IllegalStateException("StartTransaction from unregistered charger: " + chargePointId));

        ChargingTransaction transaction = new ChargingTransaction();
        transaction.setTransactionId(transactionIdGenerator.getAndIncrement());
        transaction.setCharger(charger);
        transaction.setUser(userRepository.findByUsername(idTag).orElse(null));
        transaction.setIdTag(idTag);
        transaction.setStartTime(LocalDateTime.now());
        transaction.setMeterStart(meterStart);
        transaction.setStatus(TransactionStatus.ACTIVE);
        transactionRepository.save(transaction);

        charger.setStatus(ChargerStatus.CHARGING);
        charger.setLastHeartbeat(LocalDateTime.now());
        chargerRepository.save(charger);
        chargePointStatuses.put(chargePointId, ChargerStatus.CHARGING.name());

        log.info("StartTransaction: charger={} connector={} idTag={} transactionId={}",
                chargePointId, connectorId, idTag, transaction.getTransactionId());

        String response = "[3, \"" + jsonNode.get(1).asText() + "\", {"
                + "\"transactionId\": " + transaction.getTransactionId() + ", "
                + "\"idTagInfo\": {\"status\": \"Accepted\"}"
                + "}]";
        session.sendMessage(new TextMessage(response));
    }

    /**
     * Handles StopTransaction â sent when a charging session ends. Closes out
     * the matching ChargingTransaction and returns the charger to AVAILABLE.
     */
    private void handleStopTransaction(WebSocketSession session, JsonNode jsonNode) throws Exception {
        JsonNode payload = jsonNode.get(3);
        int transactionId = payload.get("transactionId").asInt();
        int meterStop = payload.get("meterStop").asInt();
        String reason = payload.hasNonNull("reason") ? payload.get("reason").asText() : null;

        Optional<ChargingTransaction> maybeTransaction = transactionRepository.findByTransactionId(transactionId);
        if (maybeTransaction.isEmpty()) {
            log.warn("StopTransaction for unknown transactionId: {}", transactionId);
            session.sendMessage(new TextMessage("[3, \"" + jsonNode.get(1).asText()
                    + "\", {\"idTagInfo\": {\"status\": \"Invalid\"}}]"));
            return;
        }

        ChargingTransaction transaction = maybeTransaction.get();
        transaction.setEndTime(LocalDateTime.now());
        transaction.setMeterEnd(meterStop);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setStopReason(reason);
        transactionRepository.save(transaction);

        String chargePointId = transaction.getCharger().getId();
        updateChargerStatus(chargePointId, ChargerStatus.AVAILABLE);

        log.info("StopTransaction: transactionId={} charger={} meterStop={}", transactionId, chargePointId, meterStop);

        String response = "[3, \"" + jsonNode.get(1).asText() + "\", {"
                + "\"idTagInfo\": {\"status\": \"Accepted\"}"
                + "}]";
        session.sendMessage(new TextMessage(response));
    }

    /**
     * Scheduled sweep that enforces the "no heartbeat for 5+ minutes -> Unavailable"
     * requirement. Runs every minute; only touches chargers not already Unavailable
     * to avoid redundant writes.
     */
    @Scheduled(fixedRate = 60_000)
    public void markStaleChargersUnavailable() {
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(HEARTBEAT_TIMEOUT_MINUTES);
        List<Charger> chargers = chargerRepository.findAll();

        for (Charger charger : chargers) {
            boolean isStale = charger.getLastHeartbeat() != null && charger.getLastHeartbeat().isBefore(staleThreshold);
            if (isStale && charger.getStatus() != ChargerStatus.UNAVAILABLE) {
                charger.setStatus(ChargerStatus.UNAVAILABLE);
                chargerRepository.save(charger);
                chargePointStatuses.put(charger.getId(), ChargerStatus.UNAVAILABLE.name());
                log.info("Charger {} marked UNAVAILABLE â no heartbeat since {}", charger.getId(), charger.getLastHeartbeat());
            }
        }
    }

    private void validateCharger(Charger charger) {
        if (charger.getModel() == null || charger.getModel().isEmpty()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }
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
     * Retrieves transaction history for a charger, optionally filtered by time range.
     */
    public List<ChargingTransaction> getTransactionHistory(String chargePointId, LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) {
            return transactionRepository.findByCharger_IdAndStartTimeBetween(chargePointId, from, to);
        }
        return transactionRepository.findByCharger_Id(chargePointId);
    }

    /**
     * Updates a charge point's status. Creates the Charger record on first
     * contact if BootNotification hasn't been received yet (defensive â a
     * well-behaved charger always sends BootNotification first).
     */
    public void updateChargerStatus(String chargePointId, ChargerStatus status) {
        Charger charger = chargerRepository.findById(chargePointId)
                .orElseGet(() -> {
                    Charger newCharger = new Charger();
                    newCharger.setId(chargePointId);
                    newCharger.setVendor("Unknown");
                    newCharger.setModel("Unknown");
                    newCharger.setStatus(ChargerStatus.UNAVAILABLE);
                    newCharger.setLastHeartbeat(LocalDateTime.now());
                    newCharger.setCreatedAt(LocalDateTime.now());
                    validateCharger(newCharger);
                    return chargerRepository.save(newCharger);
                });

        charger.setStatus(status);
        charger.setLastHeartbeat(LocalDateTime.now());
        validateCharger(charger);
        chargerRepository.save(charger);
        chargePointStatuses.put(chargePointId, status.name());
    }

    /**
     * Removes a charge point when disconnected.
     */
    public void removeChargePoint(String chargePointId) {
        chargePointStatuses.remove(chargePointId);
        chargePointSessions.remove(chargePointId);
    }

}