package com.oddlycoder.facep2pv2.ws;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

public class AuthHandshakeConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public boolean checkOrigin(String originHeaderValue) {
        // Tighten to your domain in production
        return true;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec,
                                HandshakeRequest request,
                                HandshakeResponse response) {
        HttpSession httpSession = (HttpSession) request.getHttpSession();
        if (httpSession != null && httpSession.getAttribute("currentUser") != null) {
            sec.getUserProperties().put("currentUser", httpSession.getAttribute("currentUser"));
        }
    }
}
