package component.protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente UDP para comunicação entre componentes através do Gateway de API.
 */
public class UDPClient {
    private static final Logger LOGGER = Logger.getLogger(UDPClient.class.getName());
    
    private final String gatewayHost;
    private final int gatewayPort;
    
    // Tamanho máximo de um pacote UDP
    private static final int MAX_PACKET_SIZE = 65507;
    
    // Timeout padrão em milissegundos
    private static final int DEFAULT_TIMEOUT_MS = 5000;
    
    /**
     * Construtor para o cliente UDP.
     * 
     * @param gatewayHost Host do Gateway de API
     * @param gatewayPort Porta UDP do Gateway de API
     */
    public UDPClient(String gatewayHost, int gatewayPort) {
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;
    }
    
    /**
     * Envia uma mensagem para um componente via Gateway de API.
     * 
     * @param componentType Tipo do componente de destino (ex.: "componentA")
     * @param message Mensagem a ser enviada
     * @return Resposta do componente
     * @throws IOException Se ocorrer um erro de I/O
     */
    public String sendMessage(String componentType, String message) throws IOException {
        return sendMessage(componentType, message, DEFAULT_TIMEOUT_MS);
    }
    
    /**
     * Envia uma mensagem para um componente via Gateway de API com um timeout especificado.
     * 
     * @param componentType Tipo do componente de destino (ex.: "componentA")
     * @param message Mensagem a ser enviada
     * @param timeoutMs Timeout em milissegundos
     * @return Resposta do componente
     * @throws IOException Se ocorrer um erro de I/O
     */
    public String sendMessage(String componentType, String message, int timeoutMs) throws IOException {
        // Formato: COMPONENT_TYPE|MENSAGEM
        String formattedMessage = componentType + "|" + message;
        byte[] sendData = formattedMessage.getBytes();
        
        try (DatagramSocket socket = new DatagramSocket()) {
            // Define o timeout
            socket.setSoTimeout(timeoutMs);
            
            // Prepara e envia o pacote
            InetAddress address = InetAddress.getByName(gatewayHost);
            DatagramPacket sendPacket = new DatagramPacket(
                sendData, sendData.length, address, gatewayPort
            );
            socket.send(sendPacket);
            
            // Prepara para receber a resposta
            byte[] receiveData = new byte[MAX_PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            
            // Recebe a resposta
            socket.receive(receivePacket);
            
            // Processa a resposta
            return new String(receivePacket.getData(), 0, receivePacket.getLength());
        } catch (IOException e) {
            // LOGGER.log(Level.SEVERE, "Erro ao enviar mensagem UDP", e);
            throw e;
        }
    }
    
    /**
     * Envia um comando para o Componente A.
     * 
     * @param action Ação a ser realizada (GET, SET, INFO)
     * @param key Chave para operações GET/SET (pode ser null para INFO)
     * @param value Valor para operação SET (pode ser null para outras operações)
     * @return Resposta do Componente A
     * @throws IOException Se ocorrer um erro de I/O
     */
    public String sendToComponentA(String action, String key, String value) throws IOException {
        StringBuilder message = new StringBuilder(action);
        
        if (key != null) {
            message.append("|").append(key);
            
            if (value != null) {
                message.append("|").append(value);
            }
        }
        
        return sendMessage("componentA", message.toString());
    }
    
    /**
     * Envia um comando para o Componente B.
     * 
     * @param action Ação a ser realizada (ADD_EVENT, COUNT, INFO)
     * @param data Dados adicionais para a ação (pode ser null)
     * @return Resposta do Componente B
     * @throws IOException Se ocorrer um erro de I/O
     */
    public String sendToComponentB(String action, String data) throws IOException {
        StringBuilder message = new StringBuilder(action);
        
        if (data != null) {
            message.append("|").append(data);
        }
        
        return sendMessage("componentB", message.toString());
    }
}