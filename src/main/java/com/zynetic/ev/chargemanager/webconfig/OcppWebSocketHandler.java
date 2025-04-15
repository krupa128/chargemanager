package com.zynetic.ev.chargemanager.webconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zynetic.ev.chargemanager.entity.User;
import com.zynetic.ev.chargemanager.repository.UserRepository;
import com.zynetic.ev.chargemanager.service.OcppService;
import com.zynetic.ev.chargemanager.utility.WebSocketUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

//@Component
//public class OcppWebSocketHandler extends TextWebSocketHandler {
//
//    private static final ConcurrentHashMap<String, WebSocketSession> chargePointSessions = new ConcurrentHashMap<>();
//    private static final ConcurrentHashMap<String, String> chargePointStatuses = new ConcurrentHashMap<>();
//    private final UserRepository userRepository;
//    private final ObjectMapper objectMapper;
//    private final PasswordEncoder passwordEncoder;
//
//    public OcppWebSocketHandler(UserRepository userRepository, ObjectMapper objectMapper, PasswordEncoder passwordEncoder) {
//        this.userRepository = userRepository;
//        this.objectMapper = objectMapper;
//        this.passwordEncoder = passwordEncoder;
//    }
//
//
//    @Override
//    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        String chargePointId = extractChargePointId(session);
//        String protocol = getWebSocketProtocol(session);
//
//        if (!"ocpp1.6".equals(protocol)) {
//            System.out.println("Invalid WebSocket Protocol: " + protocol);
//            session.close(CloseStatus.NOT_ACCEPTABLE);
//            return;
//        }
//
//        chargePointSessions.put(chargePointId, session);
//        System.out.println("Charge Point Connected: " + chargePointId);
//    }
//
//    @Override
//    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        String chargePointId = extractChargePointId(session);
//        String payload = message.getPayload();
//        System.out.println("Message from " + chargePointId + ": " + payload);
//
//        JsonNode jsonNode = objectMapper.readTree(payload);
//
//        if (jsonNode.isArray() && jsonNode.size() > 2) {
//            String messageType = jsonNode.get(2).asText();
//
//            switch (messageType) {
//                case "Authorize":
//                    handleAuthorize(session, jsonNode);
//                    break;
//                case "StatusNotification":
//                    handleStatusNotification(session, jsonNode);
//                    break;
//                default:
//                    System.out.println("Unsupported message type: " + messageType);
//            }
//        }
//    }
//    public ConcurrentHashMap<String, String> getChargePointStatuses() {
//        return chargePointStatuses;
//    }
//    public void updateChargerStatus(String chargePointId, String status) {
//        chargePointStatuses.put(chargePointId, status);
//    }
//    private void handleStatusNotification(WebSocketSession session, JsonNode jsonNode) throws Exception {
//        String chargePointId = extractChargePointId(session);
//        int connectorId = jsonNode.get(3).get("connectorId").asInt();
//        String status = jsonNode.get(3).get("status").asText();
//        String errorCode = jsonNode.get(3).get("errorCode").asText();
//        String timestamp = jsonNode.get(3).get("timestamp").asText();
//
//        // Store the status in the map
//        chargePointStatuses.put(chargePointId, status);
//
//        System.out.println("Charge Point " + chargePointId + " (Connector " + connectorId + ") is now " + status + " (Error: " + errorCode + ") at " + timestamp);
//
//        // Respond with an acknowledgment
//        String response = "[3, \"" + jsonNode.get(1).asText() + "\", {}]";
//        session.sendMessage(new TextMessage(response));
//    }
//
//
//    private void handleAuthorize(WebSocketSession session, JsonNode jsonNode) throws Exception {
//        if (jsonNode.size() < 4 || !jsonNode.get(3).has("username") || !jsonNode.get(3).has("password")) {
//            System.out.println("Invalid Authorize payload received: " + jsonNode);
//            session.sendMessage(new TextMessage("[3, \"" + jsonNode.get(1).asText() + "\", {\"username\": {\"status\": \"InvalidFormat\"}}]"));
//            return;
//        }
//
//        String idTag = jsonNode.get(3).get("username").asText();
//        String password = jsonNode.get(3).get("password").asText();
//
//        Optional<User> user = userRepository.findByUsername(idTag);
//        boolean isValid = user.isPresent() && passwordEncoder.matches(password, user.get().getPassword());
//
//        String response = isValid
//                ? "[3, \"" + jsonNode.get(1).asText() + "\", {\"username\": {\"status\": \"Accepted\"}}]"
//                : "[3, \"" + jsonNode.get(1).asText() + "\", {\"username\": {\"status\": \"Invalid\"}}]";
//
//        session.sendMessage(new TextMessage(response));
//
//        if (isValid) {
//            // Send a StatusNotification message after successful authentication
//            sendStatusNotification(session, idTag);
//        }
//    }
//
//    /**
//     * Sends a StatusNotification message after a successful authorization.
//     */
//    private void sendStatusNotification(WebSocketSession session, String chargePointId) throws Exception {
//        String statusNotification = "[2, \"67890\", \"StatusNotification\", {"
//                + "\"connectorId\": 1, "
//                + "\"errorCode\": \"NoError\", "
//                + "\"status\": \"Available\", "
//                + "\"timestamp\": \"" + Instant.now().toString() + "\""
//                + "}]";
//
//        chargePointStatuses.put(chargePointId, "Available");
//        session.sendMessage(new TextMessage(statusNotification));
//
//        System.out.println("Sent StatusNotification for Charge Point: " + chargePointId);
//    }
//
//
//    @Override
//    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        String chargePointId = extractChargePointId(session);
//        chargePointSessions.remove(chargePointId);
//        chargePointStatuses.remove(chargePointId);
//        System.out.println("Charge Point Disconnected: " + chargePointId);
//    }
//
//    private String extractChargePointId(WebSocketSession session) {
//        String path = session.getUri().getPath();
//        return (path != null && path.contains("/")) ? path.substring(path.lastIndexOf('/') + 1) : null;
//    }
//
//    private String getWebSocketProtocol(WebSocketSession session) {
//        HttpHeaders headers = session.getHandshakeHeaders();
//        return headers.getFirst("Sec-WebSocket-Protocol");
//    }
//}
@Component
public class OcppWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> chargePointSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final OcppService ocppService;

    public OcppWebSocketHandler(ObjectMapper objectMapper, OcppService ocppService) {
        this.objectMapper = objectMapper;
        this.ocppService = ocppService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String chargePointId = WebSocketUtil.extractChargePointId(session);
        String protocol = WebSocketUtil.getWebSocketProtocol(session);

        if (!"ocpp1.6".equals(protocol)) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        chargePointSessions.put(chargePointId, session);
        System.out.println("Charge Point Connected: " + chargePointId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String chargePointId = WebSocketUtil.extractChargePointId(session);
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());

        if (jsonNode.isArray() && jsonNode.size() > 2) {
            String messageType = jsonNode.get(2).asText();
            System.out.println("Testing ......");
            ocppService.processMessage(session, chargePointId, messageType, jsonNode);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String chargePointId = WebSocketUtil.extractChargePointId(session);
        chargePointSessions.remove(chargePointId);
        ocppService.removeChargePoint(chargePointId);
        System.out.println("Charge Point Disconnected: " + chargePointId);
    }
}