package component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import common.pattern.KeyRangePartition;

/**
 * MessageService - Serviço para processamento e gerenciamento de mensagens no sistema de chat.
 * Oferece funcionalidades de mensagens, salas de chat e notificações.
 */
public class MessageService extends BaseComponent {
    
    private List<String> messages; // Lista para armazenar mensagens
    private Map<String, List<String>> chatRooms; // Salas de chat
    private Map<String, Set<String>> roomMembers; // Membros das salas

    /**
     * Construtor para o MessageService.
     */
    public MessageService(String host, int httpPort, int tcpPort, int udpPort,
                          String gatewayHost, int gatewayRegistrationPort) {
        super("messageService", host, httpPort, tcpPort, udpPort, 
              gatewayHost, gatewayRegistrationPort);
        
        this.messages = new CopyOnWriteArrayList<>();
        this.chatRooms = new ConcurrentHashMap<>();
        this.roomMembers = new ConcurrentHashMap<>();
    }
    
    @Override
    protected void onRangeAssigned(KeyRangePartition.PartitionRange range) {
        // Adiciona mensagem sobre nova faixa atribuída
        String message = System.currentTimeMillis() + ": RANGE_ASSIGNED - " + range.toString();
        messages.add(message);
    }
    
    @Override
    protected void onTopologyChange(List<common.model.ComponentInfo> nodes) {
        // Adiciona mensagem sobre mudança na topologia
        String message = System.currentTimeMillis() + ": TOPOLOGY_CHANGE - " + nodes.size() + " nodes";
        messages.add(message);
    }
    
    @Override
    protected void onDataMigration(String migrationInfo) {
        // Adiciona mensagem sobre migração de dados
        String message = System.currentTimeMillis() + ": DATA_MIGRATION - " + migrationInfo;
        messages.add(message);
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
            String request = new String(data).trim();
            String[] parts = request.split(":", 2);
            
            if (parts.length < 2) {
                // Enviar erro de volta via UDP não é trivial, apenas log
                System.err.println("Formato de requisição UDP inválido: " + request);
                return;
            }
            
            String action = parts[0];
            String response = "";
            
            switch (action) {
                case "MESSAGE_COUNT":
                    response = "MESSAGE_COUNT:" + messages.size();
                    break;
                case "ROOM_COUNT":
                    response = "ROOM_COUNT:" + chatRooms.size();
                    break;
                case "HEARTBEAT":
                    response = "HEARTBEAT:OK";
                    break;
                default:
                    response = "ERROR:Unknown UDP action: " + action;
            }
            
            // Para simplicidade, apenas log da resposta (UDP response requer DatagramSocket)
            System.out.println("UDP Response to " + clientAddress + ":" + clientPort + " -> " + response);
            
        } catch (Exception e) {
            System.err.println("Erro ao processar requisição UDP: " + e.getMessage());
        }
    }
    
    // Métodos auxiliares para processamento de mensagens
    
    private String handleGetMessages() {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        sb.append("Content-Type: application/json\r\n\r\n");
        sb.append("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(messages.get(i)).append("\"");
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
        messages.add(messageContent);
        
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
        messages.add(timestampedMessage);
        return "MESSAGE_SENT:" + timestampedMessage;
    }
    
    private String handleMessageGet() {
        if (messages.isEmpty()) {
            return "MESSAGE_GET:No messages";
        }
        return "MESSAGE_GET:" + String.join("|", messages);
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
