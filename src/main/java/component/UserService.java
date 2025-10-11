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
import java.util.logging.Level;
import java.util.logging.Logger;
import common.pattern.KeyRangePartition;

/**
 * Implementação do UserService - serviço de gerenciamento de usuários com particionamento por faixa de chaves.
 * Gerencia perfis de usuários, autenticação, sessões e status de presença.
 */
public class UserService extends BaseComponent {
    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());
    
    // Armazenamento de dados de usuários em memória
    private final Map<String, String> userStore = new ConcurrentHashMap<>();

    // Identificador da instância para fins de registro
    private final String instanceId;
    
    /**
     * Construtor para o UserService.
     */
    public UserService(String host, int httpPort, int tcpPort, int udpPort,
                      String gatewayHost, int gatewayRegistrationPort) {
        super("userService", host, httpPort, tcpPort, udpPort, 
              gatewayHost, gatewayRegistrationPort);
        
        // Gera um ID único para a instância
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        
        // Adiciona alguns usuários de exemplo
        userStore.put("user:admin", "{\"username\":\"admin\",\"email\":\"admin@chat.com\",\"role\":\"admin\",\"status\":\"online\"}");
        userStore.put("user:john", "{\"username\":\"john\",\"email\":\"john@chat.com\",\"role\":\"user\",\"status\":\"offline\"}");
        userStore.put("auth:token123", "{\"username\":\"admin\",\"expires\":\"" + (System.currentTimeMillis() + 3600000) + "\"}");
        userStore.put("instance", instanceId);
    }
    
    @Override
    public void start() {
        super.start();
    }
    
    @Override
    protected void onRangeAssigned(KeyRangePartition.PartitionRange range) {
        // LOGGER.info("UserService[" + instanceId + "] recebeu nova faixa: " + range);
        userStore.put("range_start", range.getStartKey() != null ? range.getStartKey() : "null");
        userStore.put("range_end", range.getEndKey() != null ? range.getEndKey() : "null");
        userStore.put("responsible", String.valueOf(range.isResponsible()));
        userStore.put("range_updated", String.valueOf(System.currentTimeMillis()));
    }
    
    @Override
    protected void onTopologyChange(java.util.List<common.model.ComponentInfo> nodes) {
        // LOGGER.info("UserService[" + instanceId + "] detectou mudança na topologia. Nós: " + nodes.size());
        userStore.put("topology_size", String.valueOf(nodes.size()));
        userStore.put("topology_updated", String.valueOf(System.currentTimeMillis()));
    }
    
    @Override
    protected void onDataMigration(String migrationInfo) {
        // LOGGER.info("UserService[" + instanceId + "] iniciando migração: " + migrationInfo);
        userStore.put("migration_info", migrationInfo);
        userStore.put("migration_time", String.valueOf(System.currentTimeMillis()));
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
            
            String responseBody = "";
            
            if (path.startsWith("/user/")) {
                String[] pathParts = path.substring(6).split("/");
                if (pathParts.length > 0) {
                    String action = pathParts[0].toUpperCase();
                    
                    switch (action) {
                        case "CREATE":
                            if (pathParts.length >= 3) {
                                String username = pathParts[1];
                                String profileData = pathParts[2];
                                String userKey = "user:" + username;
                                
                                if (isResponsibleFor(userKey)) {
                                    userStore.put(userKey, profileData);
                                    responseBody = "{\"status\":\"success\",\"message\":\"Usuário criado: " + username + "\"}";
                                } else {
                                    common.model.ComponentInfo responsibleNode = getResponsibleNode(userKey);
                                    responseBody = "{\"status\":\"redirect\",\"node\":\"" + responsibleNode.getInstanceId() + 
                                                  "\",\"host\":\"" + responsibleNode.getHost() + "\",\"port\":" + responsibleNode.getHttpPort() + "}";
                                }
                            } else {
                                responseBody = "{\"status\":\"error\",\"message\":\"Formato inválido para CREATE\"}";
                            }
                            break;
                            
                        case "GET":
                            if (pathParts.length >= 2) {
                                String username = pathParts[1];
                                String userKey = "user:" + username;
                                
                                if (isResponsibleFor(userKey)) {
                                    String userData = userStore.getOrDefault(userKey, null);
                                    if (userData != null) {
                                        responseBody = "{\"status\":\"success\",\"user\":" + userData + "}";
                                    } else {
                                        responseBody = "{\"status\":\"error\",\"message\":\"Usuário não encontrado: " + username + "\"}";
                                    }
                                } else {
                                    common.model.ComponentInfo responsibleNode = getResponsibleNode(userKey);
                                    responseBody = "{\"status\":\"redirect\",\"node\":\"" + responsibleNode.getInstanceId() + 
                                                  "\",\"host\":\"" + responsibleNode.getHost() + "\",\"port\":" + responsibleNode.getHttpPort() + "}";
                                }
                            } else {
                                responseBody = "{\"status\":\"error\",\"message\":\"Username necessário para GET\"}";
                            }
                            break;
                            
                        default:
                            responseBody = "{\"status\":\"error\",\"message\":\"Ação desconhecida: " + action + "\"}";
                    }
                }
            } else if (path.startsWith("/auth/")) {
                String[] pathParts = path.substring(6).split("/");
                if (pathParts.length > 0) {
                    String action = pathParts[0].toUpperCase();
                    
                    switch (action) {
                        case "LOGIN":
                            if (pathParts.length >= 3) {
                                String username = pathParts[1];
                                String password = pathParts[2];
                                String userKey = "user:" + username;
                                
                                if (isResponsibleFor(userKey)) {
                                    String userData = userStore.getOrDefault(userKey, null);
                                    if (userData != null) {
                                        String token = "token_" + System.currentTimeMillis();
                                        String tokenKey = "auth:" + token;
                                        String tokenData = "{\"username\":\"" + username + "\",\"expires\":" + (System.currentTimeMillis() + 3600000) + "}";
                                        userStore.put(tokenKey, tokenData);
                                        responseBody = "{\"status\":\"success\",\"token\":\"" + token + "\",\"expires\":" + (System.currentTimeMillis() + 3600000) + "}";
                                    } else {
                                        responseBody = "{\"status\":\"error\",\"message\":\"Usuário não encontrado\"}";
                                    }
                                } else {
                                    common.model.ComponentInfo responsibleNode = getResponsibleNode(userKey);
                                    responseBody = "{\"status\":\"redirect\",\"node\":\"" + responsibleNode.getInstanceId() + 
                                                  "\",\"host\":\"" + responsibleNode.getHost() + "\",\"port\":" + responsibleNode.getHttpPort() + "}";
                                }
                            }
                            break;
                            
                        case "VALIDATE":
                            if (pathParts.length >= 2) {
                                String token = pathParts[1];
                                String tokenKey = "auth:" + token;
                                
                                if (isResponsibleFor(tokenKey)) {
                                    String tokenData = userStore.getOrDefault(tokenKey, null);
                                    if (tokenData != null) {
                                        responseBody = "{\"status\":\"valid\",\"data\":" + tokenData + "}";
                                    } else {
                                        responseBody = "{\"status\":\"invalid\",\"message\":\"Token não encontrado\"}";
                                    }
                                } else {
                                    common.model.ComponentInfo responsibleNode = getResponsibleNode(tokenKey);
                                    responseBody = "{\"status\":\"redirect\",\"node\":\"" + responsibleNode.getInstanceId() + 
                                                  "\",\"host\":\"" + responsibleNode.getHost() + "\",\"port\":" + responsibleNode.getHttpPort() + "}";
                                }
                            }
                            break;
                    }
                }
            } else {
                responseBody = "UserService - Sistema de Chat Distribuído\\n" +
                              "Instância: " + instanceId + "\\n" +
                              "Usuários registrados: " + userStore.size();
            }
            
            String response = buildHTTPResponse("200 OK", "application/json", responseBody);
            output.write(response.getBytes());
            output.flush();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao processar requisição HTTP no UserService", e);
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
                    case "USER_CREATE":
                        if (parts.length >= 3) {
                            String username = parts[1];
                            String profileData = parts[2];
                            String userKey = "user:" + username;
                            
                            if (isResponsibleFor(userKey)) {
                                userStore.put(userKey, profileData);
                                response = "SUCCESS|Usuário criado: " + username;
                            } else {
                                common.model.ComponentInfo responsibleNode = getResponsibleNode(userKey);
                                if (responsibleNode != null) {
                                    response = "REDIRECT|" + responsibleNode.getInstanceId() + "|" +
                                               responsibleNode.getHost() + "|" + responsibleNode.getHttpPort() + "|" +
                                               "Usuário deve ser criado no nó responsável";
                                } else {
                                    response = "ERROR|Nenhum nó responsável encontrado para usuário: " + username;
                                }
                            }
                        } else {
                            response = "ERROR|Formato USER_CREATE inválido, esperado: USER_CREATE|username|profile_data";
                        }
                        break;
                        
                    case "USER_GET":
                        if (parts.length >= 2) {
                            String username = parts[1];
                            String userKey = "user:" + username;
                            
                            if (isResponsibleFor(userKey)) {
                                String userData = userStore.getOrDefault(userKey, null);
                                if (userData != null) {
                                    response = "USER_DATA|" + username + "|" + userData;
                                } else {
                                    response = "ERROR|Usuário não encontrado: " + username;
                                }
                            } else {
                                common.model.ComponentInfo responsibleNode = getResponsibleNode(userKey);
                                if (responsibleNode != null) {
                                    response = "REDIRECT|" + responsibleNode.getInstanceId() + "|" +
                                               responsibleNode.getHost() + "|" + responsibleNode.getHttpPort() + "|" +
                                               "Usuário deve ser consultado no nó responsável";
                                } else {
                                    response = "ERROR|Nenhum nó responsável encontrado para usuário: " + username;
                                }
                            }
                        } else {
                            response = "ERROR|Formato USER_GET inválido, esperado: USER_GET|username";
                        }
                        break;
                        
                    case "AUTH_LOGIN":
                        if (parts.length >= 3) {
                            String username = parts[1];
                            String password = parts[2];
                            String userKey = "user:" + username;
                            
                            if (isResponsibleFor(userKey)) {
                                String userData = userStore.getOrDefault(userKey, null);
                                if (userData != null) {
                                    String token = "token_" + System.currentTimeMillis();
                                    String tokenKey = "auth:" + token;
                                    String tokenData = "{\"username\":\"" + username + "\",\"expires\":" + (System.currentTimeMillis() + 3600000) + "}";
                                    userStore.put(tokenKey, tokenData);
                                    response = "LOGIN_SUCCESS|" + token + "|" + (System.currentTimeMillis() + 3600000);
                                } else {
                                    response = "LOGIN_FAILED|Usuário não encontrado";
                                }
                            } else {
                                common.model.ComponentInfo responsibleNode = getResponsibleNode(userKey);
                                if (responsibleNode != null) {
                                    response = "REDIRECT|" + responsibleNode.getInstanceId() + "|" +
                                               responsibleNode.getHost() + "|" + responsibleNode.getHttpPort();
                                } else {
                                    response = "ERROR|Nenhum nó responsível encontrado";
                                }
                            }
                        } else {
                            response = "ERROR|Formato AUTH_LOGIN inválido, esperado: AUTH_LOGIN|username|password";
                        }
                        break;
                        
                    case "INFO":
                        KeyRangePartition.PartitionRange myRange = keyRangePartition != null ? 
                                                                   keyRangePartition.getMyRange() : null;
                        String rangeInfo = myRange != null ? 
                                          (myRange.getStartKey() + "-" + myRange.getEndKey()) : "unknown";
                        response = "INFO|UserService|" + instanceId + "|" + userStore.size() + "|" +
                                   "RANGE:" + rangeInfo;
                        break;
                        
                    case "RANGE":
                        KeyRangePartition.PartitionRange range = keyRangePartition != null ? 
                                                                keyRangePartition.getMyRange() : null;
                        if (range != null) {
                            response = "RANGE|" + range.getStartKey() + "|" + range.getEndKey() + "|" + 
                                      range.getNodeId() + "|" + range.isResponsible();
                        } else {
                            response = "UNKNOWN_RANGE";
                        }
                        break;
                        
                    case "LIST_USERS":
                        StringBuilder userList = new StringBuilder();
                        for (String key : userStore.keySet()) {
                            if (key.startsWith("user:")) {
                                userList.append(key.substring(5)).append(",");
                            }
                        }
                        response = "USERS|" + (userList.length() > 0 ? 
                                  userList.substring(0, userList.length() - 1) : "");
                        break;
                        
                    default:
                        response = "ERROR|Ação desconhecida: " + action;
                }
                
                writer.println(response);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao processar requisição TCP no UserService", e);
        }
    }
    
    @Override
    protected void handleUDPRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            String request = new String(data);
            
            if (request.startsWith("HEARTBEAT")) {
                sendHeartbeatResponse(clientAddress, clientPort);
            } else {
                String[] parts = request.split("\\|");
                String action = parts[0].toUpperCase();
                
                String response;
                switch (action) {
                    case "USER_EXISTS":
                        if (parts.length >= 2) {
                            String username = parts[1];
                            String userKey = "user:" + username;
                            
                            if (isResponsibleFor(userKey)) {
                                String userData = userStore.getOrDefault(userKey, null);
                                response = userData != null ? "USER_EXISTS|" + username : "USER_NOT_FOUND|" + username;
                            } else {
                                response = "NOT_RESPONSIBLE|" + username;
                            }
                        } else {
                            response = "ERROR|Username necessário";
                        }
                        break;
                    case "USER_COUNT":
                        response = "COUNT|" + userStore.keySet().stream()
                                                      .mapToInt(key -> key.startsWith("user:") ? 1 : 0)
                                                      .sum();
                        break;
                    case "INFO":
                        response = "INFO|UserService|" + instanceId + "|" + userStore.size();
                        break;
                    default:
                        response = "ERROR|Ação desconhecida: " + action;
                }
                
                byte[] responseData = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(
                    responseData, responseData.length, clientAddress, clientPort
                );
                udpServer.send(responsePacket);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao processar requisição UDP no UserService", e);
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
        int httpPort = 8181;
        int tcpPort = 8182;
        int udpPort = 8183;
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
        
        UserService userService = new UserService(
            host, httpPort, tcpPort, udpPort, gatewayHost, gatewayRegistrationPort
        );
        userService.start();
    }
}
