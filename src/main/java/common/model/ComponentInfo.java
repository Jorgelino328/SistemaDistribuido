package common.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Classe que representa informações sobre um componente no sistema distribuído.
 * Contém o tipo do componente, localização na rede e status de saúde.
 */
public class ComponentInfo {
    private final String type;
    private final String instanceId;
    private final String host;
    private final int httpPort;
    private final int tcpPort;
    private final int udpPort;
    
    // Status de saúde do componente
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicBoolean suspect = new AtomicBoolean(false);
    private final AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
    
    /**
     * Construtor para ComponentInfo.
     * 
     * @param type Tipo do componente (ex.: "userservice", "messageservice")
     * @param instanceId ID único da instância do componente
     * @param host Endereço do host
     * @param httpPort Porta HTTP
     * @param tcpPort Porta TCP
     * @param udpPort Porta UDP
     */
    public ComponentInfo(String type, String instanceId, String host, int httpPort, int tcpPort, int udpPort) {
        this.type = type;
        this.instanceId = instanceId;
        this.host = host;
        this.httpPort = httpPort;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
    }
    
    /**
     * Construtor para ComponentInfo (compatibilidade).
     * 
     * @param type Tipo do componente (ex.: "userservice", "messageservice")
     * @param host Endereço do host
     * @param httpPort Porta HTTP
     * @param tcpPort Porta TCP
     * @param udpPort Porta UDP
     */
    public ComponentInfo(String type, String host, int httpPort, int tcpPort, int udpPort) {
        this(type, type + "_" + host + "_" + httpPort, host, httpPort, tcpPort, udpPort);
    }
    
    /**
     * Obtém o tipo do componente.
     * 
     * @return Tipo do componente
     */
    public String getType() {
        return type;
    }
    
    /**
     * Obtém o ID único da instância.
     * 
     * @return ID da instância
     */
    public String getInstanceId() {
        return instanceId;
    }
    
    /**
     * Obtém o endereço do host.
     * 
     * @return Endereço do host
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Obtém a porta HTTP.
     * 
     * @return Porta HTTP
     */
    public int getHttpPort() {
        return httpPort;
    }
    
    /**
     * Obtém a porta TCP.
     * 
     * @return Porta TCP
     */
    public int getTcpPort() {
        return tcpPort;
    }
    
    /**
     * Obtém a porta UDP.
     * 
     * @return Porta UDP
     */
    public int getUdpPort() {
        return udpPort;
    }
    
    /**
     * Obtém a porta para um protocolo específico.
     * 
     * @param protocol Nome do protocolo (http, tcp, udp)
     * @return Número da porta para o protocolo especificado
     * @throws IllegalArgumentException Se o protocolo não for suportado
     */
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
    
    /**
     * Verifica se o componente está saudável.
     * 
     * @return true se estiver saudável, false caso contrário
     */
    public boolean isHealthy() {
        return healthy.get();
    }
    
    /**
     * Verifica se o componente está suspeito.
     * 
     * @return true se estiver suspeito, false caso contrário
     */
    public boolean isSuspect() {
        return suspect.get();
    }
    
    /**
     * Obtém o timestamp do último heartbeat.
     * 
     * @return Timestamp do último heartbeat
     */
    public long getLastHeartbeat() {
        return lastHeartbeat.get();
    }
    

    
    /**
     * Marca o componente como saudável.
     */
    public void markHealthy() {
        healthy.set(true);
        suspect.set(false);
        lastHeartbeat.set(System.currentTimeMillis());
    }
    
    /**
     * Marca o componente como suspeito.
     */
    public void markSuspect() {
        suspect.set(true);
    }
    
    /**
     * Marca o componente como inativo.
     */
    public void markDead() {
        healthy.set(false);
    }
    
    /**
     * Atualiza o timestamp do último heartbeat.
     */
    public void updateHeartbeat() {
        lastHeartbeat.set(System.currentTimeMillis());
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
