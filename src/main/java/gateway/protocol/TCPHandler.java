package gateway.protocol;

import gateway.APIGateway;
import common.model.ComponentInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manipulador TCP para o Gateway de API.
 * 
 * Este manipulador recebe requisições TCP e as encaminha para os componentes apropriados.
 */
public class TCPHandler {
    private static final Logger LOGGER = Logger.getLogger(TCPHandler.class.getName());
    
    private final APIGateway gateway;
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private boolean isRunning = false;
    
    /**
     * Construtor para o manipulador TCP.
     * 
     * @param gateway Instância do Gateway de API
     * @param port Porta para o servidor TCP
     */
    public TCPHandler(APIGateway gateway, int port) {
        this.gateway = gateway;
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(50); // Ajuste o tamanho do pool conforme necessário
    }
    
    /**
     * Inicia o servidor TCP.
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
                            // LOGGER.log(Level.SEVERE, "Erro ao aceitar conexão TCP", e);
                        }
                    }
                }
            });
            
            serverThread.setDaemon(true);
            serverThread.start();
            
            // LOGGER.info("Manipulador TCP iniciado na porta " + port);
        } catch (IOException e) {
            // LOGGER.log(Level.SEVERE, "Falha ao iniciar o manipulador TCP na porta " + port, e);
        }
    }
    
    /**
     * Para o servidor TCP.
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
            // LOGGER.log(Level.WARNING, "Erro ao fechar o socket do servidor TCP", e);
        }
        
        threadPool.shutdown();
        // LOGGER.info("Manipulador TCP parado");
    }
    
    /**
     * Lida com uma requisição TCP recebida.
     * 
     * @param clientSocket Socket do cliente
     */
    private void handleRequest(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true)
        ) {
            // Lê a requisição
            String request = reader.readLine();
            
            if (request != null && !request.isEmpty()) {
                // Analisa o formato da requisição: "COMPONENT_TYPE|REQUISIÇÃO_REAL"
                String[] parts = request.split("\\|", 2);
                
                if (parts.length >= 2) {
                    String componentType = parts[0];
                    String actualRequest = parts[1];
                    
                    // Encaminha a requisição para o componente apropriado
                    byte[] response = gateway.routeRequest(componentType, actualRequest.getBytes(), "tcp");
                    
                    // Envia a resposta de volta ao cliente
                    writer.println(new String(response));
                } else {
                    writer.println("ERRO: Formato de requisição inválido. Esperado: COMPONENT_TYPE|REQUISIÇÃO_REAL");
                }
            }
        } catch (IOException e) {
            // LOGGER.log(Level.WARNING, "Erro ao lidar com a requisição TCP", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // LOGGER.log(Level.WARNING, "Erro ao fechar o socket do cliente", e);
            }
        }
    }
    
    /**
     * Encaminha uma requisição TCP para um componente.
     * 
     * @param component Informações do componente de destino
     * @param request Requisição em formato de bytes
     * @return Resposta em formato de bytes
     * @throws IOException Se ocorrer um erro durante o encaminhamento
     */
    public byte[] forwardRequest(ComponentInfo component, byte[] request) throws IOException {
        try (
            Socket socket = new Socket(component.getHost(), component.getTcpPort());
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Envia a requisição para o componente
            out.println(new String(request));
            
            // Lê a resposta
            String response = in.readLine();
            return response != null ? response.getBytes() : "Sem resposta".getBytes();
        }
    }
}