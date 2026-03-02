package com.oddlycoder.facep2pv2.ws;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;

public class JsonMessageEncoder implements Encoder.Text<SignalMessage> {

    @Override
    public String encode(SignalMessage msg) throws EncodeException {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        if (msg.getType() != null)  builder.add("type", msg.getType());
        if (msg.getFrom() != null)  builder.add("from", msg.getFrom());
        if (msg.getTo() != null)    builder.add("to",   msg.getTo());
        if (msg.getSdp()  != null)  builder.add("sdp",  msg.getSdp());

        if (msg.getCandidate() != null) {
            JsonObjectBuilder cand = Json.createObjectBuilder();
            msg.getCandidate().forEach((k, v) -> {
                if (v instanceof String s)      cand.add(k, s);
                else if (v instanceof Number n) cand.add(k, n.doubleValue());
                else if (v instanceof Boolean b) cand.add(k, b);
                else if (v != null)             cand.add(k, v.toString());
            });
            builder.add("candidate", cand.build());
        }

        if (msg.getUsers() != null) {
            JsonArrayBuilder arr = Json.createArrayBuilder();
            msg.getUsers().forEach(user -> {
                JsonObjectBuilder ub = Json.createObjectBuilder();
                user.forEach(ub::add);
                arr.add(ub.build());
            });
            builder.add("users", arr.build());
        }

        return builder.build().toString();
    }

    @Override public void init(jakarta.websocket.EndpointConfig config) {}
    @Override public void destroy() {}
}
