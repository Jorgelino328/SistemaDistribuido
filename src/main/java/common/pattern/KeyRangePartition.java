package common.pattern;

import common.model.ComponentInfo;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class KeyRangePartition {
    private final String nodeId;
    private final String componentType;
    private final String gatewayHost;
    private final int gatewayPort;
    
    private final Map<String, PartitionRange> partitionMap = new ConcurrentHashMap<>();
    private final List<ComponentInfo> allNodes = new CopyOnWriteArrayList<>();
    private PartitionRange myRange;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final Gson gson = new Gson();
    
    private Consumer<PartitionRange> onRangeAssigned;
    private Consumer<List<ComponentInfo>> onTopologyChange;
    private Consumer<String> onDataMigration;
    
    private boolean isActive = false;
    
    public static class PartitionRange {
        private final String startKey;
        private final String endKey;
        private final String nodeId;
        private final boolean isResponsible;
        
        public PartitionRange(String startKey, String endKey, String nodeId, boolean isResponsible) {
            this.startKey = startKey;
            this.endKey = endKey;
            this.nodeId = nodeId;
            this.isResponsible = isResponsible;
        }
        
        public boolean containsKey(String key) {
            if (key == null || key.isEmpty()) return false;
            
            String identifier = key;
            if (key.contains(":")) {
                String[] parts = key.split(":", 2);
                if (parts.length == 2) {
                    identifier = parts[1];
                }
            }
            
            String compareKey = identifier.substring(0, 1).toUpperCase();
            
            if (startKey == null && endKey == null) return true;
            if (startKey == null) return compareKey.compareTo(endKey) < 0;
            if (endKey == null) return compareKey.compareTo(startKey) >= 0;
            return compareKey.compareTo(startKey) >= 0 && compareKey.compareTo(endKey) < 0;
        }
        
        public String getStartKey() { return startKey; }
        public String getEndKey() { return endKey; }
        public String getNodeId() { return nodeId; }
        public boolean isResponsible() { return isResponsible; }
        
        @Override
        public String toString() {
            return String.format("Range[%s-%s] -> %s (responsible: %s)", 
                startKey, endKey, nodeId, isResponsible);
        }
    }
    
    public KeyRangePartition(String nodeId, String componentType, String gatewayHost, int gatewayPort) {
        this.nodeId = nodeId;
        this.componentType = componentType;
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;
    }
    
    public KeyRangePartition onRangeAssigned(Consumer<PartitionRange> onRangeAssigned) {
        this.onRangeAssigned = onRangeAssigned;
        return this;
    }
    
    public KeyRangePartition onTopologyChange(Consumer<List<ComponentInfo>> onTopologyChange) {
        this.onTopologyChange = onTopologyChange;
        return this;
    }
    
    public KeyRangePartition onDataMigration(Consumer<String> onDataMigration) {
        this.onDataMigration = onDataMigration;
        return this;
    }

    public void start() {
        if (isActive) return;
        
        isActive = true;
        
        scheduler.scheduleAtFixedRate(this::discoverNodes, 0, 10, TimeUnit.SECONDS);
        
        scheduler.scheduleAtFixedRate(this::rebalancePartitions, 1, 30, TimeUnit.SECONDS);
        
        scheduler.scheduleAtFixedRate(this::synchronizeMetadata, 2, 15, TimeUnit.SECONDS);
    }

    public void stop() {
        if (!isActive) return;
        
        isActive = false;
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public ComponentInfo getResponsibleNode(String key) {
        for (PartitionRange range : partitionMap.values()) {
            if (range.containsKey(key)) {
                for (ComponentInfo node : allNodes) {
                    if (node.getInstanceId().equals(range.getNodeId())) {
                        return node;
                    }
                }
            }
        }
        
        return allNodes.isEmpty() ? null : allNodes.get(0);
    }
    
    public boolean isResponsibleFor(String key) {
        return myRange != null && myRange.containsKey(key);
    }

    public PartitionRange getMyRange() {
        return myRange;
    }
    

    public Map<String, PartitionRange> getAllRanges() {
        return new HashMap<>(partitionMap);
    }
    

    public List<ComponentInfo> getAllNodes() {
        return new ArrayList<>(allNodes);
    }
    

    public void addNode(ComponentInfo node) {
        if (!allNodes.contains(node)) {
            allNodes.add(node);
            
            scheduler.schedule(this::rebalancePartitions, 2, TimeUnit.SECONDS);
            
            if (onTopologyChange != null) {
                onTopologyChange.accept(new ArrayList<>(allNodes));
            }
        }
    }
    
    public void removeNode(String nodeId) {
        allNodes.removeIf(node -> node.getInstanceId().equals(nodeId));
        partitionMap.remove(nodeId);
        
        
        scheduler.schedule(this::rebalancePartitions, 1, TimeUnit.SECONDS);
        
        if (onTopologyChange != null) {
            onTopologyChange.accept(new ArrayList<>(allNodes));
        }
    }

    private void discoverNodes() {
        try {
            try (Socket socket = new Socket(gatewayHost, gatewayPort)) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                out.println("DISCOVER:" + componentType);
                String response = in.readLine();
                
                if (response != null && response.startsWith("NODES:")) {
                    String nodesJson = response.substring(6);
                    Type listType = new TypeToken<List<ComponentInfo>>(){}.getType();
                    List<ComponentInfo> discoveredNodes = gson.fromJson(nodesJson, listType);
                    
                    boolean changed = false;
                    for (ComponentInfo node : discoveredNodes) {
                        if (!allNodes.contains(node)) {
                            allNodes.add(node);
                            changed = true;
                        }
                    }
                    
                    if (changed) {
                        rebalancePartitions();
                        if (onTopologyChange != null) {
                            onTopologyChange.accept(new ArrayList<>(allNodes));
                        }
                    }
                }
            }
        } catch (IOException e) {
        }
    }
    

    private void rebalancePartitions() {
        if (allNodes.isEmpty()) return;
        
        
        List<ComponentInfo> sortedNodes = new ArrayList<>(allNodes);
        sortedNodes.sort(Comparator.comparing(ComponentInfo::getInstanceId));
        
        Map<String, PartitionRange> newPartitions = calculatePartitions(sortedNodes);
        
        if (!newPartitions.equals(partitionMap)) {
            PartitionRange oldRange = myRange;
            
            partitionMap.clear();
            partitionMap.putAll(newPartitions);
            
            myRange = partitionMap.get(nodeId);
            
            if (myRange != null) {
                
                if (onRangeAssigned != null) {
                    onRangeAssigned.accept(myRange);
                }
                
                if (oldRange != null && !oldRange.equals(myRange)) {
                    triggerDataMigration(oldRange, myRange);
                }
            }
        }
    }
    
    private Map<String, PartitionRange> calculatePartitions(List<ComponentInfo> nodes) {
        Map<String, PartitionRange> partitions = new HashMap<>();
        
        if (nodes.isEmpty()) return partitions;
        
        if (nodes.size() == 1) {
            ComponentInfo node = nodes.get(0);
            partitions.put(node.getInstanceId(), 
                new PartitionRange(null, null, node.getInstanceId(), 
                    node.getInstanceId().equals(nodeId)));
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                ComponentInfo node = nodes.get(i);
                String startKey = getPartitionKey(i, nodes.size());
                String endKey = getPartitionKey(i + 1, nodes.size());
                
                partitions.put(node.getInstanceId(), 
                    new PartitionRange(startKey, endKey, node.getInstanceId(),
                        node.getInstanceId().equals(nodeId)));
            }
        }
        
        return partitions;
    }
    
    private String getPartitionKey(int index, int totalPartitions) {
        if (index == 0) return null; 
        if (index >= totalPartitions) return null; 
        
        char keyChar = (char) ('A' + (index * 26 / totalPartitions));
        return String.valueOf(keyChar);
    }
    

    private void synchronizeMetadata() {
        if (allNodes.isEmpty() || partitionMap.isEmpty()) return;
        
        for (ComponentInfo node : allNodes) {
            if (node.getInstanceId().equals(nodeId)) continue;
            
            try (Socket socket = new Socket(node.getHost(), node.getTcpPort())) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                String partitionsJson = gson.toJson(partitionMap);
                out.println("SYNC_PARTITIONS|" + partitionsJson);
                
                String response = in.readLine();
                if (response != null && response.startsWith("PARTITIONS_ACK")) {
                    String remotePartitionsJson = response.substring(15);
                    Type mapType = new TypeToken<Map<String, PartitionRange>>(){}.getType();
                    Map<String, PartitionRange> remotePartitions = gson.fromJson(remotePartitionsJson, mapType);
                    
                    for (Map.Entry<String, PartitionRange> entry : remotePartitions.entrySet()) {
                        if (!partitionMap.containsKey(entry.getKey())) {
                            partitionMap.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            } catch (IOException e) {
            }
        }
    }
    
 
    private void triggerDataMigration(PartitionRange oldRange, PartitionRange newRange) {
        if (onDataMigration != null) {
            String migrationInfo = String.format("Migração necessária: %s -> %s", 
                oldRange, newRange);
            onDataMigration.accept(migrationInfo);
        }
    }
}