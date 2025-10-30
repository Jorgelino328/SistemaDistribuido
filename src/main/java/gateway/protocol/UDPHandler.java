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


public class UDPHandler {
    
    private final APIGateway gateway;
    private final int port;
    private DatagramSocket socket;
    private final ExecutorService threadPool;
    private boolean isRunning = false;
    

    private static final int MAX_PACKET_SIZE = 65507; 
    

    public UDPHandler(APIGateway gateway, int port) {
        this.gateway = gateway;
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(50); 
    }
    

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
                        

                        byte[] data = new byte[receivePacket.getLength()];
                        System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), data, 0, receivePacket.getLength());
                        

                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();
                        
                        threadPool.submit(() -> handleRequest(data, clientAddress, clientPort));
                    } catch (IOException e) {
                        if (isRunning) {

                        }
                    }
                }
            });
            
            serverThread.setDaemon(true);
            serverThread.start();
            

        } catch (SocketException e) {

        }
    }
    

    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        threadPool.shutdown();

    }
    

    private void handleRequest(byte[] data, InetAddress clientAddress, int clientPort) {
        try {

            String request = new String(data);
            

            String[] parts = request.split("\\|", 2);
            
            if (parts.length >= 2) {
                String componentType = parts[0];
                String actualRequest = parts[1];
                

                byte[] response = gateway.routeRequest(componentType, actualRequest.getBytes(), "udp");
                

                DatagramPacket sendPacket = new DatagramPacket(
                    response, response.length, clientAddress, clientPort
                );
                socket.send(sendPacket);
            } else {

                String errorMsg = "ERRO: Formato de requisição inválido. Esperado: COMPONENT_TYPE|REQUISIÇÃO_REAL";
                DatagramPacket sendPacket = new DatagramPacket(
                    errorMsg.getBytes(), errorMsg.length(), clientAddress, clientPort
                );
                socket.send(sendPacket);
            }
        } catch (IOException e) {

        }
    }
    
 
    public byte[] forwardRequest(ComponentInfo component, byte[] request) throws IOException {
        DatagramSocket clientSocket = new DatagramSocket();
        try {

            clientSocket.setSoTimeout(5000); // 5 segundos
            

            InetAddress address = InetAddress.getByName(component.getHost());
            DatagramPacket sendPacket = new DatagramPacket(
                request, request.length, address, component.getUdpPort()
            );
            clientSocket.send(sendPacket);
            

            byte[] receiveBuffer = new byte[MAX_PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            clientSocket.receive(receivePacket);
            

            byte[] response = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), 0, response, 0, receivePacket.getLength());
            
            return response;
        } finally {
            clientSocket.close();
        }
    }
}