package component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import common.pattern.KeyRangePartition;

/**
 * MessageService - Serviço para processamento e gerenciamento de mensagens no sistema de chat.
 * Oferece funcionalidades de mensagens, salas de chat e notificações.
 */
public class MessageService extends BaseComponent {
    private static final Logger LOGGER = Logger.getLogger(MessageService.class.getName());
    
    private Map<String, String> messages; // Map para armazenar mensagens com chaves
    private Map<String, List<String>> chatRooms; // Salas de chat
    private Map<String, Set<String>> roomMembers; // Membros das salas
    private final String instanceId;

    /**
     * Construtor para o MessageService.
     */
    public MessageService(String host, int httpPort, int tcpPort, int udpPort,
                          String gatewayHost, int gatewayRegistrationPort) {
        super("messageservice", host, httpPort, tcpPort, udpPort, 
              gatewayHost, gatewayRegistrationPort);
        
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        this.messages = new ConcurrentHashMap<>();
        this.chatRooms = new ConcurrentHashMap<>();
        this.roomMembers = new ConcurrentHashMap<>();
    }
    
    @Override
    protected void onRangeAssigned(KeyRangePartition.PartitionRange range) {
        // Adiciona mensagem sobre nova faixa atribuída
        String message = System.currentTimeMillis() + ": RANGE_ASSIGNED - " + range.toString();
        messages.put("range_" + System.currentTimeMillis(), message);
    }
    
    @Override
    protected void onTopologyChange(List<common.model.ComponentInfo> nodes) {
        // Adiciona mensagem sobre mudança na topologia
        String message = System.currentTimeMillis() + ": TOPOLOGY_CHANGE - " + nodes.size() + " nodes";
        messages.put("topo_" + System.currentTimeMillis(), message);
    }
    
    @Override
    protected void onDataMigration(String migrationInfo) {
        // Adiciona mensagem sobre migração de dados
        String message = System.currentTimeMillis() + ": DATA_MIGRATION - " + migrationInfo;
        messages.put("migration_" + System.currentTimeMillis(), message);
    }

    @Override
    protected void handleHTTPRequest(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();
            
            String requestLine = in.readLine();
            if (requestLine == null) return;
            
            // Parse HTTP request
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length != 3) return;
            
            String method = requestParts[0];
            String path = requestParts[1];
            
            String response = "";
            
            // Handle different message endpoints
            if (path.equals("/messages") && method.equals("GET")) {
                response = handleGetMessages();
            } else if (path.startsWith("/messages/") && method.equals("POST")) {
                response = handleSendMessage(path, in);
            } else if (path.startsWith("/rooms/") && method.equals("GET")) {
                response = handleGetRoomMessages(path);
            } else if (path.startsWith("/rooms/") && method.equals("POST")) {
                response = handleJoinRoom(path, in);
            } else if (path.equals("/info")) {
                // Special endpoint for testing and monitoring
                String infoResponse = "{\"status\":\"ok\",\"service\":\"MessageService\"," +
                                     "\"messages\":" + messages.size() + 
                                     ",\"rooms\":" + chatRooms.size() + 
                                     ",\"timestamp\":" + System.currentTimeMillis() + "}";
                response = "HTTP/1.1 200 OK\r\n" +
                          "Content-Type: application/json\r\n" +
                          "Content-Length: " + infoResponse.length() + "\r\n" +
                          "Connection: close\r\n\r\n" + infoResponse;
            } else {
                response = "HTTP/1.1 404 Not Found\r\n\r\nEndpoint not found";
            }
            
            out.write(response.getBytes());
            out.flush();
            
        } catch (IOException e) {
            System.err.println("Erro ao processar requisição HTTP: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
        }
    }

    @Override
    protected void handleTCPRequest(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            
            String request = in.readLine();
            if (request == null) return;
            
            String[] parts = request.split(":", 3);
            if (parts.length < 2) {
                out.println("ERROR:Invalid request format");
                return;
            }
            
            String action = parts[0];
            String response = "";
            
            switch (action) {
                case "MESSAGE_SEND":
                    if (parts.length >= 3) {
                        String messageContent = parts[2];
                        response = handleMessageSend(messageContent);
                    } else {
                        response = "ERROR:Missing message content";
                    }
                    break;
                case "MESSAGE_GET":
                    response = handleMessageGet();
                    break;
                case "ROOM_JOIN":
                    if (parts.length >= 3) {
                        String roomId = parts[1];
                        String userId = parts[2];
                        response = handleRoomJoin(roomId, userId);
                    } else {
                        response = "ERROR:Missing room or user ID";
                    }
                    break;
                case "ROOM_MESSAGES":
                    if (parts.length >= 2) {
                        String roomId = parts[1];
                        response = handleRoomMessages(roomId);
                    } else {
                        response = "ERROR:Missing room ID";
                    }
                    break;
                default:
                    response = "ERROR:Unknown action: " + action;
            }
            
            out.println(response);
            
        } catch (IOException e) {
            System.err.println("Erro ao processar requisição TCP: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
        }
    }

    @Override
    protected void handleUDPRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            // Convert bytes to string and clean up any null bytes or extra whitespace  
            String request = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();
            // Remove any null bytes that might be present
            request = request.replaceAll("\0", "");
            String[] parts = request.split("\\|");
            
            if (parts.length < 1) {
                sendUDPResponse("ERROR|Invalid request format", clientAddress, clientPort);
                return;
            }
            
            String action = parts[0].toUpperCase();
            String response = "";
            
            switch (action) {
                case "SEND":
                    if (parts.length >= 4 && "message".equals(parts[1])) {
                        String sender = parts[2];
                        String messageText = parts[3];
                        String messageKey = "msg:" + System.currentTimeMillis();
                        
                        if (isResponsibleFor(messageKey)) {
                            String messageData = String.format("{\"sender\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}", 
                                                             sender, messageText, System.currentTimeMillis());
                            messages.put(messageKey, messageData);
                            response = "MESSAGE_SENT|Message from " + sender + " sent successfully";
                        } else {
                            response = "REDIRECT|Not responsible for message";
                        }
                    } else {
                        response = "ERROR|SEND requires: SEND|message|sender|text";
                    }
                    break;
                case "GET":
                    if (parts.length >= 2 && "messages".equals(parts[1])) {
                        // For UDP, return only a summary instead of all message content
                        // to avoid size limitations
                        int messageCount = messages.size();
                        response = "SUCCESS|Messages retrieved: " + messageCount + " total messages";
                    } else {
                        response = "ERROR|GET requires: GET|messages";
                    }
                    break;
                case "MESSAGE_COUNT":
                    response = "SUCCESS|MESSAGE_COUNT:" + messages.size();
                    break;
                case "ROOM_COUNT":
                    response = "SUCCESS|ROOM_COUNT:" + chatRooms.size();
                    break;
                case "INFO":
                    response = "SUCCESS|MessageService|" + instanceId + "|" + messages.size();
                    break;
                default:
                    response = "ERROR|Unknown UDP action: " + action;
            }
            
            sendUDPResponse(response, clientAddress, clientPort);
            
        } catch (Exception e) {
            try {
                sendUDPResponse("ERROR|Processing failed: " + e.getMessage(), clientAddress, clientPort);
            } catch (Exception sendError) {
                LOGGER.log(Level.WARNING, "Failed to send UDP error response", sendError);
            }
        }
    }
    
    private void sendUDPResponse(String response, InetAddress clientAddress, int clientPort) throws IOException {
        byte[] responseData = response.getBytes(StandardCharsets.UTF_8);
        
        // Check if response is too large for UDP (limit to ~60KB for safety)
        if (responseData.length > 60000) {
            String truncatedResponse = "SUCCESS|Response truncated due to size limit";
            responseData = truncatedResponse.getBytes(StandardCharsets.UTF_8);
        }
        
        DatagramPacket responsePacket = new DatagramPacket(
            responseData, responseData.length, clientAddress, clientPort
        );
        udpServer.send(responsePacket);
    }
    
    // Métodos auxiliares para processamento de mensagens
    
    private String handleGetMessages() {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        sb.append("Content-Type: application/json\r\n\r\n");
        sb.append("[");
        boolean first = true;
        for (String message : messages.values()) {
            if (!first) sb.append(",");
            sb.append("\"").append(message).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String handleSendMessage(String path, BufferedReader in) throws IOException {
        // Extrai o room ID do path /messages/{roomId}
        String[] pathParts = path.split("/");
        if (pathParts.length < 3) {
            return "HTTP/1.1 400 Bad Request\r\n\r\nInvalid room ID";
        }
        
        String roomId = pathParts[2];
        
        // Lê o corpo da requisição para obter a mensagem
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            // Skip headers
        }
        // Aqui deveríamos ler o corpo, mas para simplicidade...
        String messageContent = "Message to room " + roomId + " at " + System.currentTimeMillis();
        
        // Adiciona mensagem à sala
        chatRooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(messageContent);
        messages.put("msg_" + System.currentTimeMillis(), messageContent);
        
        return "HTTP/1.1 201 Created\r\n\r\nMessage sent";
    }
    
    private String handleGetRoomMessages(String path) {
        // Extrai o room ID do path /rooms/{roomId}
        String[] pathParts = path.split("/");
        if (pathParts.length < 3) {
            return "HTTP/1.1 400 Bad Request\r\n\r\nInvalid room ID";
        }
        
        String roomId = pathParts[2];
        List<String> roomMessages = chatRooms.get(roomId);
        
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        sb.append("Content-Type: application/json\r\n\r\n");
        sb.append("[");
        
        if (roomMessages != null) {
            for (int i = 0; i < roomMessages.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(roomMessages.get(i)).append("\"");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String handleJoinRoom(String path, BufferedReader in) {
        // Implementação simplificada para juntar-se a uma sala
        String[] pathParts = path.split("/");
        if (pathParts.length < 3) {
            return "HTTP/1.1 400 Bad Request\r\n\r\nInvalid room ID";
        }
        
        String roomId = pathParts[2];
        
        // Inicializa a sala se não existir
        chatRooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        roomMembers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        
        return "HTTP/1.1 200 OK\r\n\r\nJoined room " + roomId;
    }
    
    private String handleMessageSend(String messageContent) {
        String timestampedMessage = System.currentTimeMillis() + ": " + messageContent;
        messages.put("tcp_msg_" + System.currentTimeMillis(), timestampedMessage);
        return "MESSAGE_SENT:" + timestampedMessage;
    }
    
    private String handleMessageGet() {
        if (messages.isEmpty()) {
            return "MESSAGE_GET:No messages";
        }
        return "MESSAGE_GET:" + String.join("|", messages.values());
    }
    
    private String handleRoomJoin(String roomId, String userId) {
        chatRooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        Set<String> members = roomMembers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        members.add(userId);
        
        String joinMessage = System.currentTimeMillis() + ": User " + userId + " joined room " + roomId;
        chatRooms.get(roomId).add(joinMessage);
        
        return "ROOM_JOINED:" + roomId + ":" + userId;
    }
    
    private String handleRoomMessages(String roomId) {
        List<String> roomMessages = chatRooms.get(roomId);
        if (roomMessages == null || roomMessages.isEmpty()) {
            return "ROOM_MESSAGES:" + roomId + ":No messages";
        }
        return "ROOM_MESSAGES:" + roomId + ":" + String.join("|", roomMessages);
    }
}
