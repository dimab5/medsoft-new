package com.medsoft.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medsoft.models.RecognitionResult;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceWebSocketHandler extends TextWebSocketHandler {

	private static final Set<WebSocketSession> sessions =
			ConcurrentHashMap.newKeySet();

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.add(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session);
	}

	public void broadcast(RecognitionResult result) {
		sessions.forEach(session -> {
			try {
				session.sendMessage(
						new TextMessage(mapper.writeValueAsString(result))
				);
			} catch (Exception ignored) {}
		});
	}
}
