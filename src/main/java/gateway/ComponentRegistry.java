package gateway;

import common.model.ComponentInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registro de componentes para o Gateway de API.
 * 
 * Gerencia o registro, seleção e monitoramento de componentes distribuídos.
 */
public class ComponentRegistry {
    private static final Logger LOGGER = Logger.getLogger(ComponentRegistry.class.getName());
    
    // Armazena informações dos componentes por tipo
    private final Map<String, List<ComponentInfo>> componentsByType = new ConcurrentHashMap<>();
    
    // Rastreamento do último índice usado para cada tipo de componente (para balanceamento round-robin)
    private final Map<String, Integer> lastUsedIndexByType = new ConcurrentHashMap<>();
    
    // Lock para garantir consistência na seleção de componentes
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Construtor do registro de componentes.
     */
    public ComponentRegistry() {
        // Inicializa com listas vazias para tipos comuns de componentes
        componentsByType.put("componentA", new ArrayList<>());
        componentsByType.put("componentB", new ArrayList<>());
    }

    /**
     * Lida com o registro de um componente recebido via socket.
     * 
     * @param clientSocket Socket do cliente
     */
    public void handleRegistration(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            // Lê a mensagem de registro
            String registrationMessage = reader.readLine();
            
            if (registrationMessage != null && !registrationMessage.isEmpty()) {
                // Analisa a mensagem de registro
                // Formato esperado: "REGISTER|componentType|host|httpPort|tcpPort|udpPort"
                String[] parts = registrationMessage.split("\\|");
                
                if (parts.length >= 6 && "REGISTER".equals(parts[0])) {
                    String componentType = parts[1].toLowerCase();
                    String host = parts[2];
                    int httpPort = Integer.parseInt(parts[3]);
                    int tcpPort = Integer.parseInt(parts[4]);
                    int udpPort = Integer.parseInt(parts[5]);
                    
                    // Cria as informações do componente
                    ComponentInfo componentInfo = new ComponentInfo(
                        componentType, host, httpPort, tcpPort, udpPort
                    );
                    
                    // Registra o componente
                    registerComponent(componentInfo);
                    
                    // Envia confirmação de registro
                    writer.println("REGISTERED|SUCCESS");
                    LOGGER.info("Registrado " + componentType + " em " + host + 
                               " (HTTP:" + httpPort + ", TCP:" + tcpPort + 
                               ", UDP:" + udpPort + ")");
                } else {
                    writer.println("REGISTERED|FAILED|Formato de registro inválido");
                    LOGGER.warning("Mensagem de registro inválida: " + registrationMessage);
                }
            }
        } catch (IOException | NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Erro ao lidar com o registro do componente", e);
        }
    }

    /**
     * Registra um componente no sistema.
     * 
     * @param componentInfo Informações do componente
     */
    public void registerComponent(ComponentInfo componentInfo) {
        lock.writeLock().lock();
        try {
            String componentType = componentInfo.getType();
            
            // Cria uma lista para este tipo de componente, se não existir
            componentsByType.putIfAbsent(componentType, new ArrayList<>());
            
            // Verifica se o componente já está registrado
            List<ComponentInfo> components = componentsByType.get(componentType);
            boolean alreadyExists = false;
            
            for (int i = 0; i < components.size(); i++) {
                ComponentInfo existing = components.get(i);
                if (existing.equals(componentInfo)) {
                    // Atualiza a entrada existente
                    components.set(i, componentInfo);
                    alreadyExists = true;
                    break;
                }
            }
            
            // Adiciona o novo componente, se ainda não estiver registrado
            if (!alreadyExists) {
                components.add(componentInfo);
            }
            
            // Reseta o índice usado se este for o primeiro componente do tipo
            if (components.size() == 1) {
                lastUsedIndexByType.put(componentType, -1);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove o registro de um componente.
     * 
     * @param componentInfo Informações do componente
     */
    public void deregisterComponent(ComponentInfo componentInfo) {
        lock.writeLock().lock();
        try {
            String componentType = componentInfo.getType();
            List<ComponentInfo> components = componentsByType.get(componentType);
            
            if (components != null) {
                components.remove(componentInfo);
                LOGGER.info("Removido registro de " + componentType + " em " + 
                          componentInfo.getHost() + ":" + componentInfo.getHttpPort());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marca um componente como suspeito.
     * 
     * @param componentInfo Informações do componente
     */
    public void markComponentSuspect(ComponentInfo componentInfo) {
        componentInfo.markSuspect();
    }

    /**
     * Marca um componente como inativo e remove seu registro.
     * 
     * @param componentInfo Informações do componente
     */
    public void markComponentDead(ComponentInfo componentInfo) {
        componentInfo.markDead();
        deregisterComponent(componentInfo);
    }

    /**
     * Obtém a lista de componentes disponíveis de um tipo específico.
     * 
     * @param componentType Tipo do componente
     * @return Lista de componentes disponíveis
     */
    public List<ComponentInfo> getAvailableComponents(String componentType) {
        lock.readLock().lock();
        try {
            List<ComponentInfo> components = componentsByType.get(componentType.toLowerCase());
            
            if (components == null) {
                return new ArrayList<>();
            }
            
            // Filtra apenas os componentes saudáveis
            List<ComponentInfo> healthyComponents = new ArrayList<>();
            for (ComponentInfo component : components) {
                if (component.isHealthy()) {
                    healthyComponents.add(component);
                }
            }
            
            return healthyComponents;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Seleciona um componente disponível usando balanceamento round-robin.
     * 
     * @param componentType Tipo do componente
     * @return Componente selecionado ou null se nenhum estiver disponível
     */
    public ComponentInfo selectComponent(String componentType) {
        lock.writeLock().lock();
        try {
            List<ComponentInfo> components = getAvailableComponents(componentType);
            
            if (components.isEmpty()) {
                return null;
            }
            
            // Seleção round-robin simples
            int lastIndex = lastUsedIndexByType.getOrDefault(componentType, -1);
            int nextIndex = (lastIndex + 1) % components.size();
            
            // Atualiza o índice usado
            lastUsedIndexByType.put(componentType, nextIndex);
            
            return components.get(nextIndex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Obtém todos os componentes registrados.
     * 
     * @return Mapa de componentes por tipo
     */
    public Map<String, List<ComponentInfo>> getAllComponents() {
        lock.readLock().lock();
        try {
            // Cria uma cópia profunda para evitar problemas de modificação concorrente
            Map<String, List<ComponentInfo>> result = new HashMap<>();
            
            for (Map.Entry<String, List<ComponentInfo>> entry : componentsByType.entrySet()) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
}
