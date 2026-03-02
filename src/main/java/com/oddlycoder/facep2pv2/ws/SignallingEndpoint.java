package com.oddlycoder.facep2pv2.ws;

import com.oddlycoder.facep2pv2.entity.CallOutcome;
import com.oddlycoder.facep2pv2.entity.User;
import com.oddlycoder.facep2pv2.entity.UserStatus;
import com.oddlycoder.facep2pv2.service.CallService;
import com.oddlycoder.facep2pv2.service.UserService;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(
    value = "/signal",
    encoders = JsonMessageEncoder.class,
    decoders = JsonMessageDecoder.class,
    configurator = AuthHandshakeConfigurator.class
)
public class SignallingEndpoint {

    // username → WebSocket Session
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    // callerUsername → active CallRecord ID
    private static final Map<String, Long> activeCalls = new ConcurrentHashMap<>();

    @Inject
    private UserService userService;

    @Inject
    private CallService callService;

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        if (config.getUserProperties().get("currentUser") == null) {
            try {
                session.close(new CloseReason(
                    CloseReason.CloseCodes.VIOLATED_POLICY, "Unauthorized"));
            } catch (IOException ignored) {}
        }
    }

    @OnMessage
    public void onMessage(SignalMessage msg, Session senderSession) throws IOException {
        switch (msg.getType()) {
            case "register"      -> handleRegister(msg, senderSession);
            case "call-offer"    -> handleCallOffer(msg, senderSession);
            case "call-answer"   -> handleCallAnswer(msg, senderSession);
            case "call-reject"   -> handleCallReject(msg, senderSession);
            case "call-hangup"   -> handleCallHangup(msg, senderSession);
            case "ice-candidate",
                 "screen-share-start",
                 "screen-share-stop"  -> relay(msg, senderSession);
            default -> senderSession.getBasicRemote()
                .sendText("{\"type\":\"error\",\"message\":\"Unknown message type\"}");
        }
    }

    @OnClose
    public void onClose(Session session) {
        String username = (String) session.getUserProperties().get("username");
        if (username != null) {
            sessions.remove(username);
            // End any active call this user was in
            Long callId = activeCalls.remove(username);
            if (callId != null) {
                callService.endCall(callId, CallOutcome.FAILED);
            }
            userService.setStatus(username, UserStatus.OFFLINE);
            broadcastPresence();
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        onClose(session);
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private void handleRegister(SignalMessage msg, Session session) {
        String username = msg.getFrom();
        session.getUserProperties().put("username", username);
        sessions.put(username, session);
        userService.setStatus(username, UserStatus.ONLINE);
        broadcastPresence();
    }

    private void handleCallOffer(SignalMessage msg, Session senderSession) throws IOException {
        String callerUsername = senderUsername(senderSession);
        msg.setFrom(callerUsername);

        // Check if callee is BUSY
        Optional<User> calleeOpt = userService.findByUsername(msg.getTo());
        if (calleeOpt.isEmpty()) return;

        User callee = calleeOpt.get();
        if (callee.getStatus() == UserStatus.BUSY) {
            SignalMessage busy = new SignalMessage();
            busy.setType("call-busy");
            busy.setFrom(msg.getTo());
            try {
                senderSession.getBasicRemote().sendObject(busy);
            } catch (EncodeException e) {
                throw new IOException("Failed to encode call-busy message", e);
            }
            return;
        }

        // Create call record
        Optional<User> callerOpt = userService.findByUsername(callerUsername);
        if (callerOpt.isPresent()) {
            Long callId = callService.startCall(callerOpt.get(), callee).getId();
            activeCalls.put(callerUsername, callId);
            userService.setStatus(callerUsername, UserStatus.BUSY);
            broadcastPresence();
        }

        relay(msg, senderSession);
    }

    private void handleCallAnswer(SignalMessage msg, Session senderSession) throws IOException {
        String calleeUsername = senderUsername(senderSession);
        msg.setFrom(calleeUsername);

        // Find the active call for the caller
        String callerUsername = msg.getTo();
        Long callId = activeCalls.get(callerUsername);
        if (callId != null) {
            callService.markAnswered(callId);
            userService.setStatus(calleeUsername, UserStatus.BUSY);
            broadcastPresence();
        }

        relay(msg, senderSession);
    }

    private void handleCallReject(SignalMessage msg, Session senderSession) throws IOException {
        String calleeUsername = senderUsername(senderSession);
        msg.setFrom(calleeUsername);

        String callerUsername = msg.getTo();
        Long callId = activeCalls.remove(callerUsername);
        if (callId != null) {
            callService.endCall(callId, CallOutcome.REJECTED);
            userService.setStatus(callerUsername, UserStatus.ONLINE);
            broadcastPresence();
        }

        relay(msg, senderSession);
    }

    private void handleCallHangup(SignalMessage msg, Session senderSession) throws IOException {
        String senderName = senderUsername(senderSession);
        msg.setFrom(senderName);

        // The other party could be caller or callee — check both
        String otherParty = msg.getTo();
        Long callId = activeCalls.remove(senderName);
        if (callId == null) callId = activeCalls.remove(otherParty);
        if (callId != null) {
            callService.endCall(callId, CallOutcome.COMPLETED);
        }
        userService.setStatus(senderName, UserStatus.ONLINE);
        if (otherParty != null) userService.setStatus(otherParty, UserStatus.ONLINE);
        broadcastPresence();

        relay(msg, senderSession);
    }

    private void relay(SignalMessage msg, Session senderSession) throws IOException {
        String senderName = senderUsername(senderSession);
        if (msg.getFrom() == null) msg.setFrom(senderName);
        Session target = sessions.get(msg.getTo());
        if (target != null && target.isOpen()) {
            try {
                target.getBasicRemote().sendObject(msg);
            } catch (EncodeException e) {
                throw new IOException("Failed to encode message", e);
            }
        }
    }

    private void broadcastPresence() {
        SignalMessage presence = buildPresenceMessage();
        sessions.values().stream()
                .filter(Session::isOpen)
                .forEach(s -> {
                    try { s.getBasicRemote().sendObject(presence); }
                    catch (Exception ignored) {}
                });
    }

    private SignalMessage buildPresenceMessage() {
        SignalMessage msg = new SignalMessage();
        msg.setType("presence");
        msg.setUsers(userService.getOnlineUserSummaries());
        return msg;
    }

    private String senderUsername(Session session) {
        return (String) session.getUserProperties().get("username");
    }
}
