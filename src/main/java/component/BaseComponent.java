package component;

import common.model.ComponentInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe base para todos os componentes no sistema distribuído.
 * Fornece funcionalidades comuns, como:
 * - Registro no Gateway de API
 * - Respostas de heartbeat
 * - Manipuladores de protocolo para HTTP, TCP, UDP e gRPC
 */
public abstract class BaseComponent {
    private static final Logger LOGGER = Logger.getLogger(BaseComponent.class.getName());
    
    // Informações do componente
    protected final String componentType;
    protected final String host;
    protected final int httpPort;
    protected final int tcpPort;
    protected final int udpPort;
    protected final int grpcPort;
    
    // Informações do Gateway
    protected final String gatewayHost;
    protected final int gatewayRegistrationPort;
    
    // Estado
    protected boolean isRunning = false;
    
    // Pools de threads
    protected final ExecutorService threadPool;
    protected final ScheduledExecutorService scheduler;
    
    // Sockets do servidor
    protected ServerSocket httpServer;
    protected ServerSocket tcpServer;
    protected DatagramSocket udpServer;
    // Para o servidor gRPC, você usaria a classe Server do gRPC
    
    // Tamanho máximo do pacote UDP
    protected static final int MAX_UDP_PACKET_SIZE = 65507;
    
    /**
     * Construtor para o componente base.
     * 
     * @param componentType Tipo do componente (ex.: "componentA", "componentB")
     * @param host Endereço local do host
     * @param httpPort Porta para comunicação HTTP
     * @param tcpPort Porta para comunicação TCP
     * @param udpPort Porta para comunicação UDP
     * @param grpcPort Porta para comunicação gRPC
     * @param gatewayHost Endereço do host do Gateway de API
     * @param gatewayRegistrationPort Porta de registro do Gateway de API
     */
    public BaseComponent(String componentType, String host, int httpPort, int tcpPort, int udpPort, int grpcPort,
                         String gatewayHost, int gatewayRegistrationPort) {
        this.componentType = componentType;
        this.host = host;
        this.httpPort = httpPort;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.grpcPort = grpcPort;
        this.gatewayHost = gatewayHost;
        this.gatewayRegistrationPort = gatewayRegistrationPort;
        
        // Inicializa os pools de threads
        this.threadPool = Executors.newFixedThreadPool(20);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Inicia o componente e todos os seus serviços.
     */
    public void start() {
        if (isRunning) {
            LOGGER.warning(componentType + " já está em execução");
            return;
        }
        
        LOGGER.info("Iniciando " + componentType + "...");
        isRunning = true;
        
        try {
            // Inicia os manipuladores de protocolo
            startHTTPServer();
            startTCPServer();
            startUDPServer();
            // startGRPCServer(); // Seria implementado para uma solução completa
            
            // Registra no Gateway de API
            registerWithGateway();
            
            // Inicia o respondedor de heartbeat
            startHeartbeatResponder();
            
            LOGGER.info(componentType + " iniciado com sucesso");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha ao iniciar " + componentType, e);
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
        
        LOGGER.info("Parando " + componentType + "...");
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
            
            LOGGER.info(componentType + " parado");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao parar " + componentType, e);
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
                "REGISTER|%s|%s|%d|%d|%d|%d",
                componentType, host, httpPort, tcpPort, udpPort, grpcPort
            );
            
            writer.println(registrationMessage);
            
            // Lê a resposta
            String response = reader.readLine();
            
            if (response != null && response.startsWith("REGISTERED|SUCCESS")) {
                LOGGER.info(componentType + " registrado com sucesso no Gateway de API");
            } else {
                LOGGER.warning("Falha ao registrar no Gateway de API. Resposta: " + response);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao registrar no Gateway de API", e);
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
                        LOGGER.log(Level.SEVERE, "Erro ao aceitar conexão HTTP", e);
                    }
                }
            }
        });
        
        httpThread.setDaemon(true);
        httpThread.start();
        
        LOGGER.info(componentType + " servidor HTTP iniciado na porta " + httpPort);
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
                        LOGGER.log(Level.SEVERE, "Erro ao aceitar conexão TCP", e);
                    }
                }
            }
        });
        
        tcpThread.setDaemon(true);
        tcpThread.start();
        
        LOGGER.info(componentType + " servidor TCP iniciado na porta " + tcpPort);
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
                    String message = new String(data);
                    if ("HEARTBEAT".equals(message)) {
                        sendHeartbeatResponse(clientAddress, clientPort);
                    } else {
                        // Lida com a requisição regular
                        threadPool.submit(() -> handleUDPRequest(data, clientAddress, clientPort));
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        LOGGER.log(Level.SEVERE, "Erro ao receber pacote UDP", e);
                    }
                }
            }
        });
        
        udpThread.setDaemon(true);
        udpThread.start();
        
        LOGGER.info(componentType + " servidor UDP iniciado na porta " + udpPort);
    }
    
    /**
     * Inicia o respondedor de heartbeat.
     */
    protected void startHeartbeatResponder() {
        // Para UDP, lidamos com heartbeats no servidor UDP
        
        // Para heartbeats TCP, poderíamos iniciar um servidor dedicado
        scheduler.scheduleAtFixedRate(() -> {
            // Registra periodicamente para lidar com reinícios do gateway
            registerWithGateway();
        }, 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * Envia uma resposta de heartbeat via UDP.
     */
    protected void sendHeartbeatResponse(InetAddress address, int port) {
        try {
            byte[] responseData = "HEARTBEAT_ACK".getBytes();
            DatagramPacket response = new DatagramPacket(responseData, responseData.length, address, port);
            udpServer.send(response);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao enviar resposta de heartbeat", e);
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
        return new ComponentInfo(componentType, host, httpPort, tcpPort, udpPort, grpcPort);
    }
}