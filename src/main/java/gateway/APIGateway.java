package gateway;

import common.model.ComponentInfo;
import common.config.SystemConfig;
import common.pattern.HeartbeatMonitor;
import java.util.List;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Gateway de API para gerenciar comunicação entre componentes distribuídos.
 * 
 * O Gateway de API gerencia o registro de componentes, encaminha requisições
 * para os componentes apropriados e monitora a saúde dos componentes.
 */
public class APIGateway {
    private static final Logger LOGGER = Logger.getLogger(APIGateway.class.getName());
    
    // Manipuladores de protocolo
    private final gateway.protocol.HTTPHandler httpHandler;
    private final gateway.protocol.TCPHandler tcpHandler;
    private final gateway.protocol.UDPHandler udpHandler;
    
    // Registro de componentes
    private final ComponentRegistry registry;
    
    // Monitor de heartbeat
    private final HeartbeatMonitor heartbeatMonitor;
    
    // Configuração
    private final int httpPort;
    private final int tcpPort;
    private final int udpPort;
    private final int registrationPort;
    
    private boolean isRunning = false;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * Construtor do Gateway de API.
     */
    public APIGateway() {
        // Carrega a configuração
        SystemConfig config = SystemConfig.getInstance();
        this.httpPort = config.getHttpPort();
        this.tcpPort = config.getTcpPort();
        this.udpPort = config.getUdpPort();
        this.registrationPort = config.getRegistrationPort();
        
        // Inicializa os componentes
        this.registry = new ComponentRegistry();
        
        // Inicializa o monitor de heartbeat
        SystemConfig config2 = SystemConfig.getInstance();
        this.heartbeatMonitor = new HeartbeatMonitor(registry, 
                                                    config2.getHeartbeatInterval(),
                                                    config2.getHeartbeatTimeout());
        
        // Inicializa os manipuladores de protocolo
        this.httpHandler = new gateway.protocol.HTTPHandler(this, httpPort);
        this.tcpHandler = new gateway.protocol.TCPHandler(this, tcpPort);
        this.udpHandler = new gateway.protocol.UDPHandler(this, udpPort);
    }

    /**
     * Inicia o Gateway de API.
     */
    public void start() {
        if (isRunning) {
            // LOGGER.warning("O Gateway de API já está em execução");
            return;
        }
        
        // LOGGER.info("Iniciando o Gateway de API...");
        isRunning = true;
        
        // Inicia o servidor de registro de componentes
        startRegistrationServer();
        
        // Inicia os manipuladores de protocolo
        httpHandler.start();
        tcpHandler.start();
        udpHandler.start();
        
        // Inicia o monitor de heartbeat
        heartbeatMonitor.start();
        
        // LOGGER.info("Gateway de API iniciado com sucesso");
        // LOGGER.info("Aguardando registros de componentes na porta " + registrationPort);
        // LOGGER.info("Servidor HTTP iniciado na porta " + httpPort);
        // LOGGER.info("Servidor TCP iniciado na porta " + tcpPort);
        // LOGGER.info("Servidor UDP iniciado na porta " + udpPort);
    }
    
    /**
     * Inicia o servidor de registro de componentes.
     */
    private void startRegistrationServer() {
        Thread registrationThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(registrationPort)) {
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        // Lida com o registro em uma thread separada
                        new Thread(() -> registry.handleRegistration(clientSocket)).start();
                    } catch (IOException e) {
                        if (isRunning) {
                            // LOGGER.log(Level.SEVERE, "Erro ao aceitar conexão", e);
                        }
                    }
                }
            } catch (IOException e) {
                // LOGGER.log(Level.SEVERE, "Erro ao iniciar o servidor de registro", e);
            }
        });
        registrationThread.setDaemon(true);
        registrationThread.start();
    }

    /**
     * Para o Gateway de API.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        // LOGGER.info("Parando o Gateway de API...");
        isRunning = false;
        
        // Para todos os serviços
        httpHandler.stop();
        tcpHandler.stop();
        udpHandler.stop();
        
        // Para o monitor de heartbeat
        heartbeatMonitor.stop();
        
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
        
        // LOGGER.info("Gateway de API parado");
    }
    
    /**
     * Encaminha uma requisição para os componentes registrados.
     * 
     * @param componentType Tipo do componente de destino
     * @param request Requisição em formato de bytes
     * @param protocol Protocolo usado (http, tcp, udp)
     * @return Resposta do componente em formato de bytes
     */
    public byte[] routeRequest(String componentType, byte[] request, String protocol) {
        List<ComponentInfo> availableComponents = registry.getAvailableComponents(componentType);
        
        if (availableComponents.isEmpty()) {
            // LOGGER.warning("Nenhum componente " + componentType + " disponível para requisição " + protocol);
            return "Nenhum componente disponível".getBytes();
        }
        
        // Balanceamento de carga simples (round-robin)
        ComponentInfo selected = registry.selectComponent(componentType);
        
        if (selected == null) {
            // LOGGER.warning("Falha ao selecionar um componente " + componentType);
            return "Falha na seleção do componente".getBytes();
        }
        
        // LOGGER.info("Encaminhando requisição " + protocol + " para " + componentType + 
        //            " em " + selected.getHost() + ":" + selected.getPortForProtocol(protocol));
        
        // Encaminha a requisição com base no protocolo
        try {
            switch (protocol.toLowerCase()) {
                case "http":
                    return httpHandler.forwardRequest(selected, request);
                case "tcp":
                    return tcpHandler.forwardRequest(selected, request);
                case "udp":
                    return udpHandler.forwardRequest(selected, request);
                default:
                    // LOGGER.warning("Protocolo não suportado: " + protocol);
                    return "Protocolo não suportado".getBytes();
            }
        } catch (Exception e) {
            // LOGGER.log(Level.SEVERE, "Erro ao encaminhar requisição para o componente", e);
            
            // Marca o componente como possivelmente falho
            registry.markComponentSuspect(selected);
            
            return ("Erro ao encaminhar requisição: " + e.getMessage()).getBytes();
        }
    }
    
    /**
     * Obtém o registro de componentes.
     * 
     * @return Registro de componentes
     */
    public ComponentRegistry getRegistry() {
        return registry;
    }
    
    /**
     * Método principal para executar o Gateway de API.
     */
    public static void main(String[] args) {
        APIGateway gateway = new APIGateway();
        gateway.start();
        
        // Adiciona um hook para desligamento
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::stop));
    }
}
