package component.protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


public class UDPClient {
    
    private final String gatewayHost;
    private final int gatewayPort;
    

    private static final int MAX_PACKET_SIZE = 65507;
    

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    

    public UDPClient(String gatewayHost, int gatewayPort) {
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;
    }
    

    public String sendMessage(String componentType, String message) throws IOException {
        return sendMessage(componentType, message, DEFAULT_TIMEOUT_MS);
    }
    

    public String sendMessage(String componentType, String message, int timeoutMs) throws IOException {

        String formattedMessage = componentType + "|" + message;
        byte[] sendData = formattedMessage.getBytes();
        
        try (DatagramSocket socket = new DatagramSocket()) {

            socket.setSoTimeout(timeoutMs);
            

            InetAddress address = InetAddress.getByName(gatewayHost);
            DatagramPacket sendPacket = new DatagramPacket(
                sendData, sendData.length, address, gatewayPort
            );
            socket.send(sendPacket);
            

            byte[] receiveData = new byte[MAX_PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            

            socket.receive(receivePacket);
            

            return new String(receivePacket.getData(), 0, receivePacket.getLength());
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