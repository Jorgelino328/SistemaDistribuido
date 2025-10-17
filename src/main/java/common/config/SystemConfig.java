package common.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe Singleton para gerenciar a configuração do sistema.
 * Fornece acesso aos parâmetros de configuração para todos os componentes.
 */
public class SystemConfig {
    private static final Logger LOGGER = Logger.getLogger(SystemConfig.class.getName());
    
    // Instância Singleton
    private static SystemConfig instance;
    
    // Propriedades de configuração
    private final Properties properties = new Properties();
    
    // Valores padrão de configuração
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final int DEFAULT_TCP_PORT = 8081;
    private static final int DEFAULT_UDP_PORT = 8082;
    private static final int DEFAULT_REGISTRATION_PORT = 8000;
    
    /**
     * Construtor privado para evitar instância direta.
     * Carrega a configuração do arquivo config.properties, se disponível.
     */
    private SystemConfig() {
        loadDefaultProperties();
        loadPropertiesFromFile();
    }
    
    /**
     * Obtém a instância Singleton.
     * 
     * @return A instância de SystemConfig
     */
    public static synchronized SystemConfig getInstance() {
        if (instance == null) {
            instance = new SystemConfig();
        }
        return instance;
    }
    
    /**
     * Carrega as propriedades de configuração padrão.
     */
    private void loadDefaultProperties() {
        // Configuração do Gateway de API
        properties.setProperty("gateway.host", DEFAULT_HOST);
        properties.setProperty("gateway.http.port", String.valueOf(DEFAULT_HTTP_PORT));
        properties.setProperty("gateway.tcp.port", String.valueOf(DEFAULT_TCP_PORT));
        properties.setProperty("gateway.udp.port", String.valueOf(DEFAULT_UDP_PORT));
        properties.setProperty("gateway.registration.port", String.valueOf(DEFAULT_REGISTRATION_PORT));
        
        // Configuração do UserService
        properties.setProperty("userservice.http.port", "8181");
        properties.setProperty("userservice.tcp.port", "8182");
        properties.setProperty("userservice.udp.port", "8183");
        
        // Configuração do MessageService
        properties.setProperty("messageservice.http.port", "8281");
        properties.setProperty("messageservice.tcp.port", "8282");
        properties.setProperty("messageservice.udp.port", "8283");
    }
    
    /**
     * Carrega as propriedades do arquivo config.properties, se disponível.
     */
    private void loadPropertiesFromFile() {
        try {
            // Tenta carregar do classpath
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.properties");
            
            // Se não encontrado no classpath, tenta carregar do sistema de arquivos
            if (inputStream == null) {
                try {
                    inputStream = new FileInputStream("config.properties");
                } catch (IOException e) {
                    // LOGGER.info("Nenhum arquivo config.properties encontrado no sistema de arquivos, usando os valores padrão");
                    return;
                }
            }
            
            // Carrega as propriedades do input stream
            properties.load(inputStream);
            inputStream.close();
            
            // LOGGER.info("Configuração carregada do arquivo config.properties");
        } catch (IOException e) {
            // LOGGER.log(Level.WARNING, "Erro ao carregar o arquivo config.properties", e);
        }
    }
    
    /**
     * Obtém uma propriedade do tipo String.
     * 
     * @param key Chave da propriedade
     * @return Valor da propriedade
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Obtém uma propriedade do tipo inteiro.
     * 
     * @param key Chave da propriedade
     * @param defaultValue Valor padrão se a propriedade não for encontrada ou não for um inteiro válido
     * @return Valor da propriedade como inteiro
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // LOGGER.warning("Valor inteiro inválido para a propriedade " + key + ": " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Obtém o host do Gateway de API.
     * 
     * @return Host do Gateway de API
     */
    public String getGatewayHost() {
        return getProperty("gateway.host");
    }
    
    /**
     * Obtém a porta HTTP do Gateway de API.
     * 
     * @return Porta HTTP do Gateway de API
     */
    public int getHttpPort() {
        return getIntProperty("gateway.http.port", DEFAULT_HTTP_PORT);
    }
    
    /**
     * Obtém a porta TCP do Gateway de API.
     * 
     * @return Porta TCP do Gateway de API
     */
    public int getTcpPort() {
        return getIntProperty("gateway.tcp.port", DEFAULT_TCP_PORT);
    }
    
    /**
     * Obtém a porta UDP do Gateway de API.
     * 
     * @return Porta UDP do Gateway de API
     */
    public int getUdpPort() {
        return getIntProperty("gateway.udp.port", DEFAULT_UDP_PORT);
    }
    
    /**
     * Obtém a porta de registro do Gateway de API.
     * 
     * @return Porta de registro do Gateway de API
     */
    public int getRegistrationPort() {
        return getIntProperty("gateway.registration.port", DEFAULT_REGISTRATION_PORT);
    }
    
    /**
     * Obtém a porta HTTP do UserService.
     * 
     * @return Porta HTTP do UserService
     */
    public int getUserServiceHttpPort() {
        return getIntProperty("userservice.http.port", 8181);
    }
    
    /**
     * Obtém a porta TCP do UserService.
     * 
     * @return Porta TCP do UserService
     */
    public int getUserServiceTcpPort() {
        return getIntProperty("userservice.tcp.port", 8182);
    }
    
    /**
     * Obtém a porta UDP do UserService.
     * 
     * @return Porta UDP do UserService
     */
    public int getUserServiceUdpPort() {
        return getIntProperty("userservice.udp.port", 8183);
    }
    
    /**
     * Obtém a porta HTTP do MessageService.
     * 
     * @return Porta HTTP do MessageService
     */
    public int getMessageServiceHttpPort() {
        return getIntProperty("messageservice.http.port", 8281);
    }
    
    /**
     * Obtém a porta TCP do MessageService.
     * 
     * @return Porta TCP do MessageService
     */
    public int getMessageServiceTcpPort() {
        return getIntProperty("messageservice.tcp.port", 8282);
    }
    
    /**
     * Obtém a porta UDP do MessageService.
     * 
     * @return Porta UDP do MessageService
     */
    public int getMessageServiceUdpPort() {
        return getIntProperty("messageservice.udp.port", 8283);
    }
    
}
