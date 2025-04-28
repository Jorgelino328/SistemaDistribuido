package common.pattern;

import common.model.ComponentInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementação do padrão Líder-Seguidor para sistemas distribuídos.
 * Fornece mecanismos de eleição de líder e replicação de estado.
 */
public class LeaderFollower {
    private static final Logger LOGGER = Logger.getLogger(LeaderFollower.class.getName());
    
    // Informações do componente
    private final String componentType;
    private final String componentId;
    private final String host;
    private final int port;
    
    // Propriedades de eleição de líder
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private final AtomicInteger term = new AtomicInteger(0);
    private String leaderId;
    private String leaderHost;
    private int leaderPort;
    
    // Gerenciamento de seguidores (para o líder)
    private final List<ComponentInfo> followers = Collections.synchronizedList(new ArrayList<>());
    
    // Callbacks
    private Consumer<Boolean> onLeadershipChanged;
    private Consumer<String> onStateUpdate;
    
    // Comunicação
    private ServerSocket serverSocket;
    
    // Agendador
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private boolean running = false;
    
    // Replicação de estado
    private String currentState = "";
    private final int stateReplicationIntervalMs = 1000;
    private final AtomicInteger stateVersion = new AtomicInteger(0);
    
    /**
     * Construtor para o padrão Líder-Seguidor.
     * 
     * @param componentType Tipo deste componente
     * @param componentId ID único deste componente
     * @param host Endereço do host deste componente
     * @param port Porta para comunicação líder-seguidor
     */
    public LeaderFollower(String componentType, String componentId, String host, int port) {
        this.componentType = componentType;
        this.componentId = componentId;
        this.host = host;
        this.port = port;
    }
    
    public LeaderFollower onLeadershipChanged(Consumer<Boolean> onLeadershipChanged) {
        this.onLeadershipChanged = onLeadershipChanged;
        return this;
    }
    
    public LeaderFollower onStateUpdate(Consumer<String> onStateUpdate) {
        this.onStateUpdate = onStateUpdate;
        return this;
    }
    
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        
        try {
            serverSocket = new ServerSocket(port);
            startMessageHandler();
            
            if (leaderHost != null && leaderPort > 0) {
                joinCluster();
            } else {
                becomeLeader();
            }
            
            LOGGER.info(componentType + " iniciou o padrão Líder-Seguidor na porta " + port);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Falha ao criar o socket líder-seguidor", e);
            stop();
        }
    }
    
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Erro ao fechar o socket do servidor", e);
            }
        }
        
        LOGGER.info(componentType + " parou o padrão Líder-Seguidor");
    }
    
    public LeaderFollower setLeader(String leaderId, String leaderHost, int leaderPort) {
        this.leaderId = leaderId;
        this.leaderHost = leaderHost;
        this.leaderPort = leaderPort;
        return this;
    }
    
    public boolean isLeader() {
        return isLeader.get();
    }
    
    public int getTerm() {
        return term.get();
    }
    
    public String getLeaderId() {
        return leaderId;
    }
    
    public String getCurrentState() {
        return currentState;
    }
    
    public void updateState(String newState) {
        if (!isLeader.get()) {
            throw new IllegalStateException("Somente o líder pode atualizar o estado");
        }
        
        currentState = newState;
        stateVersion.incrementAndGet();
        replicateState();
    }
    
    private void startMessageHandler() {
        Thread handlerThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleConnection(clientSocket);
                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.WARNING, "Erro ao aceitar conexão", e);
                    }
                }
            }
        });
        
        handlerThread.setDaemon(true);
        handlerThread.start();
    }
    
    private void handleConnection(Socket socket) {
        new Thread(() -> {
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String message = reader.readLine();
                
                if (message != null) {
                    String[] parts = message.split("\\|");
                    String command = parts[0];
                    
                    switch (command) {
                        case "JOIN":
                            handleJoinRequest(parts, writer);
                            break;
                        case "STATE":
                            handleStateUpdate(parts);
                            break;
                        case "ELECTION":
                            handleElectionMessage(parts, writer);
                            break;
                        default:
                            writer.println("ERROR|Comando desconhecido: " + command);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Erro ao manipular conexão", e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Erro ao fechar o socket do cliente", e);
                }
            }
        }).start();
    }
    
    private void handleJoinRequest(String[] parts, PrintWriter writer) {
        if (!isLeader.get()) {
            writer.println("REDIRECT|" + leaderId + "|" + leaderHost + "|" + leaderPort);
            return;
        }
        
        if (parts.length >= 4) {
            String followerId = parts[1];
            String followerHost = parts[2];
            int followerPort = Integer.parseInt(parts[3]);
            
            ComponentInfo follower = new ComponentInfo(
                componentType, followerHost, Integer.parseInt(followerId), 0, followerPort
            );
            
            followers.add(follower);
            writer.println("WELCOME|" + term.get() + "|" + stateVersion.get() + "|" + currentState);
            
            LOGGER.info("Seguidor entrou: " + followerId + " em " + followerHost + ":" + followerPort);
        } else {
            writer.println("ERROR|Formato inválido de mensagem JOIN");
        }
    }

    private void handleStateUpdate(String[] parts) {
        if (isLeader.get()) {
            return;
        }
        
        if (parts.length >= 4) {
            int messageTerm = Integer.parseInt(parts[1]);
            int messageStateVersion = Integer.parseInt(parts[2]);
            String state = parts[3];
            
            if (messageTerm >= term.get()) {
                term.set(messageTerm);
                
                if (messageStateVersion > stateVersion.get()) {
                    stateVersion.set(messageStateVersion);
                    currentState = state;
                    
                    if (onStateUpdate != null) {
                        onStateUpdate.accept(currentState);
                    }
                }
            }
        }
    }
    
    private void handleElectionMessage(String[] parts, PrintWriter writer) {
        if (parts.length >= 3) {
            int electionTerm = Integer.parseInt(parts[1]);
            String candidateId = parts[2];
            
            if (electionTerm > term.get()) {
                term.set(electionTerm);
                leaderId = candidateId;
                isLeader.set(false);
                
                if (onLeadershipChanged != null) {
                    onLeadershipChanged.accept(false);
                }
                
                writer.println("VOTE|" + electionTerm + "|" + componentId + "|YES");
                LOGGER.info("Votou em " + candidateId + " no termo " + electionTerm);
            } else {
                writer.println("VOTE|" + term.get() + "|" + componentId + "|NO");
            }
        }
    }
    
    private void joinCluster() {
        try (
            Socket socket = new Socket(leaderHost, leaderPort);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            writer.println("JOIN|" + componentId + "|" + host + "|" + port);
            String response = reader.readLine();
            
            if (response != null) {
                String[] parts = response.split("\\|");
                String command = parts[0];
                
                if ("WELCOME".equals(command) && parts.length >= 4) {
                    int newTerm = Integer.parseInt(parts[1]);
                    int newStateVersion = Integer.parseInt(parts[2]);
                    String newState = parts[3];
                    
                    term.set(newTerm);
                    stateVersion.set(newStateVersion);
                    currentState = newState;
                    isLeader.set(false);
                    
                    if (onLeadershipChanged != null) {
                        onLeadershipChanged.accept(false);
                    }
                    
                    if (onStateUpdate != null) {
                        onStateUpdate.accept(currentState);
                    }
                    
                    LOGGER.info("Entrou no cluster como seguidor. Líder: " + leaderId);
                } else if ("REDIRECT".equals(command) && parts.length >= 4) {
                    leaderId = parts[1];
                    leaderHost = parts[2];
                    leaderPort = Integer.parseInt(parts[3]);
                    joinCluster();
                } else {
                    LOGGER.warning("Falha ao entrar no cluster. Resposta: " + response);
                    startElection();
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao entrar no cluster", e);
            startElection();
        }
    }
    
    private void startElection() {
        int newTerm = term.incrementAndGet();
        LOGGER.info("Iniciando eleição para o termo " + newTerm);
        becomeLeader();
    }
    
    private void becomeLeader() {
        isLeader.set(true);
        leaderId = componentId;
        
        scheduler.scheduleAtFixedRate(
            this::replicateState,
            stateReplicationIntervalMs,
            stateReplicationIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        if (onLeadershipChanged != null) {
            onLeadershipChanged.accept(true);
        }
        
        LOGGER.info("Tornou-se líder para o termo " + term.get());
    }
    
    private void replicateState() {
        if (!isLeader.get() || followers.isEmpty()) {
            return;
        }
        
        String stateMessage = "STATE|" + term.get() + "|" + stateVersion.get() + "|" + currentState;
        
        synchronized (followers) {
            List<ComponentInfo> deadFollowers = new ArrayList<>();
            
            for (ComponentInfo follower : followers) {
                try (
                    Socket socket = new Socket(follower.getHost(), follower.getUdpPort());
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
                ) {
                    writer.println(stateMessage);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Falha ao replicar estado para seguidor em " + 
                              follower.getHost() + ":" + follower.getUdpPort(), e);
                    deadFollowers.add(follower);
                }
            }
            
            followers.removeAll(deadFollowers);
        }
    }
    
    public static LeaderFollower createLeader(String componentType, String componentId, 
                                             String host, int port) {
        return new LeaderFollower(componentType, componentId, host, port);
    }
    
    public static LeaderFollower createFollower(String componentType, String componentId, 
                                              String host, int port, String leaderId,
                                              String leaderHost, int leaderPort) {
        return new LeaderFollower(componentType, componentId, host, port)
            .setLeader(leaderId, leaderHost, leaderPort);
    }
}
