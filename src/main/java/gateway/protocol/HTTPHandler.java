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


public class HTTPHandler {
    
    private final APIGateway gateway;
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private boolean isRunning = false;
    

    public HTTPHandler(APIGateway gateway, int port) {
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream output = clientSocket.getOutputStream()
        ) {

            StringBuilder requestBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                requestBuilder.append(line).append("\r\n");
            }
            

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
            

            String firstLine = requestHeader.substring(0, requestHeader.indexOf("\r\n"));
            String[] parts = firstLine.split(" ");
            String path = parts[1];
            


            

            if (path.equals("/favicon.ico")) {
                String response = "HTTP/1.1 200 OK\r\nContent-Type: image/x-icon\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                output.write(response.getBytes());
                output.flush();
                return;
            }
            

            if (path.equals("/")) {
                String html = "<html><body><h1>API Gateway</h1>" +
                              "<p>Componentes disponíveis:</p><ul>" +
                              "<li><a href=\"/userservice/info\">User Service</a></li>" +
                              "<li><a href=\"/fileservice/info\">File Storage Service</a></li>" +
                              "</ul></body></html>";
                
                String response = "HTTP/1.1 200 OK\r\n" +
                                  "Content-Type: text/html\r\n" +
                                  "Content-Length: " + html.length() + "\r\n" +
                                  "Connection: close\r\n\r\n" +
                                  html;
                output.write(response.getBytes());
                output.flush();
                return;
            }
            




            String componentType = ""; 
            String newPath = path;

            if (path.startsWith("/")) {
                String[] pathParts = path.substring(1).split("/", 2);
                if (pathParts.length > 0 && !pathParts[0].isEmpty()) {
                    String rawType = pathParts[0].toLowerCase();
                    if (rawType.equals("userservice")) {
                        componentType = "userservice";
                    } else if (rawType.equals("fileservice")) {
                        componentType = "fileservice";
                    } else {
                        componentType = rawType;
                    }
                    
                    newPath = pathParts.length > 1 ? "/" + pathParts[1] : "/";
                }
            }


            

            String modifiedFirstLine = parts[0] + " " + newPath + " " + parts[2];
            String modifiedRequestHeader = requestHeader.replace(firstLine, modifiedFirstLine);
            

            String fullRequest = modifiedRequestHeader + "\r\n" + requestBody.toString();
            
            try {

                byte[] response = gateway.routeRequest(componentType, fullRequest.getBytes(), "http");
                

                if (response != null && response.length > 0) {
                    output.write(response);
                    output.flush();
                } else {

                    String notFoundResponse = "HTTP/1.1 404 Not Found\r\n" +
                                              "Content-Type: text/plain\r\n" +
                                              "Content-Length: 24\r\n\r\n" +
                                              "Componente não encontrado";
                    output.write(notFoundResponse.getBytes());
                    output.flush();

                }
            } catch (Exception e) {

                String errorResponse = "HTTP/1.1 500 Internal Server Error\r\n" +
                                      "Content-Type: text/plain\r\n" +
                                      "Content-Length: " + e.getMessage().length() + "\r\n\r\n" +
                                      e.getMessage();
                output.write(errorResponse.getBytes());
                output.flush();
            }
        } catch (IOException e) {

        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {

            }
        }
    }
    

    public byte[] forwardRequest(ComponentInfo component, byte[] request) throws IOException {

        try (
            Socket socket = new Socket(component.getHost(), component.getHttpPort());
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {

            socket.setSoTimeout(5000);
            

            out.write(request);
            out.flush();
            

            StringBuilder responseBuilder = new StringBuilder();
            String line;
            boolean headersComplete = false;
            int contentLength = -1;
            

            String statusLine = in.readLine();
            if (statusLine == null) {

                return "HTTP/1.1 504 Gateway Timeout\r\nContent-Length: 29\r\n\r\nComponente não respondeu".getBytes();
            }
            responseBuilder.append(statusLine).append("\r\n");
            

            while ((line = in.readLine()) != null) {
                responseBuilder.append(line).append("\r\n");
                

                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring(16).trim());
                }
                

                if (line.isEmpty()) {
                    // Insert Connection: close before the empty line
                    int lastNewline = responseBuilder.lastIndexOf("\r\n\r\n");
                    if (lastNewline > 0) {
                        responseBuilder.insert(lastNewline, "Connection: close\r\n");
                    }
                    headersComplete = true;
                    break;
                }
            }
            

            if (headersComplete && contentLength > 0) {
                char[] bodyBuffer = new char[contentLength];
                int charsRead = in.read(bodyBuffer, 0, contentLength);

                if (charsRead > 0) {
                    responseBuilder.append(bodyBuffer, 0, charsRead);
                }
            }

            else if (headersComplete && contentLength == -1) {
                StringBuilder bodyBuilder = new StringBuilder();
                char[] buffer = new char[1024];
                int charsRead;
                
                while ((charsRead = in.read(buffer)) != -1) {
                    bodyBuilder.append(buffer, 0, charsRead);
                }
                

                if (bodyBuilder.length() > 0) {

                    String body = bodyBuilder.toString();
                    String headers = responseBuilder.toString();
                    

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
            

            return responseBuilder.toString().getBytes();
        } catch (IOException e) {

            String errorResponse = "HTTP/1.1 502 Bad Gateway\r\n" +
                                  "Content-Type: text/plain\r\n" +
                                  "Content-Length: " + e.getMessage().length() + "\r\n\r\n" +
                                  e.getMessage();
            return errorResponse.getBytes();
        }
    }
}