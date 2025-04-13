package common.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Classe que representa uma mensagem de heartbeat trocada entre os componentes
 * e o Gateway de API para monitoramento de saúde.
 */
public class HeartbeatMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Type {
        PING,       // Solicitação do gateway para o componente
        PONG,       // Resposta do componente para o gateway
        DEAD        // Notificação de que um componente é considerado inativo
    }
    
    private final String id;
    private final Type type;
    private final long timestamp;
    private final String componentType;
    private final String componentId;
    private final String host;
    private final int port;
    
    /**
     * Construtor para criar uma mensagem de heartbeat.
     * 
     * @param type Tipo da mensagem (PING, PONG, DEAD)
     * @param componentType Tipo do componente (ex.: "componentA")
     * @param componentId ID único da instância do componente
     * @param host Endereço do host do componente
     * @param port Número da porta do componente
     */
    public HeartbeatMessage(Type type, String componentType, String componentId, String host, int port) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.componentType = componentType;
        this.componentId = componentId;
        this.host = host;
        this.port = port;
    }
    
    /**
     * Cria uma mensagem de heartbeat do tipo PING.
     * 
     * @param componentType Tipo do componente
     * @return Uma nova mensagem de heartbeat do tipo PING
     */
    public static HeartbeatMessage createPing(String componentType) {
        return new HeartbeatMessage(Type.PING, componentType, null, null, 0);
    }
    
    /**
     * Cria uma mensagem de heartbeat do tipo PONG.
     * 
     * @param componentType Tipo do componente
     * @param componentId ID da instância do componente
     * @param host Host do componente
     * @param port Porta do componente
     * @return Uma nova mensagem de heartbeat do tipo PONG
     */
    public static HeartbeatMessage createPong(String componentType, String componentId, String host, int port) {
        return new HeartbeatMessage(Type.PONG, componentType, componentId, host, port);
    }
    
    /**
     * Cria uma mensagem de heartbeat do tipo DEAD.
     * 
     * @param componentType Tipo do componente
     * @param componentId ID da instância do componente
     * @param host Host do componente
     * @param port Porta do componente
     * @return Uma nova mensagem de heartbeat do tipo DEAD
     */
    public static HeartbeatMessage createDead(String componentType, String componentId, String host, int port) {
        return new HeartbeatMessage(Type.DEAD, componentType, componentId, host, port);
    }
    
    /**
     * Obtém o ID da mensagem.
     * 
     * @return ID da mensagem
     */
    public String getId() {
        return id;
    }
    
    /**
     * Obtém o tipo da mensagem.
     * 
     * @return Tipo da mensagem
     */
    public Type getType() {
        return type;
    }
    
    /**
     * Obtém o timestamp da mensagem.
     * 
     * @return Timestamp da mensagem
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Obtém o tipo do componente.
     * 
     * @return Tipo do componente
     */
    public String getComponentType() {
        return componentType;
    }
    
    /**
     * Obtém o ID do componente.
     * 
     * @return ID do componente
     */
    public String getComponentId() {
        return componentId;
    }
    
    /**
     * Obtém o host do componente.
     * 
     * @return Host do componente
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Obtém a porta do componente.
     * 
     * @return Porta do componente
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Converte a mensagem de heartbeat para um formato de string para transmissão na rede.
     * Formato: HEARTBEAT|TYPE|ID|TIMESTAMP|COMPONENT_TYPE|COMPONENT_ID|HOST|PORT
     * 
     * @return Representação em string da mensagem de heartbeat
     */
    public String toNetworkString() {
        return String.format("HEARTBEAT|%s|%s|%d|%s|%s|%s|%d",
                type.name(),
                id,
                timestamp,
                componentType != null ? componentType : "",
                componentId != null ? componentId : "",
                host != null ? host : "",
                port);
    }
    
    /**
     * Analisa uma mensagem de heartbeat a partir de uma string de rede.
     * 
     * @param message Representação em string da mensagem de heartbeat na rede
     * @return Objeto HeartbeatMessage
     * @throws IllegalArgumentException Se o formato da mensagem for inválido
     */
    public static HeartbeatMessage fromNetworkString(String message) {
        if (message == null || !message.startsWith("HEARTBEAT|")) {
            throw new IllegalArgumentException("Formato inválido de mensagem de heartbeat");
        }
        
        String[] parts = message.split("\\|");
        if (parts.length != 8) {
            throw new IllegalArgumentException("Número inválido de partes na mensagem de heartbeat");
        }
        
        Type type = Type.valueOf(parts[1]);
        String componentType = parts[4].isEmpty() ? null : parts[4];
        String componentId = parts[5].isEmpty() ? null : parts[5];
        String host = parts[6].isEmpty() ? null : parts[6];
        int port = Integer.parseInt(parts[7]);
        
        HeartbeatMessage heartbeat = new HeartbeatMessage(type, componentType, componentId, host, port);
        
        // Este é um exemplo simples - em uma implementação real, você pode querer
        // definir o ID e o timestamp a partir da mensagem também
        
        return heartbeat;
    }
    
    @Override
    public String toString() {
        return "HeartbeatMessage{" +
               "id='" + id + '\'' +
               ", type=" + type +
               ", timestamp=" + timestamp +
               ", componentType='" + componentType + '\'' +
               ", componentId='" + componentId + '\'' +
               ", host='" + host + '\'' +
               ", port=" + port +
               '}';
    }
}