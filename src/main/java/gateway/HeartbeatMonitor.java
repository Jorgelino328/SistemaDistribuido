package gateway;

import common.model.ComponentInfo;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitor de Heartbeat para verificar a saúde dos componentes registrados.
 * 
 * Este monitor envia mensagens de heartbeat periodicamente para os componentes
 * e verifica se eles estão respondendo. Componentes que não respondem são marcados
 * como suspeitos ou inativos.
 */
public class HeartbeatMonitor {
    private static final Logger LOGGER = Logger.getLogger(HeartbeatMonitor.class.getName());
    
    // Referência ao registro de componentes
    private final ComponentRegistry registry;
    
    // Configuração de heartbeat
    private static final int HEARTBEAT_INTERVAL_MS = 5000;  // Intervalo entre heartbeats (5 segundos)
    private static final int HEARTBEAT_TIMEOUT_MS = 3000;   // Timeout para resposta (3 segundos)
    private static final int MAX_MISSED_HEARTBEATS = 3;     // Máximo de heartbeats perdidos antes de marcar como inativo
    
    // Rastreamento de heartbeats perdidos por componente
    private final Map<ComponentInfo, Integer> missedHeartbeats = new ConcurrentHashMap<>();
    
    // Scheduler para verificações periódicas de heartbeat
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private boolean isRunning = false;

    /**
     * Construtor do monitor de heartbeat.
     * 
     * @param registry Registro de componentes
     */
    public HeartbeatMonitor(ComponentRegistry registry) {
        this.registry = registry;
    }

    /**
     * Inicia o monitor de heartbeat.
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        // LOGGER.info("Iniciando o Monitor de Heartbeat...");
        
        // Agenda verificações periódicas de heartbeat
        scheduler.scheduleAtFixedRate(
            this::checkAllComponents, 
            HEARTBEAT_INTERVAL_MS, 
            HEARTBEAT_INTERVAL_MS, 
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Para o monitor de heartbeat.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        // LOGGER.info("Parando o Monitor de Heartbeat...");
        
        // Encerra o scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Verifica a saúde de todos os componentes registrados.
     */
    private void checkAllComponents() {
        Map<String, List<ComponentInfo>> allComponents = registry.getAllComponents();
        
        for (List<ComponentInfo> components : allComponents.values()) {
            for (ComponentInfo component : components) {
                // Ignora componentes já marcados como inativos
                if (!component.isHealthy()) {
                    continue;
                }
                
                // Verifica a saúde do componente (usando UDP)
                boolean isAlive = checkComponentHealth(component);
                
                if (!isAlive) {
                    // Incrementa o contador de heartbeats perdidos
                    int missed = missedHeartbeats.getOrDefault(component, 0) + 1;
                    missedHeartbeats.put(component, missed);
                    
                    if (missed >= MAX_MISSED_HEARTBEATS) {
                        // Marca o componente como inativo após exceder o limite
                        // LOGGER.warning("Componente " + component.getType() + " em " + 
                        //              component.getHost() + ":" + component.getUdpPort() + 
                        //              " está inativo após " + missed + " heartbeats perdidos");
                        registry.markComponentDead(component);
                        missedHeartbeats.remove(component);
                    } else {
                        // Marca o componente como suspeito
                        // LOGGER.info("Componente " + component.getType() + " em " + 
                        //           component.getHost() + ":" + component.getUdpPort() + 
                        //           " perdeu heartbeat (" + missed + "/" + MAX_MISSED_HEARTBEATS + ")");
                        registry.markComponentSuspect(component);
                    }
                } else {
                    // Reseta o contador de heartbeats perdidos
                    missedHeartbeats.remove(component);
                    
                    // Marca o componente como saudável
                    component.markHealthy();
                }
            }
        }
    }

    /**
     * Verifica a saúde de um componente usando UDP.
     * 
     * @param component Componente a ser verificado
     * @return true se o componente estiver saudável, false caso contrário
     */
    private boolean checkComponentHealth(ComponentInfo component) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(HEARTBEAT_TIMEOUT_MS);
            
            // Prepara a mensagem de heartbeat
            String message = "HEARTBEAT";
            byte[] sendData = message.getBytes();
            
            // Envia o heartbeat
            InetAddress address = InetAddress.getByName(component.getHost());
            DatagramPacket sendPacket = new DatagramPacket(
                sendData, sendData.length, address, component.getUdpPort()
            );
            socket.send(sendPacket);
            
            // Aguarda a resposta
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            
            try {
                socket.receive(receivePacket);
                String response = new String(
                    receivePacket.getData(), 0, receivePacket.getLength()
                );
                
                // Verifica se a resposta é válida
                return "HEARTBEAT_ACK".equals(response);
            } catch (SocketTimeoutException e) {
                // Timeout aguardando resposta
                return false;
            }
        } catch (IOException e) {
            // LOGGER.log(Level.WARNING, "Erro ao verificar a saúde do componente", e);
            return false;
        }
    }
}