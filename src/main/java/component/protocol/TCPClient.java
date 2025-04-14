package component.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente TCP para comunicação entre componentes através do Gateway de API.
 */
public class TCPClient {
    private static final Logger LOGGER = Logger.getLogger(TCPClient.class.getName());
    
    private final String gatewayHost;
    private final int gatewayPort;
    
    /**
     * Construtor para o cliente TCP.
     * 
     * @param gatewayHost Host do Gateway de API
     * @param gatewayPort Porta TCP do Gateway de API
     */
    public TCPClient(String gatewayHost, int gatewayPort) {
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
        // Formato: COMPONENT_TYPE|MENSAGEM
        String formattedMessage = componentType + "|" + message;
        
        try (
            Socket socket = new Socket(gatewayHost, gatewayPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Envia a mensagem
            out.println(formattedMessage);
            
            // Lê a resposta
            String response = in.readLine();
            return response;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao enviar mensagem TCP", e);
            throw e;
        }
    }
    
    /**
     * Envia um comando para o Componente A.
     * 
     * @param action Ação a ser realizada (GET, SET, LIST, INFO)
     * @param key Chave para operações GET/SET (pode ser null para LIST/INFO)
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
     * @param action Ação a ser realizada (ADD_EVENT, GET_EVENTS, COUNT, INFO)
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