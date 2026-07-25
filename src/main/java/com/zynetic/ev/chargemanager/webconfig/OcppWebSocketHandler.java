package com.zynetic.ev.chargemanager.webconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zynetic.ev.chargemanager.service.OcppService;
import com.zynetic.ev.chargemanager.utility.WebSocketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket entry point for OCPP 1.6 charge point connections.
 * Validates the Sec-WebSocket-Protocol header on connect, tracks active
 * sessions so the server can push messages back to a specific charger,
 * and delegates all OCPP message parsing/handling to OcppService.
 */
@Component
public class OcppWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OcppWebSocketHandler.class);

    // chargePointId -> active session; keyed by the id in the WebSocket URL path (e.g. /ocpp/{chargePointId})
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

        // OCPP 1.6 charge points must negotiate this subprotocol; reject anything else
        if (!"ocpp1.6".equals(protocol)) {
            log.warn("Rejected connection from {} â unsupported protocol: {}", chargePointId, protocol);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        chargePointSessions.put(chargePointId, session);
        log.info("Charge Point Connected: {}", chargePointId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String chargePointId = WebSocketUtil.extractChargePointId(session);
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());

        // OCPP "Call" messages are JSON arrays: [messageTypeId, uniqueId, action, payload]
        if (jsonNode.isArray() && jsonNode.size() > 2) {
            String messageType = jsonNode.get(2).asText();
            ocppService.processMessage(session, chargePointId, messageType, jsonNode);
        } else {
            log.warn("Malformed OCPP message from {}: {}", chargePointId, message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String chargePointId = WebSocketUtil.extractChargePointId(session);
        chargePointSessions.remove(chargePointId);
        ocppService.removeChargePoint(chargePointId);
        log.info("Charge Point Disconnected: {} ({})", chargePointId, status);
    }
}
