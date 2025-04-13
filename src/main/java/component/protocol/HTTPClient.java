package component.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente HTTP para comunicação entre componentes através do Gateway de API.
 */
public class HTTPClient {
    private static final Logger LOGGER = Logger.getLogger(HTTPClient.class.getName());
    
    private final String gatewayHost;
    private final int gatewayPort;
    
    /**
     * Construtor para o cliente HTTP.
     * 
     * @param gatewayHost Host do Gateway de API
     * @param gatewayPort Porta HTTP do Gateway de API
     */
    public HTTPClient(String gatewayHost, int gatewayPort) {
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;
    }
    
    /**
     * Envia uma requisição GET para um componente via Gateway de API.
     * 
     * @param componentType Tipo do componente de destino (ex.: "componentA")
     * @param path Caminho da requisição
     * @return Resposta do componente
     * @throws IOException Se ocorrer um erro de I/O
     */
    public String get(String componentType, String path) throws IOException {
        String request = buildGetRequest(componentType, path);
        return sendRequest(request);
    }
    
    /**
     * Envia uma requisição POST para um componente via Gateway de API.
     * 
     * @param componentType Tipo do componente de destino (ex.: "componentA")
     * @param path Caminho da requisição
     * @param body Conteúdo do corpo da requisição
     * @return Resposta do componente
     * @throws IOException Se ocorrer um erro de I/O
     */
    public String post(String componentType, String path, String body) throws IOException {
        String request = buildPostRequest(componentType, path, body);
        return sendRequest(request);
    }
    
    /**
     * Envia uma requisição PUT para um componente via Gateway de API.
     * 
     * @param componentType Tipo do componente de destino (ex.: "componentA")
     * @param path Caminho da requisição
     * @param body Conteúdo do corpo da requisição
     * @return Resposta do componente
     * @throws IOException Se ocorrer um erro de I/O
     */
    public String put(String componentType, String path, String body) throws IOException {
        String request = buildPutRequest(componentType, path, body);
        return sendRequest(request);
    }
    
    /**
     * Envia uma requisição DELETE para um componente via Gateway de API.
     * 
     * @param componentType Tipo do componente de destino (ex.: "componentA")
     * @param path Caminho da requisição
     * @return Resposta do componente
     * @throws IOException Se ocorrer um erro de I/O
     */
    public String delete(String componentType, String path) throws IOException {
        String request = buildDeleteRequest(componentType, path);
        return sendRequest(request);
    }
    
    /**
     * Constrói uma requisição GET.
     */
    private String buildGetRequest(String componentType, String path) {
        return "GET /" + componentType + path + " HTTP/1.1\r\n" +
               "Host: " + gatewayHost + ":" + gatewayPort + "\r\n" +
               "Connection: close\r\n" +
               "\r\n";
    }
    
    /**
     * Constrói uma requisição POST.
     */
    private String buildPostRequest(String componentType, String path, String body) {
        return "POST /" + componentType + path + " HTTP/1.1\r\n" +
               "Host: " + gatewayHost + ":" + gatewayPort + "\r\n" +
               "Connection: close\r\n" +
               "Content-Type: text/plain\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "\r\n" +
               body;
    }
    
    /**
     * Constrói uma requisição PUT.
     */
    private String buildPutRequest(String componentType, String path, String body) {
        return "PUT /" + componentType + path + " HTTP/1.1\r\n" +
               "Host: " + gatewayHost + ":" + gatewayPort + "\r\n" +
               "Connection: close\r\n" +
               "Content-Type: text/plain\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "\r\n" +
               body;
    }
    
    /**
     * Constrói uma requisição DELETE.
     */
    private String buildDeleteRequest(String componentType, String path) {
        return "DELETE /" + componentType + path + " HTTP/1.1\r\n" +
               "Host: " + gatewayHost + ":" + gatewayPort + "\r\n" +
               "Connection: close\r\n" +
               "\r\n";
    }
    
    /**
     * Envia uma requisição HTTP e recebe a resposta.
     */
    private String sendRequest(String request) throws IOException {
        try (
            Socket socket = new Socket(gatewayHost, gatewayPort);
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // Envia a requisição
            out.write(request.getBytes());
            out.flush();
            
            // Lê a resposta
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append("\n");
            }
            
            return response.toString();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao enviar requisição HTTP", e);
            throw e;
        }
    }
}