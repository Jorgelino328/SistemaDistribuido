package common.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ComponentInfo {
    private final String type;
    private final String instanceId;
    private final String host;
    private final int httpPort;
    private final int tcpPort;
    private final int udpPort;
    
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicBoolean suspect = new AtomicBoolean(false);
    private final AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
    
    private String keyRangeStart;
    private String keyRangeEnd;
    
    public ComponentInfo(String type, String instanceId, String host, int httpPort, int tcpPort, int udpPort) {
        this.type = type;
        this.instanceId = instanceId;
        this.host = host;
        this.httpPort = httpPort;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
    }
    
    public ComponentInfo(String type, String host, int httpPort, int tcpPort, int udpPort) {
        this(type, type + "_" + host + "_" + httpPort, host, httpPort, tcpPort, udpPort);
    }
    
    public String getType() {
        return type;
    }
    
    public String getInstanceId() {
        return instanceId;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getHttpPort() {
        return httpPort;
    }
    
    public int getTcpPort() {
        return tcpPort;
    }
    
    public int getUdpPort() {
        return udpPort;
    }
    
    public int getPortForProtocol(String protocol) {
        switch (protocol.toLowerCase()) {
            case "http":
                return httpPort;
            case "tcp":
                return tcpPort;
            case "udp":
                return udpPort;
            default:
                throw new IllegalArgumentException("Protocolo não suportado: " + protocol);
        }
    }
    
    public boolean isHealthy() {
        return healthy.get();
    }
    
    public boolean isSuspect() {
        return suspect.get();
    }
    
    public long getLastHeartbeat() {
        return lastHeartbeat.get();
    }
    

    public void markHealthy() {
        healthy.set(true);
        suspect.set(false);
        lastHeartbeat.set(System.currentTimeMillis());
    }
    
    public void markSuspect() {
        suspect.set(true);
    }
    
    public void markDead() {
        healthy.set(false);
    }
    
    public void updateHeartbeat() {
        lastHeartbeat.set(System.currentTimeMillis());
    }
    
    public void setKeyRange(String start, String end) {
        this.keyRangeStart = start;
        this.keyRangeEnd = end;
    }
    
    public String getKeyRangeStart() {
        return keyRangeStart;
    }
    
    public String getKeyRangeEnd() {
        return keyRangeEnd;
    }
    
    public boolean isResponsibleForKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        
        String identifier = key;
        if (key.contains(":")) {
            identifier = key.substring(key.indexOf(":") + 1);
        }
        
        if (identifier.isEmpty()) {
            return false;
        }
        
        String compareKey = identifier.substring(0, 1).toUpperCase();
        
        if (keyRangeStart == null && keyRangeEnd == null) {
            return true;
        }
        
        String normalizedStart = keyRangeStart != null ? keyRangeStart.toUpperCase() : null;
        String normalizedEnd = keyRangeEnd != null ? keyRangeEnd.toUpperCase() : null;
        
        if (normalizedStart == null) {
            return compareKey.compareTo(normalizedEnd) < 0;
        }
        
        if (normalizedEnd == null) {
            return compareKey.compareTo(normalizedStart) >= 0;
        }
        
        return compareKey.compareTo(normalizedStart) >= 0 && 
               compareKey.compareTo(normalizedEnd) < 0;
    }

    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ComponentInfo that = (ComponentInfo) o;
        
        return httpPort == that.httpPort &&
               tcpPort == that.tcpPort &&
               udpPort == that.udpPort &&
               Objects.equals(type, that.type) &&
               Objects.equals(instanceId, that.instanceId) &&
               Objects.equals(host, that.host);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, instanceId, host, httpPort, tcpPort, udpPort);
    }
    
    @Override
    public String toString() {
        return "ComponentInfo{" +
               "type='" + type + '\'' +
               ", instanceId='" + instanceId + '\'' +
               ", host='" + host + '\'' +
               ", httpPort=" + httpPort +
               ", tcpPort=" + tcpPort +
               ", udpPort=" + udpPort +
               ", healthy=" + healthy +
               ", suspect=" + suspect +
               '}';
    }
}
