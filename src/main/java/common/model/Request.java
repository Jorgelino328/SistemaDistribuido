package common.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class Request implements Serializable {
    private static final long serialVersionUID = 1L;
    

    public enum Type {
        GET,        
        POST,       
        PUT,        
        DELETE,     
        INFO,       
        CUSTOM      
    }
    

    private final String id;
    private final Type type;
    private final long timestamp;
    private final String sourceComponent;
    private final String targetComponent;
    

    private final String path;
    private final Map<String, String> headers;
    private final String body;
    

    private boolean processed = false;
    private String responseBody;
    private int responseCode = 200;
    

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
    
 
    public static RequestBuilder builder() {
        return new RequestBuilder();
    }
    

    public String getId() {
        return id;
    }
    

    public Type getType() {
        return type;
    }
    

    public long getTimestamp() {
        return timestamp;
    }
    

    public String getSourceComponent() {
        return sourceComponent;
    }
    

    public String getTargetComponent() {
        return targetComponent;
    }
    

    public String getPath() {
        return path;
    }
    

    public Map<String, String> getHeaders() {
        return new HashMap<>(headers);
    }
    

    public String getHeader(String name) {
        return headers.get(name);
    }
    

    public String getBody() {
        return body;
    }
    

    public boolean isProcessed() {
        return processed;
    }
    

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }
    

    public String getResponseBody() {
        return responseBody;
    }
    

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
        this.processed = true;
    }
    

    public int getResponseCode() {
        return responseCode;
    }
    

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }
    
    public void setResponse(int responseCode, String responseBody) {
        this.responseCode = responseCode;
        this.responseBody = responseBody;
        this.processed = true;
    }
    

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