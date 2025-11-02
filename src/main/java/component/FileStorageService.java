package component;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import common.pattern.KeyRangePartition;

public class FileStorageService extends BaseComponent {
    private static final Logger LOGGER = Logger.getLogger(FileStorageService.class.getName());
    private final Map<String, Map<String, String>> userFiles = new ConcurrentHashMap<>();
    
    public FileStorageService(String host, int httpPort, int tcpPort, int udpPort,
                      String gatewayHost, int gatewayRegistrationPort) {
        super("fileservice", host, httpPort, tcpPort, udpPort, 
              gatewayHost, gatewayRegistrationPort);
    }
    
    @Override
    public void start() {
        super.start();
    }
    
    @Override
    protected void onRangeAssigned(KeyRangePartition.PartitionRange range) {}
    
    @Override
    protected void onTopologyChange(java.util.List<common.model.ComponentInfo> nodes) {}
    
    @Override
    protected void onDataMigration(String migrationInfo) {}
    
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
            String path = parts[1];
            
            String responseBody = "";
            
            if (path.startsWith("/file/")) {
                String[] pathParts = path.substring(6).split("/");
                if (pathParts.length > 0) {
                    String action = pathParts[0].toUpperCase();
                    
                    switch (action) {
                        case "STORE":
                            if (pathParts.length >= 3) {
                                String username = pathParts[1];
                                String filename = pathParts[2];
                                String content = pathParts.length >= 4 ? pathParts[3] : "";
                                
                                storeFile(username, filename, content);
                                responseBody = "{\"status\":\"success\",\"message\":\"Arquivo armazenado: " + filename + "\"}";
                            } else {
                                responseBody = "{\"status\":\"error\",\"message\":\"Invalid format for STORE\"}";
                            }
                            break;
                            
                        case "RETRIEVE":
                            if (pathParts.length >= 2) {
                                String username = pathParts[1];
                                String filename = pathParts.length >= 3 ? pathParts[2] : "";
                                
                                String content = retrieveFile(username, filename);
                                if (content != null) {
                                    responseBody = "{\"status\":\"success\",\"content\":\"" + content + "\"}";
                                } else {
                                    responseBody = "{\"status\":\"error\",\"message\":\"Arquivo não encontrado: " + filename + "\"}";
                                }
                            } else {
                                responseBody = "{\"status\":\"error\",\"message\":\"Username and filename required for RETRIEVE\"}";
                            }
                            break;
                            
                        default:
                            responseBody = "{\"status\":\"error\",\"message\":\"Unknown action: " + action + "\"}";
                    }
                }
            } else if (path.startsWith("/health")) {
                responseBody = "{\"status\":\"healthy\",\"service\":\"FileStorageService\",\"instance\":\"" + instanceId + "\"}";
            } else if (path.equals("/info")) {
                responseBody = "{\"status\":\"ok\",\"service\":\"FileStorageService\",\"instance\":\"" + instanceId + 
                              "\",\"files\":" + userFiles.size() + ",\"timestamp\":" + System.currentTimeMillis() + "}";
            } else {
                responseBody = "FileStorageService - Sistema de Armazenamento Distribuído\\n" +
                              "Instância: " + instanceId + "\\n" +
                              "Arquivos armazenados: " + userFiles.size();
            }
            
            // Build full HTTP response
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                                 "Content-Type: application/json\r\n" +
                                 "Content-Length: " + responseBody.length() + "\r\n" +
                                 "\r\n" +
                                 responseBody;
            
            output.write(httpResponse.getBytes());
            output.flush();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "CRÍTICO: Falha ao enviar resposta HTTP", e);
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
                    case "FILE_STORE":
                        if (parts.length >= 4) {
                            String username = parts[1];
                            String filename = parts[2];
                            String content = parts[3];
                            
                            storeFile(username, filename, content);
                            response = "SUCCESS|Arquivo armazenado: " + filename;
                        } else {
                            response = "ERROR|Formato FILE_STORE inválido, esperado: FILE_STORE|username|filename|content";
                        }
                        break;
                        
                    case "FILE_RETRIEVE":
                        if (parts.length >= 3) {
                            String username = parts[1];
                            String filename = parts[2];
                            
                            String content = retrieveFile(username, filename);
                            if (content != null) {
                                response = "FILE_DATA|" + filename + "|" + content;
                            } else {
                                response = "ERROR|Arquivo não encontrado: " + filename;
                            }
                        } else {
                            response = "ERROR|Formato FILE_RETRIEVE inválido, esperado: FILE_RETRIEVE|username|filename";
                        }
                        break;
                    
                    case "INFO":
                        response = "INFO|FileStorageService|" + instanceId + "|" + userFiles.size();
                        break;
                        
                    default:
                        response = "ERROR|Unknown action: " + action;
                }
                
                writer.println(response);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "CRÍTICO: Falha ao processar requisição TCP", e);
        }
    }
    
    @Override
    protected void handleUDPRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            String request = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();
            request = request.replaceAll("\0", "");
            
            if (request.startsWith("HEARTBEAT")) {
                sendHeartbeatResponse(clientAddress, clientPort);
                return;
            }
            
            String[] parts = request.split("\\|");
            String action = parts[0].toUpperCase();
            
            String response;
            switch (action) {
                    case "STORE":
                        if (parts.length >= 4) {
                            String username = parts[1];
                            String filename = parts[2];
                            String content = parts[3];
                            
                            storeFile(username, filename, content);
                            response = "SUCCESS|Arquivo armazenado: " + filename;
                        } else {
                            response = "ERROR|STORE requires: STORE|username|filename|content";
                        }
                        break;
                    case "RETRIEVE":
                        if (parts.length >= 3) {
                            String username = parts[1];
                            String filename = parts[2];
                            
                            String content = retrieveFile(username, filename);
                            response = content != null ? "FILE_DATA|" + filename + "|" + content : "ERROR|Arquivo não encontrado: " + filename;
                        } else {
                            response = "ERROR|RETRIEVE requires: RETRIEVE|username|filename";
                        }
                        break;
                    case "INFO":
                        response = "SUCCESS|FileStorageService|" + instanceId + "|" + userFiles.size();
                        break;
                    default:
                        response = "ERROR|Unknown action: " + action;
                }
                
            byte[] responseData = response.getBytes(StandardCharsets.UTF_8);
            DatagramPacket responsePacket = new DatagramPacket(
                responseData, responseData.length, clientAddress, clientPort
            );
            udpServer.send(responsePacket);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "CRÍTICO: Falha ao processar requisição UDP", e);
        }
    }
    
    private void storeFile(String username, String filename, String content) {
        userFiles.computeIfAbsent(username, k -> new ConcurrentHashMap<>())
                 .put(filename, content);
    }
    
    private String retrieveFile(String username, String filename) {
        Map<String, String> files = userFiles.get(username);
        if (files != null) {
            return files.get(filename);
        }
        return null;
    }
}
