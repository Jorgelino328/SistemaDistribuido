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

/**
 * Implementação do Componente A - simula um serviço simples de armazenamento chave-valor.
 */
public class ComponentA extends BaseComponent {
    private static final Logger LOGGER = Logger.getLogger(ComponentA.class.getName());
    
    // Armazenamento chave-valor em memória
    private final Map<String, String> dataStore = new ConcurrentHashMap<>();
    
    // Identificador da instância para fins de registro
    private final String instanceId;
    
    /**
     * Construtor para o Componente A.
     */
    public ComponentA(String host, int httpPort, int tcpPort, int udpPort, int grpcPort,
                      String gatewayHost, int gatewayRegistrationPort) {
        super("componentA", host, httpPort, tcpPort, udpPort, grpcPort, 
              gatewayHost, gatewayRegistrationPort);
        
        // Gera um ID único para a instância
        this.instanceId = UUID.randomUUID().toString().substring(0, 8);
        
        // Adiciona alguns dados iniciais
        dataStore.put("example", "Este é um valor de exemplo do Componente A");
        dataStore.put("instance", instanceId);
    }
    
    @Override
    public void start() {
        LOGGER.info("Iniciando instância do Componente A " + instanceId + "...");
        super.start();
    }
    
    @Override
    protected void handleHTTPRequest(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream output = clientSocket.getOutputStream()
        ) {
            // Lê a requisição HTTP
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                requestBuilder.append(line).append("\r\n");
            }
            
            // Analisa a requisição
            String request = requestBuilder.toString();
            String firstLine = request.substring(0, request.indexOf("\r\n"));
            String[] parts = firstLine.split(" ");
            String method = parts[0];
            String path = parts[1];
            
            // Processa a requisição
            String response;
            if (path.startsWith("/get/")) {
                // Requisição GET - formato: /get/{key}
                String key = path.substring(5);
                String value = dataStore.getOrDefault(key, "Chave não encontrada");
                response = buildHTTPResponse("200 OK", "text/plain", value);
            } else if (method.equals("POST") && path.startsWith("/set/")) {
                // Requisição POST - formato: /set/{key} com valor no corpo
                String key = path.substring(5);
                
                // Lê o corpo da requisição, se presente
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
                
                // Armazena o valor
                String value = valueBuilder.toString();
                dataStore.put(key, value);
                response = buildHTTPResponse("200 OK", "text/plain", "Valor armazenado para a chave: " + key);
            } else if (path.equals("/info")) {
                // Retorna informações do componente
                String info = "Instância do Componente A " + instanceId + "\n" +
                              "Tamanho do armazenamento: " + dataStore.size() + " entradas\n" +
                              "Executando em: " + host + "\n" +
                              "Porta HTTP: " + httpPort;
                response = buildHTTPResponse("200 OK", "text/plain", info);
            } else {
                // Endpoint desconhecido
                response = buildHTTPResponse("404 Not Found", "text/plain", "Endpoint desconhecido");
            }
            
            // Envia a resposta
            output.write(response.getBytes());
            output.flush();
            
            LOGGER.info("Componente A[" + instanceId + "] processou requisição HTTP: " + firstLine);
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
                // Analisa o formato da requisição: ACTION|KEY|VALUE (VALUE é opcional)
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
                    case "LIST":
                        StringBuilder keyList = new StringBuilder();
                        for (String key : dataStore.keySet()) {
                            keyList.append(key).append(",");
                        }
                        response = "KEYS|" + (keyList.length() > 0 ? 
                                  keyList.substring(0, keyList.length() - 1) : "");
                        break;
                    case "INFO":
                        response = "INFO|Componente A|" + instanceId + "|" + dataStore.size();
                        break;
                    default:
                        response = "ERROR|Ação desconhecida: " + action;
                }
                
                writer.println(response);
                LOGGER.info("Componente A[" + instanceId + "] processou requisição TCP: " + action);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao processar requisição TCP no Componente A", e);
        }
    }
    
    @Override
    protected void handleUDPRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            String request = new String(data);
            
            // Analisa o formato da requisição: ACTION|KEY|VALUE (VALUE é opcional)
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
            
            // Envia a resposta
            byte[] responseData = response.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(
                responseData, responseData.length, clientAddress, clientPort
            );
            udpServer.send(responsePacket);
            
            LOGGER.info("Componente A[" + instanceId + "] processou requisição UDP: " + action);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao processar requisição UDP no Componente A", e);
        }
    }
    
    /**
     * Constrói uma resposta HTTP.
     */
    private String buildHTTPResponse(String status, String contentType, String body) {
        return "HTTP/1.1 " + status + "\r\n" +
               "Content-Type: " + contentType + "\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "Connection: close\r\n" +
               "\r\n" +
               body;
    }
    
    /**
     * Método principal para executar o Componente A de forma independente.
     */
    public static void main(String[] args) {
        // Valores padrão
        String host = "localhost";
        int httpPort = 8081;
        int tcpPort = 8082;
        int udpPort = 8083;
        int grpcPort = 8084;
        String gatewayHost = "localhost";
        int gatewayRegistrationPort = 8000;
        
        // Analisa argumentos da linha de comando, se fornecidos
        if (args.length >= 7) {
            host = args[0];
            httpPort = Integer.parseInt(args[1]);
            tcpPort = Integer.parseInt(args[2]);
            udpPort = Integer.parseInt(args[3]);
            grpcPort = Integer.parseInt(args[4]);
            gatewayHost = args[5];
            gatewayRegistrationPort = Integer.parseInt(args[6]);
        }
        
        // Cria e inicia o componente
        ComponentA component = new ComponentA(
            host, httpPort, tcpPort, udpPort, grpcPort, gatewayHost, gatewayRegistrationPort
        );
        component.start();
        
        // Adiciona um hook para desligamento
        Runtime.getRuntime().addShutdownHook(new Thread(component::stop));
        
        LOGGER.info("Componente A iniciado com as portas - HTTP: " + httpPort + 
                   ", TCP: " + tcpPort + ", UDP: " + udpPort + ", gRPC: " + grpcPort);
    }
}