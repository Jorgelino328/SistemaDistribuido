package common.pattern;

import common.model.ComponentInfo;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementação do padrão Heartbeat para sistemas distribuídos.
 * Fornece funcionalidades para envio e recebimento de mensagens de heartbeat.
 */
public class HeartbeatPattern {
    private static final Logger LOGGER = Logger.getLogger(HeartbeatPattern.class.getName());
    
    // Configuração
    private final String componentType;
    private final int port;
    private final int heartbeatIntervalMs;
    private final int heartbeatTimeoutMs;
    private final int maxMissedHeartbeats;
    
    // Dados de monitoramento
    private final Map<String, ComponentInfo> monitoredComponents = new ConcurrentHashMap<>();
    private final Map<String, Integer> missedHeartbeats = new ConcurrentHashMap<>();
    
    // Handlers de callback
    private Consumer<ComponentInfo> onComponentHealthy;
    private Consumer<ComponentInfo> onComponentSuspect;
    private Consumer<ComponentInfo> onComponentDead;
    
    // Comunicação
    private DatagramSocket socket;
    
    // Agendador
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private boolean running = false;
    
    /**
     * Construtor para HeartbeatPattern.
     * 
     * @param componentType Tipo do componente
     * @param componentId ID único do componente
     * @param host Endereço do host do componente
     * @param port Porta para comunicação de heartbeat
     * @param heartbeatIntervalMs Intervalo entre heartbeats em milissegundos
     * @param heartbeatTimeoutMs Timeout para respostas de heartbeat em milissegundos
     * @param maxMissedHeartbeats Número máximo de heartbeats perdidos antes de considerar um componente inativo
     */
    public HeartbeatPattern(String componentType, String componentId, String host, int port,
                           int heartbeatIntervalMs, int heartbeatTimeoutMs, int maxMissedHeartbeats) {
        this.componentType = componentType;
        this.port = port;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        this.maxMissedHeartbeats = maxMissedHeartbeats;
    }
    
    /**
     * Define o callback para quando um componente for detectado como saudável.
     * 
     * @param onComponentHealthy Função de callback
     * @return Esta instância de HeartbeatPattern para encadeamento de métodos
     */
    public HeartbeatPattern onComponentHealthy(Consumer<ComponentInfo> onComponentHealthy) {
        this.onComponentHealthy = onComponentHealthy;
        return this;
    }
    
    /**
     * Define o callback para quando um componente for detectado como suspeito.
     * 
     * @param onComponentSuspect Função de callback
     * @return Esta instância de HeartbeatPattern para encadeamento de métodos
     */
    public HeartbeatPattern onComponentSuspect(Consumer<ComponentInfo> onComponentSuspect) {
        this.onComponentSuspect = onComponentSuspect;
        return this;
    }
    
    /**
     * Define o callback para quando um componente for detectado como inativo.
     * 
     * @param onComponentDead Função de callback
     * @return Esta instância de HeartbeatPattern para encadeamento de métodos
     */
    public HeartbeatPattern onComponentDead(Consumer<ComponentInfo> onComponentDead) {
        this.onComponentDead = onComponentDead;
        return this;
    }
    
    /**
     * Inicia a implementação do padrão Heartbeat.
     * Isso iniciará tanto o envio quanto o recebimento de mensagens de heartbeat, se configurado.
     */
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        
        try {
            // Cria o socket para comunicação UDP
            socket = new DatagramSocket(port);
            socket.setSoTimeout(heartbeatTimeoutMs);
            
            // Inicia o respondedor de heartbeat
            startHeartbeatResponder();
            
            // Inicia o monitor de heartbeat se houver componentes para monitorar
            if (!monitoredComponents.isEmpty()) {
                startHeartbeatMonitor();
            }
            
            LOGGER.info("Padrão Heartbeat iniciado para " + componentType + 
                       " na porta " + port);
        } catch (SocketException e) {
            LOGGER.log(Level.SEVERE, "Falha ao criar o socket de heartbeat", e);
            stop();
        }
    }
    
    /**
     * Para a implementação do padrão Heartbeat.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        // Encerra o agendador
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Fecha o socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        LOGGER.info("Padrão Heartbeat parado para " + componentType);
    }
    
    /**
     * Adiciona um componente para monitoramento com heartbeats.
     * 
     * @param component Componente a ser monitorado
     */
    public void monitorComponent(ComponentInfo component) {
        monitoredComponents.put(createComponentKey(component), component);
        missedHeartbeats.put(createComponentKey(component), 0);
        
        LOGGER.info("Agora monitorando " + component.getType() + " em " + 
                   component.getHost() + ":" + component.getUdpPort());
        
        // Inicia o monitor se ainda não estiver em execução
        if (running && monitoredComponents.size() == 1) {
            startHeartbeatMonitor();
        }
    }
    
    /**
     * Remove um componente do monitoramento.
     * 
     * @param component Componente a ser removido do monitoramento
     */
    public void stopMonitoring(ComponentInfo component) {
        String key = createComponentKey(component);
        monitoredComponents.remove(key);
        missedHeartbeats.remove(key);
        
        LOGGER.info("Parou de monitorar " + component.getType() + " em " + 
                   component.getHost() + ":" + component.getUdpPort());
    }
    
    /**
     * Inicia o respondedor de heartbeat para responder a mensagens de heartbeat.
     */
    private void startHeartbeatResponder() {
        Thread responderThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    
                    // Extrai a mensagem
                    String message = new String(packet.getData(), 0, packet.getLength());
                    
                    // Lida com a mensagem de heartbeat
                    if ("HEARTBEAT".equals(message)) {
                        // Envia a resposta de heartbeat
                        String response = "HEARTBEAT_ACK";
                        DatagramPacket responsePacket = new DatagramPacket(
                            response.getBytes(), response.length(),
                            packet.getAddress(), packet.getPort()
                        );
                        socket.send(responsePacket);
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout esperado, continua
                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.WARNING, "Erro no respondedor de heartbeat", e);
                    }
                }
            }
        });
        
        responderThread.setDaemon(true);
        responderThread.start();
    }
    
    /**
     * Inicia o monitor de heartbeat para enviar heartbeats aos componentes monitorados.
     */
    private void startHeartbeatMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            for (ComponentInfo component : monitoredComponents.values()) {
                checkComponentHealth(component);
            }
        }, 0, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Verifica a saúde de um componente enviando um heartbeat.
     * 
     * @param component Componente a ser verificado
     */
    private void checkComponentHealth(ComponentInfo component) {
        String key = createComponentKey(component);
        
        try {
            // Envia a mensagem de heartbeat
            String message = "HEARTBEAT";
            InetAddress address = InetAddress.getByName(component.getHost());
            DatagramPacket packet = new DatagramPacket(
                message.getBytes(), message.length(),
                address, component.getUdpPort()
            );
            
            socket.send(packet);
            
            // Aguarda a resposta
            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            
            try {
                socket.receive(responsePacket);
                String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
                
                if ("HEARTBEAT_ACK".equals(response)) {
                    // Componente está saudável
                    missedHeartbeats.put(key, 0);
                    
                    if (onComponentHealthy != null) {
                        onComponentHealthy.accept(component);
                    }
                }
            } catch (SocketTimeoutException e) {
                // Nenhuma resposta recebida, incrementa os heartbeats perdidos
                int missed = missedHeartbeats.getOrDefault(key, 0) + 1;
                missedHeartbeats.put(key, missed);
                
                if (missed >= maxMissedHeartbeats) {
                    // Componente é considerado inativo
                    if (onComponentDead != null) {
                        onComponentDead.accept(component);
                    }
                    
                    // Para de monitorar este componente
                    stopMonitoring(component);
                } else {
                    // Componente é suspeito
                    if (onComponentSuspect != null) {
                        onComponentSuspect.accept(component);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao verificar a saúde do componente", e);
        }
    }
    
    /**
     * Cria uma chave única para um componente.
     * 
     * @param component Componente
     * @return Chave única
     */
    private String createComponentKey(ComponentInfo component) {
        return component.getType() + "-" + component.getHost() + "-" + component.getUdpPort();
    }
    
    /**
     * Cria uma instância de HeartbeatPattern configurada como respondedor.
     * 
     * @param componentType Tipo do componente
     * @param componentId ID do componente
     * @param host Endereço do host
     * @param port Porta UDP
     * @return Instância configurada de HeartbeatPattern
     */
    public static HeartbeatPattern createResponder(String componentType, String componentId, 
                                                  String host, int port) {
        return new HeartbeatPattern(componentType, componentId, host, port, 0, 1000, 0);
    }
    
    /**
     * Cria uma instância de HeartbeatPattern configurada como monitor.
     * 
     * @param componentType Tipo do componente
     * @param componentId ID do componente
     * @param host Endereço do host
     * @param port Porta UDP
     * @param heartbeatIntervalMs Intervalo entre heartbeats em milissegundos
     * @param heartbeatTimeoutMs Timeout para respostas de heartbeat em milissegundos
     * @param maxMissedHeartbeats Número máximo de heartbeats perdidos antes de considerar um componente inativo
     * @return Instância configurada de HeartbeatPattern
     */
    public static HeartbeatPattern createMonitor(String componentType, String componentId, 
                                               String host, int port, int heartbeatIntervalMs,
                                               int heartbeatTimeoutMs, int maxMissedHeartbeats) {
        return new HeartbeatPattern(componentType, componentId, host, port,
                                   heartbeatIntervalMs, heartbeatTimeoutMs, maxMissedHeartbeats);
    }
}