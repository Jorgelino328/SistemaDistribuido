package component.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


public class TCPClient {
    
    private final String gatewayHost;
    private final int gatewayPort;
    

    public TCPClient(String gatewayHost, int gatewayPort) {
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;
    }
    

    public String sendMessage(String componentType, String message) throws IOException {

        String formattedMessage = componentType + "|" + message;
        
        try (
            Socket socket = new Socket(gatewayHost, gatewayPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {

            out.println(formattedMessage);
            

            String response = in.readLine();
            return response;
        } catch (IOException e) {

            throw e;
        }
    }
    

    public String sendToUserService(String action, String key, String value) throws IOException {
        StringBuilder message = new StringBuilder(action);
        
        if (key != null) {
            message.append("|").append(key);
            
            if (value != null) {
                message.append("|").append(value);
            }
        }
        
        return sendMessage("userservice", message.toString());
    }
    

    public String sendToFileService(String action, String data) throws IOException {
        StringBuilder message = new StringBuilder(action);
        
        if (data != null) {
            message.append("|").append(data);
        }
        
        return sendMessage("fileservice", message.toString());
    }
}