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
            
            // Determina o tipo do componente a partir do caminho (ex.: /componentA/action -> componentA)
            String componentType = "componentA"; // Padrão
            if (path.startsWith("/")) {
                String[] pathParts = path.substring(1).split("/");
                if (pathParts.length > 0) {
                    componentType = pathParts[0];
                }
            }
            
            // Cria a requisição completa para encaminhar
            String fullRequest = requestHeader + "\r\n" + requestBody.toString();
            
            // Encaminha a requisição para o componente apropriado
            byte[] response = gateway.routeRequest(componentType, fullRequest.getBytes(), "http");
            
            // Envia a resposta de volta ao cliente
            output.write(response);
            output.flush();
            
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
        try (
            Socket socket = new Socket(component.getHost(), component.getHttpPort());
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Envia a requisição para o componente
            out.write(request);
            out.flush();
            
            // Lê os cabeçalhos da resposta
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            boolean headersComplete = false;
            int contentLength = -1;
            
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
                in.read(bodyBuffer, 0, contentLength);
                responseBuilder.append(bodyBuffer);
            }
            
            return responseBuilder.toString().getBytes();
        }
    }
}