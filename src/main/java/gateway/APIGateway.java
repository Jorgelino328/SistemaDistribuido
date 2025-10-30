package gateway;

import common.model.ComponentInfo;
import common.config.SystemConfig;
import common.pattern.HeartbeatMonitor;
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class APIGateway {
    private final gateway.protocol.HTTPHandler httpHandler;
    private final gateway.protocol.TCPHandler tcpHandler;
    private final gateway.protocol.UDPHandler udpHandler;
    
    private final ComponentRegistry registry;
    
    private final HeartbeatMonitor heartbeatMonitor;
    
    private final int httpPort;
    private final int tcpPort;
    private final int udpPort;
    private final int registrationPort;
    
    private boolean isRunning = false;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public APIGateway() {
        SystemConfig config = SystemConfig.getInstance();
        this.httpPort = config.getHttpPort();
        this.tcpPort = config.getTcpPort();
        this.udpPort = config.getUdpPort();
        this.registrationPort = config.getRegistrationPort();
        
        this.registry = new ComponentRegistry();
        
        this.heartbeatMonitor = new HeartbeatMonitor(registry, 
                                                    config.getHeartbeatInterval(),
                                                    config.getHeartbeatTimeout());
        
        this.httpHandler = new gateway.protocol.HTTPHandler(this, httpPort);
        this.tcpHandler = new gateway.protocol.TCPHandler(this, tcpPort);
        this.udpHandler = new gateway.protocol.UDPHandler(this, udpPort);
    }

    public void start() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        
        System.out.println("API GATEWAY started (HTTP:" + httpPort + " TCP:" + tcpPort + " UDP:" + udpPort + " Registration:" + registrationPort + ")");
        
        startRegistrationServer();
        
        httpHandler.start();
        tcpHandler.start();
        udpHandler.start();
        
        heartbeatMonitor.start();
    }
    
    private void startRegistrationServer() {
        Thread registrationThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(registrationPort)) {
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new Thread(() -> registry.handleRegistration(clientSocket)).start();
                    } catch (IOException e) {
                    }
                }
            } catch (IOException e) {
            }
        });
        registrationThread.setDaemon(true);
        registrationThread.start();
    }

    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        httpHandler.stop();
        tcpHandler.stop();
        udpHandler.stop();
        
        heartbeatMonitor.stop();
        
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
    

    public byte[] routeRequest(String componentType, byte[] request, String protocol) {
        List<ComponentInfo> availableComponents = registry.getAvailableComponents(componentType);
        
        if (availableComponents.isEmpty()) {
            return "Nenhum componente disponível".getBytes();
        }
        
        String key = extractKeyFromRequest(new String(request));
        ComponentInfo selected;
        
        if (key != null) {
            selected = registry.selectComponentByKey(componentType, key);
        } else {
            selected = registry.selectComponent(componentType);
        }
        
        if (selected == null) {
            return "Falha na seleção do componente".getBytes();
        }
        
        
        try {
            switch (protocol.toLowerCase()) {
                case "http":
                    return httpHandler.forwardRequest(selected, request);
                case "tcp":
                    return tcpHandler.forwardRequest(selected, request);
                case "udp":
                    return udpHandler.forwardRequest(selected, request);
                default:
                    return "Protocolo não suportado".getBytes();
            }
        } catch (Exception e) {
            
            registry.markComponentSuspect(selected);
            
            return ("Erro ao encaminhar requisição: " + e.getMessage()).getBytes();
        }
    }
    
    private String extractKeyFromRequest(String request) {
        if (request == null || request.isEmpty()) {
            return null;
        }
        
        String[] parts = request.split("\\|");
        if (parts.length < 2) {
            return null;
        }
        
        String action = parts[0].toUpperCase();
        
        switch (action) {
            case "USER_CREATE":
            case "USER_GET":
            case "AUTH_LOGIN":
            case "FILE_STORE":
            case "FILE_RETRIEVE":
            case "STORE":
            case "RETRIEVE":
                return parts.length >= 2 ? parts[1] : null;
            // UDP user-service actions
            case "CREATE":
                // Format: CREATE|username|email
                return parts.length >= 2 ? parts[1] : null;
            case "GET":
                // Format: GET|user|username
                if (parts.length >= 3 && "user".equalsIgnoreCase(parts[1])) {
                    return parts[2];
                }
                return null;
            default:
                return null;
        }
    }
    

    public ComponentRegistry getRegistry() {
        return registry;
    }
    
    public static void main(String[] args) {
        APIGateway gateway = new APIGateway();
        gateway.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::stop));
    }
}
