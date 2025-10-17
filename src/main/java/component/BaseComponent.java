package component;

import common.model.ComponentInfo;
import common.pattern.KeyRangePartition;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Classe base para todos os componentes no sistema distribuído.
 * Fornece funcionalidades comuns, como:
 * - Registro no Gateway de API
 * - Particionamento por faixa de chaves
 * - Manipuladores de protocolo para HTTP, TCP e UDP 
 */
public abstract class BaseComponent {
    
    // Informações do componente
    protected final String componentType;
    protected final String host;
    protected final int httpPort;
    protected final int tcpPort;
    protected final int udpPort;
    
    // Informações do Gateway
    protected final String gatewayHost;
    protected final int gatewayRegistrationPort;
    
    // Estado
    protected boolean isRunning = false;
    
    // Identificador único da instância
    protected final String instanceId;
    
    // Padrões de resiliência
    protected KeyRangePartition keyRangePartition;
    
    // Pools de threads
    protected final ExecutorService threadPool;
    protected final ScheduledExecutorService scheduler;
    
    // Sockets do servidor
    protected ServerSocket httpServer;
    protected ServerSocket tcpServer;
    protected DatagramSocket udpServer;
    
    // Tamanho máximo do pacote UDP
    protected static final int MAX_UDP_PACKET_SIZE = 65507;
    
    /**
     * Construtor para o componente base.
     * 
     * @param componentType Tipo do componente (ex.: "userservice", "messageservice")
     * @param host Endereço local do host
     * @param httpPort Porta para comunicação HTTP
     * @param tcpPort Porta para comunicação TCP
     * @param udpPort Porta para comunicação UDP
     * @param gatewayHost Endereço do host do Gateway de API
     * @param gatewayRegistrationPort Porta de registro do Gateway de API
     */
    public BaseComponent(String componentType, String host, int httpPort, int tcpPort, int udpPort,
                         String gatewayHost, int gatewayRegistrationPort) {
        this.componentType = componentType;
        this.host = host;
        this.httpPort = httpPort;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.gatewayHost = gatewayHost;
        this.gatewayRegistrationPort = gatewayRegistrationPort;
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        
        // Inicializa os pools de threads
        this.threadPool = Executors.newFixedThreadPool(20);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Inicia o componente e todos os seus serviços.
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        // LOGGER.info("Iniciando " + componentType + " " + instanceId + "...");
        isRunning = true;
        
        try {
            // Inicia os manipuladores de protocolo
            startHTTPServer();
            startTCPServer();
            startUDPServer();
            
            // Inicia o particionamento por faixa de chaves
            initKeyRangePartition();
            
            // Registra no Gateway de API
            registerWithGateway();
            
            // LOGGER.info(componentType + " " + instanceId + " iniciado com sucesso");
        } catch (Exception e) {
            // LOGGER.log(Level.SEVERE, "Falha ao iniciar " + componentType, e);
            stop();
        }
    }
    
    /**
     * Para o componente e todos os seus serviços.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        // LOGGER.info("Parando " + componentType + " " + instanceId + "...");
        isRunning = false;
        
        try {
            // Fecha os sockets do servidor
            if (httpServer != null && !httpServer.isClosed()) {
                httpServer.close();
            }
            
            if (tcpServer != null && !tcpServer.isClosed()) {
                tcpServer.close();
            }
            
            if (udpServer != null && !udpServer.isClosed()) {
                udpServer.close();
            }
            
            // Para os padrões
            if (keyRangePartition != null) {
                keyRangePartition.stop();
            }
            
            // Para os pools de threads
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
            
            // LOGGER.info(componentType + " " + instanceId + " parado com sucesso");
        } catch (IOException e) {
            // LOGGER.log(Level.SEVERE, "Erro ao parar " + componentType, e);
        }
    }
    
    /**
     * Registra este componente no Gateway de API.
     */
    protected void registerWithGateway() {
        try (
            Socket socket = new Socket(gatewayHost, gatewayRegistrationPort);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Envia a mensagem de registro
            String registrationMessage = String.format(
                "REGISTER|%s|%s|%d|%d|%d",
                componentType, host, httpPort, tcpPort, udpPort
            );
            
            writer.println(registrationMessage);
            
            // Lê a resposta
            String response = reader.readLine();
            
            if (response != null && response.startsWith("REGISTERED|SUCCESS")) {
                // LOGGER.info(componentType + " registrado com sucesso no Gateway de API");
            } else {
                // LOGGER.warning("Falha ao registrar no Gateway de API. Resposta: " + response);
            }
        } catch (IOException e) {
            // LOGGER.log(Level.SEVERE, "Erro ao registrar no Gateway de API", e);
        }
    }
    
    /**
     * Inicia o servidor HTTP para lidar com requisições HTTP.
     */
    protected void startHTTPServer() throws IOException {
        httpServer = new ServerSocket(httpPort);
        
        Thread httpThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = httpServer.accept();
                    threadPool.submit(() -> handleHTTPRequest(clientSocket));
                } catch (IOException e) {
                    if (isRunning) {
                        // LOGGER.log(Level.SEVERE, "Erro ao aceitar conexão HTTP", e);
                    }
                }
            }
        });
        
        httpThread.setDaemon(true);
        httpThread.start();
        
        // LOGGER.info(componentType + " servidor HTTP iniciado na porta " + httpPort);
    }
    
    /**
     * Inicia o servidor TCP para lidar com requisições TCP.
     */
    protected void startTCPServer() throws IOException {
        tcpServer = new ServerSocket(tcpPort);
        
        Thread tcpThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = tcpServer.accept();
                    threadPool.submit(() -> handleTCPRequest(clientSocket));
                } catch (IOException e) {
                    if (isRunning) {
                        // LOGGER.log(Level.SEVERE, "Erro ao aceitar conexão TCP", e);
                    }
                }
            }
        });
        
        tcpThread.setDaemon(true);
        tcpThread.start();
        
        // LOGGER.info(componentType + " servidor TCP iniciado na porta " + tcpPort);
    }
    
    /**
     * Inicia o servidor UDP para lidar com requisições UDP.
     */
    protected void startUDPServer() throws IOException {
        udpServer = new DatagramSocket(udpPort);
        
        Thread udpThread = new Thread(() -> {
            byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
            
            while (isRunning) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpServer.receive(packet);
                    
                    // Faz uma cópia dos dados
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
                    
                    // Obtém o endereço e a porta do cliente
                    InetAddress clientAddress = packet.getAddress();
                    int clientPort = packet.getPort();
                    
                    // Verifica se é uma mensagem de heartbeat
                    String message = new String(data, StandardCharsets.UTF_8).replaceAll("\0", "");
                    if ("HEARTBEAT".equals(message)) {
                        sendHeartbeatResponse(clientAddress, clientPort);
                    } else {
                        // Lida com a requisição regular
                        threadPool.submit(() -> handleUDPRequest(data, clientAddress, clientPort));
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        // LOGGER.log(Level.SEVERE, "Erro ao receber pacote UDP", e);
                    }
                }
            }
        });
        
        udpThread.setDaemon(true);
        udpThread.start();
        
        // LOGGER.info(componentType + " servidor UDP iniciado na porta " + udpPort);
    }
    
    /**
     * Inicia o respondedor de heartbeat.
     */
    protected void startHeartbeatResponder() {
        // Para UDP, lidamos com heartbeats no servidor UDP
        
        scheduler.scheduleAtFixedRate(() -> {
            // Registra periodicamente para lidar com reinícios do gateway
            registerWithGateway();
        }, 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * Inicializa o sistema de particionamento por faixa de chaves.
     */
    protected void initKeyRangePartition() {
        keyRangePartition = new KeyRangePartition(instanceId, componentType);
        
        // Configura callbacks
        keyRangePartition.onRangeAssigned(this::handleRangeAssignment)
                        .onTopologyChange(this::handleTopologyChange)
                        .onDataMigration(this::handleDataMigration);
        
        keyRangePartition.start();
        
        // LOGGER.info("Key-Range Partition inicializado para " + componentType + " " + instanceId);
    }
    
    /**
     * Manipula atribuição de uma nova faixa de chaves.
     * 
     * @param range Faixa de chaves atribuída a este nó
     */
    protected void handleRangeAssignment(KeyRangePartition.PartitionRange range) {
        // LOGGER.info(componentType + " " + instanceId + " recebeu nova faixa: " + range);
        onRangeAssigned(range);
    }
    
    /**
     * Manipula mudanças na topologia do cluster.
     * 
     * @param nodes Lista de todos os nós conhecidos
     */
    protected void handleTopologyChange(java.util.List<ComponentInfo> nodes) {
        // LOGGER.info(componentType + " " + instanceId + " detectou mudança na topologia. Nós: " + nodes.size());
        onTopologyChange(nodes);
    }
    
    /**
     * Manipula migração de dados.
     * 
     * @param migrationInfo Informações sobre a migração
     */
    protected void handleDataMigration(String migrationInfo) {
        // LOGGER.info(componentType + " " + instanceId + " iniciando migração: " + migrationInfo);
        onDataMigration(migrationInfo);
    }
    
    /**
     * Método chamado quando uma nova faixa de chaves é atribuída a este nó.
     * Deve ser implementado pelas subclasses.
     * 
     * @param range Faixa de chaves atribuída
     */
    protected abstract void onRangeAssigned(KeyRangePartition.PartitionRange range);
    
    /**
     * Método chamado quando há mudanças na topologia do cluster.
     * Deve ser implementado pelas subclasses.
     * 
     * @param nodes Lista de todos os nós conhecidos
     */
    protected abstract void onTopologyChange(java.util.List<ComponentInfo> nodes);
    
    /**
     * Método chamado quando é necessário migrar dados.
     * Deve ser implementado pelas subclasses.
     * 
     * @param migrationInfo Informações sobre a migração
     */
    protected abstract void onDataMigration(String migrationInfo);
    
    /**
     * Verifica se este nó é responsável por uma chave específica.
     * 
     * @param key Chave a ser verificada
     * @return true se este nó for responsável pela chave
     */
    protected boolean isResponsibleFor(String key) {
        return keyRangePartition != null && keyRangePartition.isResponsibleFor(key);
    }
    
    /**
     * Obtém o nó responsável por uma chave específica.
     * 
     * @param key Chave a ser verificada
     * @return Informações do nó responsável pela chave
     */
    protected ComponentInfo getResponsibleNode(String key) {
        return keyRangePartition != null ? keyRangePartition.getResponsibleNode(key) : null;
    }
    
    /**
     * Envia uma resposta de heartbeat via UDP.
     */
    protected void sendHeartbeatResponse(InetAddress address, int port) {
        try {
            byte[] responseData = "HEARTBEAT_ACK".getBytes(StandardCharsets.UTF_8);
            DatagramPacket response = new DatagramPacket(responseData, responseData.length, address, port);
            udpServer.send(response);
        } catch (IOException e) {
            // LOGGER.log(Level.WARNING, "Erro ao enviar resposta de heartbeat", e);
        }
    }
    
    /**
     * Lida com uma requisição HTTP.
     * Este método deve ser implementado pelas subclasses para fornecer manipulação específica do componente.
     */
    protected abstract void handleHTTPRequest(Socket clientSocket);
    
    /**
     * Lida com uma requisição TCP.
     * Este método deve ser implementado pelas subclasses para fornecer manipulação específica do componente.
     */
    protected abstract void handleTCPRequest(Socket clientSocket);
    
    /**
     * Lida com uma requisição UDP.
     * Este método deve ser implementado pelas subclasses para fornecer manipulação específica do componente.
     */
    protected abstract void handleUDPRequest(byte[] data, InetAddress clientAddress, int clientPort);
    
    /**
     * Obtém o objeto de informações do componente.
     */
    protected ComponentInfo getComponentInfo() {
        return new ComponentInfo(componentType, instanceId, host, httpPort, tcpPort, udpPort);
    }
}
