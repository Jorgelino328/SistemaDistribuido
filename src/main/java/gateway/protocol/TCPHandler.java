package gateway.protocol;

import gateway.APIGateway;
import common.model.ComponentInfo;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class TCPHandler {
    
    private final APIGateway gateway;
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private boolean isRunning = false;
    

    public TCPHandler(APIGateway gateway, int port) {
        this.gateway = gateway;
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(50); 
    }
    
 
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

                        }
                    }
                }
            });
            
            serverThread.setDaemon(true);
            serverThread.start();
            

        } catch (IOException e) {

        }
    }
    

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

        }
        
        threadPool.shutdown();

    }
    

    private void handleRequest(Socket clientSocket) {
        try (
            Socket socket = clientSocket;
            InputStream in = socket.getInputStream();
            OutputStream output = socket.getOutputStream()
        ) {
            socket.setSoTimeout(10000);
            
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') { break; }
                buffer.write(b);
            }

            String request = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
            if (request != null) request = request.replace("\r", "");

            if (request != null && !request.isEmpty()) {
                String[] parts = request.split("\\|", 2);
                if (parts.length >= 2) {
                    String componentType = parts[0];
                    String actualRequest = parts[1];

                    byte[] response = gateway.routeRequest(componentType, actualRequest.getBytes(), "tcp");

                    if (response == null || response.length == 0) {
                        output.write('\n');
                    } else if (response[response.length - 1] == (byte) '\n') {
                        output.write(response);
                    } else {
                        output.write(response);
                        output.write('\n');
                    }
                    output.flush();
                } else {
                    String err = "ERRO: Formato de requisição inválido. Esperado: COMPONENT_TYPE|REQUISIÇÃO_REAL\n";
                    output.write(err.getBytes());
                    output.flush();
                }
            }
            
        } catch (IOException e) {
        }
    }
    

    public byte[] forwardRequest(ComponentInfo component, byte[] request) throws IOException {
        try (
            Socket socket = new Socket(component.getHost(), component.getTcpPort());
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {

            out.println(new String(request));
            

            String response = in.readLine();
            if (response == null) {
                return "Sem resposta\n".getBytes();
            }

            if (response.endsWith("\n")) {
                return response.getBytes();
            } else {
                return (response + "\n").getBytes();
            }
        }
    }
}