package component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import common.pattern.LeaderFollower;

/**
 * Implementação do Componente A - serviço de armazenamento chave-valor com alta disponibilidade.
 */
public class ComponentA extends BaseComponent {
    private static final Logger LOGGER = Logger.getLogger(ComponentA.class.getName());
    
    // Armazenamento chave-valor em memória
    private final Map<String, String> dataStore = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private LeaderFollower leaderFollower;
    private boolean isLeader = false;
    private final int leaderPort = 9000;

    // Identificador da instância para fins de registro
    private final String instanceId;
    
    /**
     * Construtor para o Componente A.
     */
    public ComponentA(String host, int httpPort, int tcpPort, int udpPort,
                      String gatewayHost, int gatewayRegistrationPort) {
        super("componentA", host, httpPort, tcpPort, udpPort, 
              gatewayHost, gatewayRegistrationPort);
        
        // Gera um ID único para a instância
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        
        // Adiciona alguns dados iniciais
        dataStore.put("example", "Este é um valor de exemplo do Componente A");
        dataStore.put("instance", instanceId);
    }
    
    @Override
    public void start() {
        super.start();
    }
    
    @Override
    protected void onBecomeLeader() {
        isLeader = true;
        dataStore.put("role", "leader");
        dataStore.put("leader_since", String.valueOf(System.currentTimeMillis()));
        
        // Agenda replicação periódica de estado
        scheduler.scheduleAtFixedRate(
            this::replicateState,
            1000, 5000, TimeUnit.MILLISECONDS
        );
    }
    
    @Override
    protected void onBecomeFollower() {
        isLeader = false;
        dataStore.put("role", "follower");
    }
    
    @Override
    protected String serializeState() {
        return gson.toJson(dataStore);
    }
    
    @Override
    protected void processStateUpdate(String stateData) {
        try {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> leaderState = gson.fromJson(stateData, type);
            
            // Preserva informações locais
            String localInstance = dataStore.get("instance");
            String localRole = dataStore.get("role");
            
            // Atualiza o estado com os dados do líder
            dataStore.clear();
            dataStore.putAll(leaderState);
            
            // Restaura informações locais
            dataStore.put("instance", localInstance);
            dataStore.put("role", localRole);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao processar atualização de estado", e);
        }
    }
    
    @Override
    protected void handleHTTPRequest(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream output = clientSocket.getOutputStream()
        ) {
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                requestBuilder.append(line).append("\r\n");
            }
            
            String request = requestBuilder.toString();
            String firstLine = request.substring(0, request.indexOf("\r\n"));
            String[] parts = firstLine.split(" ");
            String method = parts[0];
            String path = parts[1];
            
            String response;
            if (path.startsWith("/get/")) {
                String key = path.substring(5);
                String value = dataStore.getOrDefault(key, "Chave não encontrada");
                response = buildHTTPResponse("200 OK", "text/plain", value);
            } else if (method.equals("POST") && path.startsWith("/set/")) {
                String key = path.substring(5);
                
                int contentLength = 0;
                if (request.contains("Content-Length:")) {
                    String contentLengthStr = request.substring(
                        request.indexOf("Content-Length:") + 16,
                        request.indexOf("\r\n", request.indexOf("Content-Length:"))
                    );
                    contentLength = Integer.parseInt(contentLengthStr.trim());
                }
                
                StringBuilder valueBuilder = new StringBuilder();
                if (contentLength > 0) {
                    char[] buffer = new char[contentLength];
                    reader.read(buffer, 0, contentLength);
                    valueBuilder.append(buffer);
                }
                
                String value = valueBuilder.toString();
                dataStore.put(key, value);
                response = buildHTTPResponse("200 OK", "text/plain", "Valor armazenado para a chave: " + key);
            } else if (path.equals("/info")) {
                String info = "Instância do Componente A " + instanceId + "\n" +
                              "Tamanho do armazenamento: " + dataStore.size() + " entradas\n" +
                              "Executando em: " + host + "\n" +
                              "Porta HTTP: " + httpPort;
                response = buildHTTPResponse("200 OK", "text/plain", info);
            } else {
                response = buildHTTPResponse("404 Not Found", "text/plain", "Endpoint desconhecido");
            }
            
            output.write(response.getBytes());
            output.flush();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao processar requisição HTTP no Componente A", e);
        }
    }
    
    @Override
    protected void handleTCPRequest(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String request = reader.readLine();
            
            if (request != null && !request.isEmpty()) {
                String[] parts = request.split("\\|");
                String action = parts[0].toUpperCase();
                
                String response;
                switch (action) {
                    case "GET":
                        if (parts.length >= 2) {
                            String key = parts[1];
                            String value = dataStore.getOrDefault(key, "Chave não encontrada");
                            response = "VALUE|" + key + "|" + value;
                        } else {
                            response = "ERROR|Formato GET inválido, esperado: GET|KEY";
                        }
                        break;
                    case "SET":
                        if (parts.length >= 3) {
                            String key = parts[1];
                            String value = parts[2];
                            
                            if (!isLeader && leaderFollower != null && leaderFollower.getLeaderId() != null) {
                                response = "REDIRECT|" + leaderFollower.getLeaderId() + "|" +
                                           "Operação de escrita deve ser enviada ao líder";
                            } else {
                                dataStore.put(key, value);
                                response = "SUCCESS|Valor armazenado para a chave: " + key;
                                
                                if (isLeader) {
                                    replicateState();
                                }
                            }
                        } else {
                            response = "ERROR|Formato SET inválido, esperado: SET|KEY|VALUE";
                        }
                        break;
                    case "INFO":
                        response = "INFO|Componente A|" + instanceId + "|" + dataStore.size() + "|" +
                                   (isLeader ? "LEADER" : "FOLLOWER");
                        break;
                    case "LEADER":
                        if (isLeader) {
                            response = "LEADER|" + instanceId + "|" + host + "|" + leaderPort;
                        } else if (leaderFollower != null && leaderFollower.getLeaderId() != null) {
                            response = "LEADER|" + leaderFollower.getLeaderId();
                        } else {
                            response = "UNKNOWN_LEADER";
                        }
                        break;
                    case "LIST":
                        StringBuilder keyList = new StringBuilder();
                        for (String key : dataStore.keySet()) {
                            keyList.append(key).append(",");
                        }
                        response = "KEYS|" + (keyList.length() > 0 ? 
                                  keyList.substring(0, keyList.length() - 1) : "");
                        break;
                    default:
                        response = "ERROR|Ação desconhecida: " + action;
                }
                
                writer.println(response);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao processar requisição TCP no Componente A", e);
        }
    }
    
    @Override
    protected void handleUDPRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            String request = new String(data);
            
            String[] parts = request.split("\\|");
            String action = parts[0].toUpperCase();
            
            String response;
            switch (action) {
                case "GET":
                    if (parts.length >= 2) {
                        String key = parts[1];
                        String value = dataStore.getOrDefault(key, "Chave não encontrada");
                        response = "VALUE|" + key + "|" + value;
                    } else {
                        response = "ERROR|Formato GET inválido, esperado: GET|KEY";
                    }
                    break;
                case "SET":
                    if (parts.length >= 3) {
                        String key = parts[1];
                        String value = parts[2];
                        dataStore.put(key, value);
                        response = "SUCCESS|Valor armazenado para a chave: " + key;
                    } else {
                        response = "ERROR|Formato SET inválido, esperado: SET|KEY|VALUE";
                    }
                    break;
                case "INFO":
                    response = "INFO|Componente A|" + instanceId + "|" + dataStore.size();
                    break;
                default:
                    response = "ERROR|Ação desconhecida: " + action;
            }
            
            byte[] responseData = response.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(
                responseData, responseData.length, clientAddress, clientPort
            );
            udpServer.send(responsePacket);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao processar requisição UDP no Componente A", e);
        }
    }
    
    /**
     * Replica o estado atual para os seguidores (quando for líder).
     */
    @Override
    protected void replicateState() {
        if (isLeader && leaderFollower != null) {
            String stateData = serializeState();
            leaderFollower.updateState(stateData);
        }
    }
    
    private String buildHTTPResponse(String status, String contentType, String body) {
        return "HTTP/1.1 " + status + "\r\n" +
               "Content-Type: " + contentType + "\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "Connection: close\r\n" +
               "\r\n" +
               body;
    }
    
    public static void main(String[] args) {
        String host = "localhost";
        int httpPort = 8081;
        int tcpPort = 8082;
        int udpPort = 8083;
        String gatewayHost = "localhost";
        int gatewayRegistrationPort = 8000;
        
        if (args.length >= 6) {
            host = args[0];
            httpPort = Integer.parseInt(args[1]);
            tcpPort = Integer.parseInt(args[2]);
            udpPort = Integer.parseInt(args[3]);
            gatewayHost = args[4];
            gatewayRegistrationPort = Integer.parseInt(args[5]);
        }
        
        if (args.length >= 8) {
            String leaderHost = args[6];
            int leaderPort = Integer.parseInt(args[7]);
            
            ComponentA component = new ComponentA(
                host, httpPort, tcpPort, udpPort, gatewayHost, gatewayRegistrationPort
            );
            
            component.start();
            
            component.leaderFollower = LeaderFollower.createFollower(
                component.componentType, component.instanceId, 
                host, component.leaderPort,
                "leader-" + component.componentType, leaderHost, leaderPort
            );
            
            component.leaderFollower
                .onLeadershipChanged(component::handleLeadershipChange)
                .onStateUpdate(component::handleStateUpdate)
                .start();
            
        } else {
            ComponentA component = new ComponentA(
                host, httpPort, tcpPort, udpPort, gatewayHost, gatewayRegistrationPort
            );
            component.start();
        }
    }
}
