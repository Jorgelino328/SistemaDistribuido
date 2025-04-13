package gateway.protocol;

import gateway.APIGateway;
import common.model.ComponentInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manipulador HTTP para o Gateway de API.
 * 
 * Este manipulador recebe requisições HTTP e as encaminha para os componentes apropriados.
 */
public class HTTPHandler {
    private static final Logger LOGGER = Logger.getLogger(HTTPHandler.class.getName());
    
    private final APIGateway gateway;
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private boolean isRunning = false;
    
    /**
     * Construtor para o manipulador HTTP.
     * 
     * @param gateway Instância do Gateway de API
     * @param port Porta para o servidor HTTP
     */
    public HTTPHandler(APIGateway gateway, int port) {
        this.gateway = gateway;
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(10); // Ajuste o tamanho do pool conforme necessário
    }
    
    /**
     * Inicia o servidor HTTP.
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            
            Thread serverThread = new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        threadPool.submit(() -> handleRequest(clientSocket));
                    } catch (IOException e) {
                        if (isRunning) {
                            LOGGER.log(Level.SEVERE, "Erro ao aceitar conexão HTTP", e);
                        }
                    }
                }
            });
            
            serverThread.setDaemon(true);
            serverThread.start();
            
            LOGGER.info("Manipulador HTTP iniciado na porta " + port);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Falha ao iniciar o manipulador HTTP na porta " + port, e);
        }
    }
    
    /**
     * Para o servidor HTTP.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao fechar o socket do servidor HTTP", e);
        }
        
        threadPool.shutdown();
        LOGGER.info("Manipulador HTTP parado");
    }
    
    /**
     * Lida com uma requisição HTTP recebida.
     * 
     * @param clientSocket Socket do cliente
     */
    private void handleRequest(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream output = clientSocket.getOutputStream()
        ) {
            // Lê a requisição
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                requestBuilder.append(line).append("\r\n");
            }
            
            // Lê o corpo da requisição, se presente
            int contentLength = 0;
            String requestHeader = requestBuilder.toString();
            if (requestHeader.contains("Content-Length:")) {
                String contentLengthStr = requestHeader.substring(
                    requestHeader.indexOf("Content-Length:") + 16,
                    requestHeader.indexOf("\r\n", requestHeader.indexOf("Content-Length:"))
                );
                contentLength = Integer.parseInt(contentLengthStr.trim());
            }
            
            StringBuilder requestBody = new StringBuilder();
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                reader.read(buffer, 0, contentLength);
                requestBody.append(buffer);
            }
            
            // Analisa a requisição para extrair o componente de destino
            String firstLine = requestHeader.substring(0, requestHeader.indexOf("\r\n"));
            String[] parts = firstLine.split(" ");
            String path = parts[1];
            
            // Log da requisição recebida para depuração
            LOGGER.info("Requisição HTTP recebida: " + firstLine);
            
            // Tratamento especial para favicon.ico
            if (path.equals("/favicon.ico")) {
                String response = "HTTP/1.1 200 OK\r\nContent-Type: image/x-icon\r\nContent-Length: 0\r\n\r\n";
                output.write(response.getBytes());
                return;
            }
            
            // Tratamento para raiz (/)
            if (path.equals("/")) {
                // Retorna uma página HTML simples listando os componentes disponíveis
                String html = "<html><body><h1>API Gateway</h1>" +
                              "<p>Componentes disponíveis:</p><ul>" +
                              "<li><a href=\"/componenta/info\">Componente A</a></li>" +
                              "<li><a href=\"/componentb/info\">Componente B</a></li>" +
                              "</ul></body></html>";
                
                String response = "HTTP/1.1 200 OK\r\n" +
                                  "Content-Type: text/html\r\n" +
                                  "Content-Length: " + html.length() + "\r\n\r\n" +
                                  html;
                output.write(response.getBytes());
                return;
            }
            
            // Determina o tipo do componente a partir do caminho
            String componentType = ""; 
            if (path.startsWith("/")) {
                String[] pathParts = path.substring(1).split("/");
                if (pathParts.length > 0 && !pathParts[0].isEmpty()) {
                    componentType = pathParts[0].toLowerCase();
                }
            }
            
            LOGGER.info("Encaminhando para o componente: " + componentType);
            
            // Cria a requisição completa para encaminhar
            String fullRequest = requestHeader + "\r\n" + requestBody.toString();
            
            try {
                // Encaminha a requisição para o componente apropriado
                byte[] response = gateway.routeRequest(componentType, fullRequest.getBytes(), "http");
                
                // Se obteve uma resposta, envia-a de volta
                if (response != null && response.length > 0) {
                    output.write(response);
                    output.flush();
                    
                    // Log da resposta para depuração
                    String responseStr = new String(response);
                    LOGGER.info("Resposta enviada: " + 
                                responseStr.substring(0, Math.min(100, responseStr.length())) + 
                                (responseStr.length() > 100 ? "..." : ""));
                } else {
                    // Envia um 404 se nenhuma resposta foi retornada
                    String notFoundResponse = "HTTP/1.1 404 Not Found\r\n" +
                                              "Content-Type: text/plain\r\n" +
                                              "Content-Length: 24\r\n\r\n" +
                                              "Componente não encontrado";
                    output.write(notFoundResponse.getBytes());
                    output.flush();
                    LOGGER.warning("Nenhuma resposta recebida do componente: " + componentType);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao encaminhar requisição para o componente " + componentType, e);
                String errorResponse = "HTTP/1.1 500 Internal Server Error\r\n" +
                                      "Content-Type: text/plain\r\n" +
                                      "Content-Length: " + e.getMessage().length() + "\r\n\r\n" +
                                      e.getMessage();
                output.write(errorResponse.getBytes());
                output.flush();
            }
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao lidar com a requisição HTTP", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Erro ao fechar o socket do cliente", e);
            }
        }
    }
    
    /**
     * Encaminha uma requisição HTTP para um componente.
     * 
     * @param component Informações do componente de destino
     * @param request Requisição em formato de bytes
     * @return Resposta em formato de bytes
     * @throws IOException Se ocorrer um erro durante o encaminhamento
     */
    public byte[] forwardRequest(ComponentInfo component, byte[] request) throws IOException {
        LOGGER.info("Encaminhando requisição HTTP para " + component.getHost() + ":" + component.getHttpPort());
        
        try (
            Socket socket = new Socket(component.getHost(), component.getHttpPort());
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Define o timeout do socket para evitar bloqueios indefinidos
            socket.setSoTimeout(5000);
            
            // Envia a requisição para o componente
            out.write(request);
            out.flush();
            
            // Lê os cabeçalhos da resposta
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            boolean headersComplete = false;
            int contentLength = -1;
            
            // Lê a linha de status primeiro
            String statusLine = in.readLine();
            if (statusLine == null) {
                // Sem resposta do componente
                return "HTTP/1.1 504 Gateway Timeout\r\nContent-Length: 29\r\n\r\nComponente não respondeu".getBytes();
            }
            
            responseBuilder.append(statusLine).append("\r\n");
            
            // Lê os cabeçalhos
            while ((line = in.readLine()) != null) {
                responseBuilder.append(line).append("\r\n");
                
                // Procura pelo cabeçalho Content-Length
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring(16).trim());
                }
                
                // Linha vazia indica o fim dos cabeçalhos
                if (line.isEmpty()) {
                    headersComplete = true;
                    break;
                }
            }
            
            // Lê o corpo, se Content-Length for fornecido
            if (headersComplete && contentLength > 0) {
                char[] bodyBuffer = new char[contentLength];
                int charsRead = in.read(bodyBuffer, 0, contentLength);
                
                // Adiciona apenas o que foi realmente lido
                if (charsRead > 0) {
                    responseBuilder.append(bodyBuffer, 0, charsRead);
                }
            }
            // Se não houver content length mas os cabeçalhos estiverem completos, tenta ler até EOF
            else if (headersComplete && contentLength == -1) {
                StringBuilder bodyBuilder = new StringBuilder();
                char[] buffer = new char[1024];
                int charsRead;
                
                while ((charsRead = in.read(buffer)) != -1) {
                    bodyBuilder.append(buffer, 0, charsRead);
                }
                
                // Adiciona o corpo somente se algo foi lido
                if (bodyBuilder.length() > 0) {
                    // Adiciona um cabeçalho content-length
                    String body = bodyBuilder.toString();
                    String headers = responseBuilder.toString();
                    
                    // Insere o cabeçalho Content-Length antes da linha em branco
                    int insertPos = headers.lastIndexOf("\r\n\r\n");
                    if (insertPos > 0) {
                        headers = headers.substring(0, insertPos) + 
                                 "\r\nContent-Length: " + body.length() + 
                                 headers.substring(insertPos);
                    }
                    
                    responseBuilder = new StringBuilder(headers);
                    responseBuilder.append(body);
                }
            }
            
            // Log da primeira parte da resposta para depuração
            String responseStr = responseBuilder.toString();
            LOGGER.info("Resposta do componente: " + 
                        responseStr.substring(0, Math.min(100, responseStr.length())) + 
                        (responseStr.length() > 100 ? "..." : ""));
            
            return responseBuilder.toString().getBytes();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao encaminhar requisição para o componente: " + e.getMessage(), e);
            String errorResponse = "HTTP/1.1 502 Bad Gateway\r\n" +
                                  "Content-Type: text/plain\r\n" +
                                  "Content-Length: " + e.getMessage().length() + "\r\n\r\n" +
                                  e.getMessage();
            return errorResponse.getBytes();
        }
    }
}