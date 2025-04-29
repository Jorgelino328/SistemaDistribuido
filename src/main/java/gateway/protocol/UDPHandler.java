package gateway.protocol;

import gateway.APIGateway;
import common.model.ComponentInfo;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manipulador UDP para o Gateway de API.
 * 
 * Este manipulador recebe requisições UDP e as encaminha para os componentes apropriados.
 */
public class UDPHandler {
    private static final Logger LOGGER = Logger.getLogger(UDPHandler.class.getName());
    
    private final APIGateway gateway;
    private final int port;
    private DatagramSocket socket;
    private final ExecutorService threadPool;
    private boolean isRunning = false;
    
    // Tamanho máximo do pacote UDP
    private static final int MAX_PACKET_SIZE = 65507; // Tamanho máximo permitido para pacotes UDP
    
    /**
     * Construtor para o manipulador UDP.
     * 
     * @param gateway Instância do Gateway de API
     * @param port Porta para o servidor UDP
     */
    public UDPHandler(APIGateway gateway, int port) {
        this.gateway = gateway;
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(50); // Ajuste o tamanho do pool conforme necessário
    }
    
    /**
     * Inicia o servidor UDP.
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        try {
            socket = new DatagramSocket(port);
            isRunning = true;
            
            Thread serverThread = new Thread(() -> {
                byte[] receiveBuffer = new byte[MAX_PACKET_SIZE];
                
                while (isRunning) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                        socket.receive(receivePacket);
                        
                        // Cria uma cópia dos dados antes de processar em uma thread separada
                        byte[] data = new byte[receivePacket.getLength()];
                        System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, receivePacket.getLength());
                        
                        // Obtém o endereço e a porta do cliente para resposta
                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();
                        
                        threadPool.submit(() -> handleRequest(data, clientAddress, clientPort));
                    } catch (IOException e) {
                        if (isRunning) {
                            LOGGER.log(Level.SEVERE, "Erro ao receber pacote UDP", e);
                        }
                    }
                }
            });
            
            serverThread.setDaemon(true);
            serverThread.start();
            
            LOGGER.info("Manipulador UDP iniciado na porta " + port);
        } catch (SocketException e) {
            LOGGER.log(Level.SEVERE, "Falha ao iniciar o manipulador UDP na porta " + port, e);
        }
    }
    
    /**
     * Para o servidor UDP.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        threadPool.shutdown();
        LOGGER.info("Manipulador UDP parado");
    }
    
    /**
     * Lida com uma requisição UDP recebida.
     * 
     * @param data Dados da requisição
     * @param clientAddress Endereço do cliente
     * @param clientPort Porta do cliente
     */
    private void handleRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {
            // Converte os dados da requisição para string
            String request = new String(data);
            
            // Analisa o formato da requisição: "COMPONENT_TYPE|REQUISIÇÃO_REAL"
            String[] parts = request.split("\\|", 2);
            
            if (parts.length >= 2) {
                String componentType = parts[0];
                String actualRequest = parts[1];
                
                // Encaminha a requisição para o componente apropriado
                byte[] response = gateway.routeRequest(componentType, actualRequest.getBytes(), "udp");
                
                // Envia a resposta de volta ao cliente
                DatagramPacket sendPacket = new DatagramPacket(
                    response, response.length, clientAddress, clientPort
                );
                socket.send(sendPacket);
            } else {
                // Envia uma resposta de erro
                String errorMsg = "ERRO: Formato de requisição inválido. Esperado: COMPONENT_TYPE|REQUISIÇÃO_REAL";
                DatagramPacket sendPacket = new DatagramPacket(
                    errorMsg.getBytes(), errorMsg.length(), clientAddress, clientPort
                );
                socket.send(sendPacket);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao lidar com a requisição UDP", e);
        }
    }
    
    /**
     * Encaminha uma requisição UDP para um componente.
     * 
     * @param component Informações do componente de destino
     * @param request Requisição em formato de bytes
     * @return Resposta em formato de bytes
     * @throws IOException Se ocorrer um erro durante o encaminhamento
     */
    public byte[] forwardRequest(ComponentInfo component, byte[] request) throws IOException {
        DatagramSocket clientSocket = new DatagramSocket();
        try {
            // Define o timeout para a resposta
            clientSocket.setSoTimeout(5000); // 5 segundos
            
            // Envia a requisição para o componente
            InetAddress address = InetAddress.getByName(component.getHost());
            DatagramPacket sendPacket = new DatagramPacket(
                request, request.length, address, component.getUdpPort()
            );
            clientSocket.send(sendPacket);
            
            // Recebe a resposta
            byte[] receiveBuffer = new byte[MAX_PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            clientSocket.receive(receivePacket);
            
            // Copia os dados recebidos
            byte[] response = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), 0, response, 0, receivePacket.getLength());
            
            return response;
        } finally {
            clientSocket.close();
        }
    }
}