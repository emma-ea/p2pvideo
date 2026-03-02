package com.oddlycoder.facep2pv2.ws;

import java.util.List;
import java.util.Map;

public class SignalMessage {

    private String type;
    private String from;
    private String to;
    private String sdp;
    private Map<String, Object> candidate; // raw ICE candidate object
    private List<Map<String, String>> users; // for presence broadcasts

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSdp() { return sdp; }
    public void setSdp(String sdp) { this.sdp = sdp; }

    public Map<String, Object> getCandidate() { return candidate; }
    public void setCandidate(Map<String, Object> candidate) { this.candidate = candidate; }

    public List<Map<String, String>> getUsers() { return users; }
    public void setUsers(List<Map<String, String>> users) { this.users = users; }
}
