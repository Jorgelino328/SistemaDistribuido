package component.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;


public class HTTPClient {
    
    private final String gatewayHost;
    private final int gatewayPort;
    

    public HTTPClient(String gatewayHost, int gatewayPort) {
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;
    }
    
    public String get(String componentType, String path) throws IOException {
        String request = buildGetRequest(componentType, path);
        return sendRequest(request);
    }
    

    public String post(String componentType, String path, String body) throws IOException {
        String request = buildPostRequest(componentType, path, body);
        return sendRequest(request);
    }
    

    public String put(String componentType, String path, String body) throws IOException {
        String request = buildPutRequest(componentType, path, body);
        return sendRequest(request);
    }
    

    public String delete(String componentType, String path) throws IOException {
        String request = buildDeleteRequest(componentType, path);
        return sendRequest(request);
    }
    

    private String buildGetRequest(String componentType, String path) {
        return "GET /" + componentType + path + " HTTP/1.1\r\n" +
               "Host: " + gatewayHost + ":" + gatewayPort + "\r\n" +
               "Connection: close\r\n" +
               "\r\n";
    }
    

    private String buildPostRequest(String componentType, String path, String body) {
        return "POST /" + componentType + path + " HTTP/1.1\r\n" +
               "Host: " + gatewayHost + ":" + gatewayPort + "\r\n" +
               "Connection: close\r\n" +
               "Content-Type: text/plain\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "\r\n" +
               body;
    }
    

    private String buildPutRequest(String componentType, String path, String body) {
        return "PUT /" + componentType + path + " HTTP/1.1\r\n" +
               "Host: " + gatewayHost + ":" + gatewayPort + "\r\n" +
               "Connection: close\r\n" +
               "Content-Type: text/plain\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "\r\n" +
               body;
    }
    

    private String buildDeleteRequest(String componentType, String path) {
        return "DELETE /" + componentType + path + " HTTP/1.1\r\n" +
               "Host: " + gatewayHost + ":" + gatewayPort + "\r\n" +
               "Connection: close\r\n" +
               "\r\n";
    }
    

    private String sendRequest(String request) throws IOException {
        try (
            Socket socket = new Socket(gatewayHost, gatewayPort);
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {

            out.write(request.getBytes());
            out.flush();
            

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append("\n");
            }
            
            return response.toString();
        } catch (IOException e) {

            throw e;
        }
    }
}