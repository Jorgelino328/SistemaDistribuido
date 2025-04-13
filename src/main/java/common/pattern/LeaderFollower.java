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
    
    /**
     * Define o callback para quando o status de liderança mudar.
     * 
     * @param onLeadershipChanged Função de callback com parâmetro booleano (true se tornou líder)
     * @return Esta instância de LeaderFollower para encadeamento de métodos
     */
    public LeaderFollower onLeadershipChanged(Consumer<Boolean> onLeadershipChanged) {
        this.onLeadershipChanged = onLeadershipChanged;
        return this;
    }
    
    /**
     * Define o callback para quando atualizações de estado forem recebidas (para seguidores).
     * 
     * @param onStateUpdate Função de callback com o estado como parâmetro
     * @return Esta instância de LeaderFollower para encadeamento de métodos
     */
    public LeaderFollower onStateUpdate(Consumer<String> onStateUpdate) {
        this.onStateUpdate = onStateUpdate;
        return this;
    }
    
    /**
     * Inicia a implementação do padrão Líder-Seguidor.
     */
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        
        try {
            // Cria o socket do servidor
            serverSocket = new ServerSocket(port);
            
            // Inicia o manipulador de mensagens
            startMessageHandler();
            
            // Junta-se ao cluster ou inicia como líder
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
    
    /**
     * Para a implementação do padrão Líder-Seguidor.
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
        
        // Fecha o socket do servidor
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Erro ao fechar o socket do servidor", e);
            }
        }
        
        LOGGER.info(componentType + " parou o padrão Líder-Seguidor");
    }
    
    /**
     * Define as informações do líder conhecido para se juntar a um cluster.
     * 
     * @param leaderId Identificador do líder
     * @param leaderHost Host do líder
     * @param leaderPort Porta do líder
     * @return Esta instância de LeaderFollower para encadeamento de métodos
     */
    public LeaderFollower setLeader(String leaderId, String leaderHost, int leaderPort) {
        this.leaderId = leaderId;
        this.leaderHost = leaderHost;
        this.leaderPort = leaderPort;
        return this;
    }
    
    /**
     * Verifica se este nó é o líder.
     * 
     * @return true se for líder, false caso contrário
     */
    public boolean isLeader() {
        return isLeader.get();
    }
    
    /**
     * Obtém o número do termo atual.
     * 
     * @return Termo atual
     */
    public int getTerm() {
        return term.get();
    }
    
    /**
     * Obtém o ID do líder.
     * 
     * @return ID do líder
     */
    public String getLeaderId() {
        return leaderId;
    }
    
    /**
     * Obtém o estado atual.
     * 
     * @return Estado atual
     */
    public String getCurrentState() {
        return currentState;
    }
    
    /**
     * Atualiza o estado (só pode ser chamado pelo líder).
     * 
     * @param newState Novo estado
     * @throws IllegalStateException Se não for o líder
     */
    public void updateState(String newState) {
        if (!isLeader.get()) {
            throw new IllegalStateException("Somente o líder pode atualizar o estado");
        }
        
        currentState = newState;
        stateVersion.incrementAndGet();
        
        // Agenda replicação imediata do estado
        replicateState();
    }
    
    /**
     * Inicia o manipulador de mensagens para processar mensagens recebidas.
     */
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
    
    /**
     * Manipula uma conexão recebida.
     * 
     * @param socket Socket do cliente
     */
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
    
    /**
     * Manipula uma solicitação de entrada de um seguidor.
     * 
     * @param parts Partes da mensagem
     * @param writer Escritor para resposta
     */
    private void handleJoinRequest(String[] parts, PrintWriter writer) {
        if (!isLeader.get()) {
            // Redireciona para o líder
            writer.println("REDIRECT|" + leaderId + "|" + leaderHost + "|" + leaderPort);
            return;
        }
        
        if (parts.length >= 4) {
            String followerId = parts[1];
            String followerHost = parts[2];
            int followerPort = Integer.parseInt(parts[3]);
            
            // Cria informações do componente para o seguidor
            ComponentInfo follower = new ComponentInfo(
                componentType, followerHost, 0, 0, 0, followerPort
            );
            
            // Adiciona à lista de seguidores
            followers.add(follower);
            
            // Envia estado atual e termo
            writer.println("WELCOME|" + term.get() + "|" + stateVersion.get() + "|" + currentState);
            
            LOGGER.info("Seguidor entrou: " + followerId + " em " + followerHost + ":" + followerPort);
        } else {
            writer.println("ERROR|Formato inválido de mensagem JOIN");
        }
    }
    
    /**
     * Manipula uma mensagem de atualização de estado.
     * 
     * @param parts Partes da mensagem
     */
    private void handleStateUpdate(String[] parts) {
        if (isLeader.get()) {
            // Líderes não aceitam atualizações de estado
            return;
        }
        
        if (parts.length >= 4) {
            int messageTerm = Integer.parseInt(parts[1]);
            int messageStateVersion = Integer.parseInt(parts[2]);
            String state = parts[3];
            
            // Verifica se a mensagem é do termo atual
            if (messageTerm >= term.get()) {
                term.set(messageTerm);
                
                // Atualiza o estado se for uma versão mais recente
                if (messageStateVersion > stateVersion.get()) {
                    stateVersion.set(messageStateVersion);
                    currentState = state;
                    
                    // Notifica atualização de estado
                    if (onStateUpdate != null) {
                        onStateUpdate.accept(currentState);
                    }
                }
            }
        }
    }
    
    /**
     * Manipula uma mensagem de eleição.
     * 
     * @param parts Partes da mensagem
     * @param writer Escritor para resposta
     */
    private void handleElectionMessage(String[] parts, PrintWriter writer) {
        if (parts.length >= 3) {
            int electionTerm = Integer.parseInt(parts[1]);
            String candidateId = parts[2];
            
            if (electionTerm > term.get()) {
                // Aceita o novo líder
                term.set(electionTerm);
                leaderId = candidateId;
                isLeader.set(false);
                
                // Notifica mudança de liderança
                if (onLeadershipChanged != null) {
                    onLeadershipChanged.accept(false);
                }
                
                writer.println("VOTE|" + electionTerm + "|" + componentId + "|YES");
                LOGGER.info("Votou em " + candidateId + " no termo " + electionTerm);
            } else {
                // Rejeita o candidato
                writer.println("VOTE|" + term.get() + "|" + componentId + "|NO");
            }
        }
    }
    
    /**
     * Junta-se a um cluster existente como seguidor.
     */
    private void joinCluster() {
        try (
            Socket socket = new Socket(leaderHost, leaderPort);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Envia solicitação de entrada
            writer.println("JOIN|" + componentId + "|" + host + "|" + port);
            
            // Lê a resposta
            String response = reader.readLine();
            
            if (response != null) {
                String[] parts = response.split("\\|");
                String command = parts[0];
                
                if ("WELCOME".equals(command) && parts.length >= 4) {
                    // Entrou com sucesso como seguidor
                    int newTerm = Integer.parseInt(parts[1]);
                    int newStateVersion = Integer.parseInt(parts[2]);
                    String newState = parts[3];
                    
                    term.set(newTerm);
                    stateVersion.set(newStateVersion);
                    currentState = newState;
                    isLeader.set(false);
                    
                    // Notifica status de liderança
                    if (onLeadershipChanged != null) {
                        onLeadershipChanged.accept(false);
                    }
                    
                    // Notifica atualização de estado
                    if (onStateUpdate != null) {
                        onStateUpdate.accept(currentState);
                    }
                    
                    LOGGER.info("Entrou no cluster como seguidor. Líder: " + leaderId);
                } else if ("REDIRECT".equals(command) && parts.length >= 4) {
                    // Redirecionado para outro líder
                    leaderId = parts[1];
                    leaderHost = parts[2];
                    leaderPort = Integer.parseInt(parts[3]);
                    
                    // Tenta entrar novamente com o novo líder
                    joinCluster();
                } else {
                    LOGGER.warning("Falha ao entrar no cluster. Resposta: " + response);
                    
                    // Inicia uma eleição
                    startElection();
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao entrar no cluster", e);
            
            // Inicia uma eleição
            startElection();
        }
    }
    
    /**
     * Inicia uma eleição para se tornar o líder.
     */
    private void startElection() {
        // Incrementa o termo e vota em si mesmo
        int newTerm = term.incrementAndGet();
        LOGGER.info("Iniciando eleição para o termo " + newTerm);
        
        // Em uma implementação real, contataria todos os outros nós
        // Para esta versão simplificada, apenas torna-se líder
        becomeLeader();
    }
    
    /**
     * Torna-se o líder do cluster.
     */
    private void becomeLeader() {
        isLeader.set(true);
        leaderId = componentId;
        
        // Inicia replicação de estado
        scheduler.scheduleAtFixedRate(
            this::replicateState,
            stateReplicationIntervalMs,
            stateReplicationIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        // Notifica mudança de liderança
        if (onLeadershipChanged != null) {
            onLeadershipChanged.accept(true);
        }
        
        LOGGER.info("Tornou-se líder para o termo " + term.get());
    }
    
    /**
     * Replica o estado para todos os seguidores (somente líder).
     */
    private void replicateState() {
        if (!isLeader.get() || followers.isEmpty()) {
            return;
        }
        
        String stateMessage = "STATE|" + term.get() + "|" + stateVersion.get() + "|" + currentState;
        
        // Envia estado para todos os seguidores
        synchronized (followers) {
            List<ComponentInfo> deadFollowers = new ArrayList<>();
            
            for (ComponentInfo follower : followers) {
                try (
                    Socket socket = new Socket(follower.getHost(), follower.getGrpcPort());
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
                ) {
                    writer.println(stateMessage);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Falha ao replicar estado para seguidor em " + 
                              follower.getHost() + ":" + follower.getGrpcPort(), e);
                    
                    // Marca seguidor para remoção
                    deadFollowers.add(follower);
                }
            }
            
            // Remove seguidores inativos
            followers.removeAll(deadFollowers);
        }
    }
    
    /**
     * Cria uma instância de LeaderFollower configurada para iniciar como líder.
     * 
     * @param componentType Tipo do componente
     * @param componentId ID do componente
     * @param host Endereço do host
     * @param port Porta para comunicação líder-seguidor
     * @return Instância configurada de LeaderFollower
     */
    public static LeaderFollower createLeader(String componentType, String componentId, 
                                             String host, int port) {
        return new LeaderFollower(componentType, componentId, host, port);
    }
    
    /**
     * Cria uma instância de LeaderFollower configurada para se juntar a um cluster existente.
     * 
     * @param componentType Tipo do componente
     * @param componentId ID do componente
     * @param host Endereço do host
     * @param port Porta para comunicação líder-seguidor
     * @param leaderId ID do líder
     * @param leaderHost Endereço do host do líder
     * @param leaderPort Porta do líder
     * @return Instância configurada de LeaderFollower
     */
    public static LeaderFollower createFollower(String componentType, String componentId, 
                                              String host, int port, String leaderId,
                                              String leaderHost, int leaderPort) {
        return new LeaderFollower(componentType, componentId, host, port)
            .setLeader(leaderId, leaderHost, leaderPort);
    }
}