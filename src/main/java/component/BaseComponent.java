package component;

import common.model.ComponentInfo;
import common.pattern.KeyRangePartition;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public abstract class BaseComponent {
    
    protected final String componentType;
    protected final String host;
    protected final int httpPort;
    protected final int tcpPort;
    protected final int udpPort;
    
    protected final String gatewayHost;
    protected final int gatewayRegistrationPort;
    
    protected boolean isRunning = false;
    
    protected final String instanceId;
    
    protected KeyRangePartition keyRangePartition;
    
    protected final ExecutorService threadPool;
    protected final ScheduledExecutorService scheduler;
    
    protected ServerSocket httpServer;
    protected ServerSocket tcpServer;
    protected DatagramSocket udpServer;
    
    protected static final int MAX_UDP_PACKET_SIZE = 65507;
    
    public BaseComponent(String componentType, String host, int httpPort, int tcpPort, int udpPort,
                         String gatewayHost, int gatewayRegistrationPort) {
        this.componentType = componentType;
        this.host = host;
        this.httpPort = httpPort;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.gatewayHost = gatewayHost;
        this.gatewayRegistrationPort = gatewayRegistrationPort;
        this.instanceId = componentType + "_" + host + "_" + httpPort;
        
        this.threadPool = Executors.newFixedThreadPool(20);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    public void start() {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        
        try {
            startHTTPServer();
            startTCPServer();
            startUDPServer();
            
            System.out.println(componentType.toUpperCase() + " started: " + instanceId + " (HTTP:" + httpPort + " TCP:" + tcpPort + " UDP:" + udpPort + ")");
            
            initKeyRangePartition();
            
            registerWithGateway();
            
            startPeriodicRegistration();
            
        } catch (Exception e) {
            stop();
        }
    }
    
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        try {
            if (httpServer != null && !httpServer.isClosed()) {
                httpServer.close();
            }
            
            if (tcpServer != null && !tcpServer.isClosed()) {
                tcpServer.close();
            }
            
            if (udpServer != null && !udpServer.isClosed()) {
                udpServer.close();
            }
            
            if (keyRangePartition != null) {
                keyRangePartition.stop();
            }
            
            threadPool.shutdown();
            scheduler.shutdown();
            
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
                
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
        } catch (IOException e) {
        }
    }
    
    protected void registerWithGateway() {
        try (
            Socket socket = new Socket(gatewayHost, gatewayRegistrationPort);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            String keyRangeInfo = "";
            if (keyRangePartition != null && keyRangePartition.getMyRange() != null) {
                common.pattern.KeyRangePartition.PartitionRange range = keyRangePartition.getMyRange();
                String startKey = range.getStartKey() != null ? range.getStartKey() : "null";
                String endKey = range.getEndKey() != null ? range.getEndKey() : "null";
                keyRangeInfo = "|" + startKey + "|" + endKey;
            }
            
            String registrationMessage = String.format(
                "REGISTER|%s|%s|%d|%d|%d%s",
                componentType, host, httpPort, tcpPort, udpPort, keyRangeInfo
            );
            
            writer.println(registrationMessage);
            writer.flush();
            reader.readLine();
            
        } catch (IOException e) {
        }
    }
    
    protected void startHTTPServer() throws IOException {
        httpServer = new ServerSocket(httpPort);
        
        Thread httpThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = httpServer.accept();
                    threadPool.submit(() -> handleHTTPRequest(clientSocket));
                } catch (IOException e) {
                }
            }
        });
        
        httpThread.setDaemon(true);
        httpThread.start();
    }
    
    protected void startTCPServer() throws IOException {
        tcpServer = new ServerSocket(tcpPort);
        
        Thread tcpThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = tcpServer.accept();
                    threadPool.submit(() -> handleIncomingTCPRequest(clientSocket));
                } catch (IOException e) {
                }
            }
        });
        
        tcpThread.setDaemon(true);
        tcpThread.start();
    }
    
    private void handleIncomingTCPRequest(Socket clientSocket) {
        handleTCPRequest(clientSocket);
    }
    
    protected void startUDPServer() throws IOException {
        udpServer = new DatagramSocket(udpPort);
        
        Thread udpThread = new Thread(() -> {
            byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
            
            while (isRunning) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpServer.receive(packet);
                    
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                    
                    InetAddress clientAddress = packet.getAddress();
                    int clientPort = packet.getPort();
                    
                    threadPool.submit(() -> handleUDPRequest(data, clientAddress, clientPort));
                } catch (IOException e) {
                    if (isRunning) {
                    }
                }
            }
        });
        
        udpThread.setDaemon(true);
        udpThread.start();
        
    }
    
    protected void startPeriodicRegistration() {
        scheduler.scheduleAtFixedRate(() -> {
            registerWithGateway();
        }, 60, 60, TimeUnit.SECONDS);
    }
    

    protected void initKeyRangePartition() {
        keyRangePartition = new KeyRangePartition(instanceId, componentType, gatewayHost, gatewayRegistrationPort);
        
        keyRangePartition.onRangeAssigned(this::handleRangeAssignment)
                        .onTopologyChange(this::handleTopologyChange)
                        .onDataMigration(this::handleDataMigration);
        
        keyRangePartition.start();
    }
    

    protected void handleRangeAssignment(KeyRangePartition.PartitionRange range) {
        onRangeAssigned(range);
        registerWithGateway();
    }
  
    protected void handleTopologyChange(java.util.List<ComponentInfo> nodes) {
        onTopologyChange(nodes);
    }
    
    protected void handleDataMigration(String migrationInfo) {
        onDataMigration(migrationInfo);
    }
    
    protected abstract void onRangeAssigned(KeyRangePartition.PartitionRange range);
    
    protected abstract void onTopologyChange(java.util.List<ComponentInfo> nodes);
    
    protected abstract void onDataMigration(String migrationInfo);
    
    protected boolean isResponsibleFor(String key) {
        return keyRangePartition != null && keyRangePartition.isResponsibleFor(key);
    }
    
    protected ComponentInfo getResponsibleNode(String key) {
        return keyRangePartition != null ? keyRangePartition.getResponsibleNode(key) : null;
    }
    


    protected void sendHeartbeatResponse(InetAddress address, int port) {
        try {
            byte[] responseData = "HEARTBEAT_ACK".getBytes(StandardCharsets.UTF_8);
            DatagramPacket response = new DatagramPacket(responseData, responseData.length, address, port);
            udpServer.send(response);
        } catch (IOException e) {
        }
    }
    
    protected abstract void handleHTTPRequest(Socket clientSocket);
    
    protected abstract void handleTCPRequest(Socket clientSocket);
    
    protected abstract void handleUDPRequest(byte[] data, InetAddress clientAddress, int clientPort);
    
    protected ComponentInfo getComponentInfo() {
        return new ComponentInfo(componentType, instanceId, host, httpPort, tcpPort, udpPort);
    }
}
