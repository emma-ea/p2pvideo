package com.oddlycoder.facep2pv2.ws;

import jakarta.json.*;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class JsonMessageDecoder implements Decoder.Text<SignalMessage> {

    @Override
    public SignalMessage decode(String s) throws DecodeException {
        try (JsonReader reader = Json.createReader(new StringReader(s))) {
            JsonObject json = reader.readObject();
            SignalMessage msg = new SignalMessage();

            if (json.containsKey("type")) msg.setType(json.getString("type"));
            if (json.containsKey("from")) msg.setFrom(json.getString("from"));
            if (json.containsKey("to"))   msg.setTo(json.getString("to"));
            if (json.containsKey("sdp"))  msg.setSdp(json.getString("sdp"));

            if (json.containsKey("candidate")) {
                JsonObject candObj = json.getJsonObject("candidate");
                Map<String, Object> candidate = new HashMap<>();
                candObj.forEach((k, v) -> {
                    switch (v.getValueType()) {
                        case STRING  -> candidate.put(k, ((JsonString) v).getString());
                        case NUMBER  -> candidate.put(k, ((JsonNumber) v).numberValue());
                        case TRUE    -> candidate.put(k, Boolean.TRUE);
                        case FALSE   -> candidate.put(k, Boolean.FALSE);
                        default      -> candidate.put(k, v.toString());
                    }
                });
                msg.setCandidate(candidate);
            }

            return msg;
        } catch (Exception e) {
            throw new DecodeException(s, "Failed to decode SignalMessage", e);
        }
    }

    @Override
    public boolean willDecode(String s) {
        return s != null && s.startsWith("{");
    }

    @Override public void init(jakarta.websocket.EndpointConfig config) {}
    @Override public void destroy() {}
}
