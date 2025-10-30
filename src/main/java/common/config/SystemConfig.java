package common.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SystemConfig {
    

    private static SystemConfig instance;
    

    private final Properties properties = new Properties();
    

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final int DEFAULT_TCP_PORT = 8081;
    private static final int DEFAULT_UDP_PORT = 8082;
    private static final int DEFAULT_REGISTRATION_PORT = 8000;
    private static final int DEFAULT_HEARTBEAT_INTERVAL = 10; // segundos
    private static final int DEFAULT_HEARTBEAT_TIMEOUT = 30; // segundos
    

    private SystemConfig() {
        loadDefaultProperties();
        loadPropertiesFromFile();
    }
    

    public static synchronized SystemConfig getInstance() {
        if (instance == null) {
            instance = new SystemConfig();
        }
        return instance;
    }
    

    private void loadDefaultProperties() {

        properties.setProperty("gateway.host", DEFAULT_HOST);
        properties.setProperty("gateway.http.port", String.valueOf(DEFAULT_HTTP_PORT));
        properties.setProperty("gateway.tcp.port", String.valueOf(DEFAULT_TCP_PORT));
        properties.setProperty("gateway.udp.port", String.valueOf(DEFAULT_UDP_PORT));
        properties.setProperty("gateway.registration.port", String.valueOf(DEFAULT_REGISTRATION_PORT));
        

        properties.setProperty("userservice.http.port", "8181");
        properties.setProperty("userservice.tcp.port", "8182");
        properties.setProperty("userservice.udp.port", "8183");
        

        properties.setProperty("fileservice.http.port", "8281");
        properties.setProperty("fileservice.tcp.port", "8282");
        properties.setProperty("fileservice.udp.port", "8283");
        

        properties.setProperty("heartbeat.interval", String.valueOf(DEFAULT_HEARTBEAT_INTERVAL));
        properties.setProperty("heartbeat.timeout", String.valueOf(DEFAULT_HEARTBEAT_TIMEOUT));
    }
    

    private void loadPropertiesFromFile() {
        try {

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.properties");
            

            if (inputStream == null) {
                try {
                    inputStream = new FileInputStream("config.properties");
                } catch (IOException e) {

                    return;
                }
            }
            

            properties.load(inputStream);
            inputStream.close();
            

        } catch (IOException e) {

        }
    }
    

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    

    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {

            }
        }
        return defaultValue;
    }
    

    public String getGatewayHost() {
        return getProperty("gateway.host");
    }
    

    public int getHttpPort() {
        return getIntProperty("gateway.http.port", DEFAULT_HTTP_PORT);
    }
    

    public int getTcpPort() {
        return getIntProperty("gateway.tcp.port", DEFAULT_TCP_PORT);
    }
    

    public int getUdpPort() {
        return getIntProperty("gateway.udp.port", DEFAULT_UDP_PORT);
    }
    

    public int getRegistrationPort() {
        return getIntProperty("gateway.registration.port", DEFAULT_REGISTRATION_PORT);
    }
    

    public int getUserServiceHttpPort() {
        return getIntProperty("userservice.http.port", 8181);
    }
    

    public int getUserServiceTcpPort() {
        return getIntProperty("userservice.tcp.port", 8182);
    }
    

    public int getUserServiceUdpPort() {
        return getIntProperty("userservice.udp.port", 8183);
    }
    

    public int getFileServiceHttpPort() {
        return getIntProperty("fileservice.http.port", 8281);
    }
    

    public int getFileServiceTcpPort() {
        return getIntProperty("fileservice.tcp.port", 8282);
    }
    

    public int getFileServiceUdpPort() {
        return getIntProperty("fileservice.udp.port", 8283);
    }

    public int getHeartbeatInterval() {
        return getIntProperty("heartbeat.interval", DEFAULT_HEARTBEAT_INTERVAL);
    }
    

    public int getHeartbeatTimeout() {
        return getIntProperty("heartbeat.timeout", DEFAULT_HEARTBEAT_TIMEOUT);
    }
    
}
