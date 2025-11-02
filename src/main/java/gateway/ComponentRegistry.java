package gateway;

import common.model.ComponentInfo;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class ComponentRegistry {
    private final Map<String, List<ComponentInfo>> componentsByType = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastUsedIndexByType = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ComponentRegistry() {
        componentsByType.put("userservice", new ArrayList<>());
        componentsByType.put("fileservice", new ArrayList<>());
    }

    public void handleRegistration(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String registrationMessage = reader.readLine();
            
            if (registrationMessage != null && !registrationMessage.isEmpty()) {
                String[] parts = registrationMessage.split("\\|");
                
                if (parts.length >= 6 && "REGISTER".equals(parts[0])) {
                    String componentType = parts[1].toLowerCase();
                    String host = parts[2];
                    int httpPort = Integer.parseInt(parts[3]);
                    int tcpPort = Integer.parseInt(parts[4]);
                    int udpPort = Integer.parseInt(parts[5]);
                    
                    ComponentInfo componentInfo = new ComponentInfo(
                        componentType, host, httpPort, tcpPort, udpPort
                    );
                    
                    if (parts.length >= 8) {
                        String keyRangeStart = "null".equals(parts[6]) ? null : parts[6];
                        String keyRangeEnd = "null".equals(parts[7]) ? null : parts[7];
                        componentInfo.setKeyRange(keyRangeStart, keyRangeEnd);
                    }
                    
                    registerComponent(componentInfo);
                    
                    writer.println("REGISTERED|SUCCESS");
                    
                } else if (registrationMessage.startsWith("DISCOVER:")) {
                    String componentType = registrationMessage.substring(9);
                    List<ComponentInfo> nodes = getAvailableComponents(componentType);
                    
                    StringBuilder jsonBuilder = new StringBuilder();
                    jsonBuilder.append("[");
                    for (int i = 0; i < nodes.size(); i++) {
                        if (i > 0) jsonBuilder.append(",");
                        ComponentInfo node = nodes.get(i);
                        jsonBuilder.append("{")
                                   .append("\"type\":\"").append(node.getType()).append("\",")
                                   .append("\"instanceId\":\"").append(node.getInstanceId()).append("\",")
                                   .append("\"host\":\"").append(node.getHost()).append("\",")
                                   .append("\"httpPort\":").append(node.getHttpPort()).append(",")
                                   .append("\"tcpPort\":").append(node.getTcpPort()).append(",")
                                   .append("\"udpPort\":").append(node.getUdpPort())
                                   .append("}");
                    }
                    jsonBuilder.append("]");
                    
                    writer.println("NODES:" + jsonBuilder.toString());
                } else {
                    writer.println("REGISTERED|FAILED|Formato de registro inválido");
                }
            }
        } catch (IOException | NumberFormatException e) {
        }
    }

    public void registerComponent(ComponentInfo componentInfo) {
        lock.writeLock().lock();
        try {
            String componentType = componentInfo.getType();
            
            componentsByType.putIfAbsent(componentType, new ArrayList<>());
            
            List<ComponentInfo> components = componentsByType.get(componentType);
            boolean alreadyExists = false;
            
            for (int i = 0; i < components.size(); i++) {
                ComponentInfo existing = components.get(i);
                if (existing.equals(componentInfo)) {
                    components.set(i, componentInfo);
                    alreadyExists = true;
                    break;
                }
            }
            
            if (!alreadyExists) {
                components.add(componentInfo);
                System.out.println("INFO: Novo componente registrado: " + componentInfo.getInstanceId());
            }
            
            if (components.size() == 1) {
                lastUsedIndexByType.put(componentType, -1);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deregisterComponent(ComponentInfo componentInfo) {
        lock.writeLock().lock();
        try {
            String componentType = componentInfo.getType();
            List<ComponentInfo> components = componentsByType.get(componentType);
            
            if (components != null) {
                components.remove(componentInfo);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void markComponentDead(ComponentInfo componentInfo) {
        componentInfo.markDead();
        deregisterComponent(componentInfo);
    }

    public List<ComponentInfo> getAvailableComponents(String componentType) {
        lock.readLock().lock();
        try {
            List<ComponentInfo> components = componentsByType.get(componentType.toLowerCase());
            
            if (components == null) {
                return new ArrayList<>();
            }
            
            List<ComponentInfo> healthyComponents = new ArrayList<>();
            for (ComponentInfo component : components) {
                if (component.isHealthy() && !component.isSuspect()) {
                    healthyComponents.add(component);
                }
            }
            
            return healthyComponents;
        } finally {
            lock.readLock().unlock();
        }
    }

    public ComponentInfo selectComponent(String componentType) {
        lock.writeLock().lock();
        try {
            List<ComponentInfo> components = getAvailableComponents(componentType);
            
            if (components.isEmpty()) {
                return null;
            }
            
            int lastIndex = lastUsedIndexByType.getOrDefault(componentType, -1);
            int nextIndex = (lastIndex + 1) % components.size();
            
            lastUsedIndexByType.put(componentType, nextIndex);
            
            return components.get(nextIndex);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public ComponentInfo selectComponentByKey(String componentType, String key) {
        // Primeiro tentar encontrar componente responsável com read lock
        lock.readLock().lock();
        try {
            List<ComponentInfo> components = getAvailableComponents(componentType);
            
            if (components.isEmpty()) {
                return null;
            }
            
            for (ComponentInfo component : components) {
                if (component.isResponsibleForKey(key)) {
                    return component;
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Se não encontrou componente responsável, usar round-robin com write lock
        lock.writeLock().lock();
        try {
            List<ComponentInfo> components = getAvailableComponents(componentType);
            
            if (components.isEmpty()) {
                return null;
            }
            
            int lastIndex = lastUsedIndexByType.getOrDefault(componentType, -1);
            int nextIndex = (lastIndex + 1) % components.size();
            lastUsedIndexByType.put(componentType, nextIndex);
            
            return components.get(nextIndex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<ComponentInfo> getAllComponentsList() {
        lock.readLock().lock();
        try {
            List<ComponentInfo> allComponents = new ArrayList<>();
            for (List<ComponentInfo> components : componentsByType.values()) {
                allComponents.addAll(components);
            }
            return allComponents;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void removeComponent(ComponentInfo componentToRemove) {
        lock.writeLock().lock();
        try {
            List<ComponentInfo> components = componentsByType.get(componentToRemove.getType());
            if (components != null) {
                components.removeIf(comp -> comp.getInstanceId().equals(componentToRemove.getInstanceId()));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void markComponentSuspect(ComponentInfo componentInfo) {
        componentInfo.markSuspect();
    }

    public Map<String, List<ComponentInfo>> getAllComponents() {
        lock.readLock().lock();
        try {
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
