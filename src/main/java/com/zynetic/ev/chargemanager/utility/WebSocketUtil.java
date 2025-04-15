package com.zynetic.ev.chargemanager.utility;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketSession;

public class WebSocketUtil {

    public static String extractChargePointId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return (path != null && path.contains("/")) ? path.substring(path.lastIndexOf('/') + 1) : null;
    }

    public static String getWebSocketProtocol(WebSocketSession session) {
        HttpHeaders headers = session.getHandshakeHeaders();
        return headers.getFirst("Sec-WebSocket-Protocol");
    }
}
