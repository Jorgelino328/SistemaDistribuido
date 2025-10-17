package common.pattern;

import common.model.ComponentInfo;
import gateway.ComponentRegistry;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitor de heartbeat para verificar a disponibilidade dos componentes.
 * Envia mensagens de heartbeat periodicamente e remove componentes que não respondem.
 */
public class HeartbeatMonitor {
    private static final Logger LOGGER = Logger.getLogger(HeartbeatMonitor.class.getName());
    
    private final ComponentRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final int heartbeatInterval; // em segundos
    private final int timeoutThreshold; // em segundos
    private DatagramSocket udpSocket;
    
    public HeartbeatMonitor(ComponentRegistry registry, int heartbeatInterval, int timeoutThreshold) {
        this.registry = registry;
        this.heartbeatInterval = heartbeatInterval;
        this.timeoutThreshold = timeoutThreshold;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Inicia o monitoramento de heartbeat.
     */
    public void start() {
        try {
            udpSocket = new DatagramSocket();
            
            // Agenda envio de heartbeats
            scheduler.scheduleAtFixedRate(this::sendHeartbeats, 0, heartbeatInterval, TimeUnit.SECONDS);
            
            // Agenda verificação de timeouts
            scheduler.scheduleAtFixedRate(this::checkTimeouts, timeoutThreshold, timeoutThreshold, TimeUnit.SECONDS);
            
            LOGGER.info("HeartbeatMonitor iniciado - interval: " + heartbeatInterval + "s, timeout: " + timeoutThreshold + "s");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao iniciar HeartbeatMonitor", e);
        }
    }
    
    /**
     * Para o monitoramento de heartbeat.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        LOGGER.info("HeartbeatMonitor parado");
    }
    
    /**
     * Envia mensagens de heartbeat para todos os componentes registrados.
     */
    private void sendHeartbeats() {
        List<ComponentInfo> components = registry.getAllComponentsList();
        
        for (ComponentInfo component : components) {
            try {
                sendHeartbeatToComponent(component);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao enviar heartbeat para " + component.getInstanceId(), e);
                // Marca como suspeito em caso de erro
                component.markSuspect();
            }
        }
    }
    
    /**
     * Envia uma mensagem de heartbeat para um componente específico.
     */
    private void sendHeartbeatToComponent(ComponentInfo component) throws Exception {
        String heartbeatMessage = "HEARTBEAT";
        byte[] data = heartbeatMessage.getBytes(StandardCharsets.UTF_8);
        
        InetAddress address = InetAddress.getByName(component.getHost());
        DatagramPacket packet = new DatagramPacket(
            data, data.length, address, component.getUdpPort()
        );
        
        udpSocket.send(packet);
        
        // Aguarda resposta (com timeout)
        byte[] responseBuffer = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
        
        udpSocket.setSoTimeout(2000); // 2 segundos timeout
        
        try {
            udpSocket.receive(responsePacket);
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength(), StandardCharsets.UTF_8);
            
            if ("HEARTBEAT_ACK".equals(response.trim())) {
                component.updateHeartbeat();
                component.markHealthy();
            } else {
                component.markSuspect();
            }
        } catch (Exception e) {
            // Timeout ou erro - marca como suspeito
            component.markSuspect();
        }
    }
    
    /**
     * Verifica timeouts e remove componentes inativos.
     */
    private void checkTimeouts() {
        List<ComponentInfo> components = registry.getAllComponentsList();
        long currentTime = System.currentTimeMillis();
        long timeoutMs = timeoutThreshold * 1000L;
        
        for (ComponentInfo component : components) {
            long lastHeartbeat = component.getLastHeartbeat();
            
            if ((currentTime - lastHeartbeat) > timeoutMs) {
                if (component.isSuspect()) {
                    // Se já estava suspeito e ainda não respondeu, remove
                    LOGGER.warning("Removendo componente inativo: " + component.getInstanceId());
                    registry.removeComponent(component);
                } else {
                    // Primeira vez sem resposta, marca como suspeito
                    component.markSuspect();
                    LOGGER.warning("Componente suspeito: " + component.getInstanceId());
                }
            }
        }
    }
}