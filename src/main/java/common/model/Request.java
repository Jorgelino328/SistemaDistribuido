package common.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Classe que representa uma mensagem de requisição genérica no sistema distribuído.
 * Usada para comunicação entre componentes e o Gateway de API.
 */
public class Request implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Tipos de requisição
    public enum Type {
        GET,        // Operação de leitura
        POST,       // Operação de criação
        PUT,        // Operação de atualização
        DELETE,     // Operação de exclusão
        INFO,       // Solicitação de informações
        CUSTOM      // Operação personalizada
    }
    
    // Metadados da requisição
    private final String id;
    private final Type type;
    private final long timestamp;
    private final String sourceComponent;
    private final String targetComponent;
    
    // Dados da requisição
    private final String path;
    private final Map<String, String> headers;
    private final String body;
    
    // Rastreamento de resposta
    private boolean processed = false;
    private String responseBody;
    private int responseCode = 200;
    
    /**
     * Construtor para criar uma requisição.
     * 
     * @param type Tipo da requisição
     * @param sourceComponent Identificador do componente de origem
     * @param targetComponent Identificador do componente de destino
     * @param path Caminho ou identificador do recurso da requisição
     * @param headers Cabeçalhos da requisição
     * @param body Corpo da requisição
     */
    public Request(Type type, String sourceComponent, String targetComponent, 
                   String path, Map<String, String> headers, String body) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.sourceComponent = sourceComponent;
        this.targetComponent = targetComponent;
        this.path = path;
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.body = body;
    }
    
    /**
     * Cria um builder para a classe Request.
     * 
     * @return Um novo RequestBuilder
     */
    public static RequestBuilder builder() {
        return new RequestBuilder();
    }
    
    /**
     * Obtém o ID da requisição.
     * 
     * @return ID da requisição
     */
    public String getId() {
        return id;
    }
    
    /**
     * Obtém o tipo da requisição.
     * 
     * @return Tipo da requisição
     */
    public Type getType() {
        return type;
    }
    
    /**
     * Obtém o timestamp da requisição.
     * 
     * @return Timestamp da requisição
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Obtém o identificador do componente de origem.
     * 
     * @return Componente de origem
     */
    public String getSourceComponent() {
        return sourceComponent;
    }
    
    /**
     * Obtém o identificador do componente de destino.
     * 
     * @return Componente de destino
     */
    public String getTargetComponent() {
        return targetComponent;
    }
    
    /**
     * Obtém o caminho da requisição.
     * 
     * @return Caminho da requisição
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Obtém os cabeçalhos da requisição.
     * 
     * @return Mapa de cabeçalhos da requisição
     */
    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }
    
    /**
     * Obtém o valor de um cabeçalho específico.
     * 
     * @param name Nome do cabeçalho
     * @return Valor do cabeçalho, ou null se não encontrado
     */
    public String getHeader(String name) {
        return headers.get(name);
    }
    
    /**
     * Obtém o corpo da requisição.
     * 
     * @return Corpo da requisição
     */
    public String getBody() {
        return body;
    }
    
    /**
     * Verifica se a requisição foi processada.
     * 
     * @return true se processada, false caso contrário
     */
    public boolean isProcessed() {
        return processed;
    }
    
    /**
     * Define o estado de processado.
     * 
     * @param processed Novo estado de processado
     */
    public void setProcessed(boolean processed) {
        this.processed = processed;
    }
    
    /**
     * Obtém o corpo da resposta.
     * 
     * @return Corpo da resposta
     */
    public String getResponseBody() {
        return responseBody;
    }
    
    /**
     * Define o corpo da resposta.
     * 
     * @param responseBody Novo corpo da resposta
     */
    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
        this.processed = true;
    }
    
    /**
     * Obtém o código da resposta.
     * 
     * @return Código da resposta
     */
    public int getResponseCode() {
        return responseCode;
    }
    
    /**
     * Define o código da resposta.
     * 
     * @param responseCode Novo código da resposta
     */
    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }
    
    /**
     * Define as informações da resposta.
     * 
     * @param responseCode Código da resposta
     * @param responseBody Corpo da resposta
     */
    public void setResponse(int responseCode, String responseBody) {
        this.responseCode = responseCode;
        this.responseBody = responseBody;
        this.processed = true;
    }
    
    /**
     * Converte a requisição para um formato de string para transmissão na rede.
     * Formato: REQUEST|TYPE|ID|TIMESTAMP|SOURCE|TARGET|PATH|HEADERS|BODY
     * Os cabeçalhos são codificados como key1=value1,key2=value2,...
     * 
     * @return Representação em string da requisição
     */
    public String toNetworkString() {
        StringBuilder headersStr = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (headersStr.length() > 0) {
                headersStr.append(",");
            }
            headersStr.append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        return String.format("REQUEST|%s|%s|%d|%s|%s|%s|%s|%s",
                type.name(),
                id,
                timestamp,
                sourceComponent != null ? sourceComponent : "",
                targetComponent != null ? targetComponent : "",
                path != null ? path : "",
                headersStr.toString(),
                body != null ? body : "");
    }
    
    /**
     * Analisa uma requisição a partir de uma string de rede.
     * 
     * @param message Representação em string da requisição na rede
     * @return Objeto Request
     * @throws IllegalArgumentException Se o formato da mensagem for inválido
     */
    public static Request fromNetworkString(String message) {
        if (message == null || !message.startsWith("REQUEST|")) {
            throw new IllegalArgumentException("Formato inválido de mensagem de requisição");
        }
        
        String[] parts = message.split("\\|", 9);
        if (parts.length != 9) {
            throw new IllegalArgumentException("Número inválido de partes na mensagem de requisição");
        }
        
        Type type = Type.valueOf(parts[1]);
        String sourceComponent = parts[4].isEmpty() ? null : parts[4];
        String targetComponent = parts[5].isEmpty() ? null : parts[5];
        String path = parts[6].isEmpty() ? null : parts[6];
        
        // Analisa os cabeçalhos
        Map<String, String> headers = new HashMap<>();
        if (!parts[7].isEmpty()) {
            String[] headerParts = parts[7].split(",");
            for (String header : headerParts) {
                String[] keyValue = header.split("=", 2);
                if (keyValue.length == 2) {
                    headers.put(keyValue[0], keyValue[1]);
                }
            }
        }
        
        String body = parts[8].isEmpty() ? null : parts[8];
        
        return new Request(type, sourceComponent, targetComponent, path, headers, body);
    }
    
    @Override
    public String toString() {
        return "Request{" +
               "id='" + id + '\'' +
               ", type=" + type +
               ", timestamp=" + timestamp +
               ", sourceComponent='" + sourceComponent + '\'' +
               ", targetComponent='" + targetComponent + '\'' +
               ", path='" + path + '\'' +
               ", headers=" + headers +
               ", bodyLength=" + (body != null ? body.length() : 0) +
               ", processed=" + processed +
               ", responseCode=" + responseCode +
               '}';
    }
    
    /**
     * Classe Builder para criar objetos Request.
     */
    public static class RequestBuilder {
        private Type type = Type.GET;
        private String sourceComponent;
        private String targetComponent;
        private String path;
        private Map<String, String> headers = new HashMap<>();
        private String body;
        
        private RequestBuilder() {
        }
        
        public RequestBuilder type(Type type) {
            this.type = type;
            return this;
        }
        
        public RequestBuilder source(String sourceComponent) {
            this.sourceComponent = sourceComponent;
            return this;
        }
        
        public RequestBuilder target(String targetComponent) {
            this.targetComponent = targetComponent;
            return this;
        }
        
        public RequestBuilder path(String path) {
            this.path = path;
            return this;
        }
        
        public RequestBuilder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }
        
        public RequestBuilder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }
        
        public RequestBuilder body(String body) {
            this.body = body;
            return this;
        }
        
        public Request build() {
            return new Request(type, sourceComponent, targetComponent, path, headers, body);
        }
    }
}