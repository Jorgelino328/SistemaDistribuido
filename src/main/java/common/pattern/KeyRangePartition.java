package common.pattern;

import common.model.ComponentInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * Implementação do padrão Key-Range Partitioning para sistemas distribuídos.
 * Distribui dados baseado em faixas de chaves, eliminando a necessidade de 
 * replicação líder-seguidor e permitindo escalabilidade horizontal.
 */
public class KeyRangePartition {
    private static final Logger LOGGER = Logger.getLogger(KeyRangePartition.class.getName());
    
    // Configuração do nó
    private final String nodeId;
    private final String componentType;
    
    // Informações de particionamento
    private final Map<String, PartitionRange> partitionMap = new ConcurrentHashMap<>();
    private final List<ComponentInfo> allNodes = new CopyOnWriteArrayList<>();
    private PartitionRange myRange;
    
    // Comunicação e sincronização
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final Gson gson = new Gson();
    
    // Callbacks
    private Consumer<PartitionRange> onRangeAssigned;
    private Consumer<List<ComponentInfo>> onTopologyChange;
    private Consumer<String> onDataMigration;
    
    // Estado
    private boolean isActive = false;
    
    /**
     * Representa uma faixa de chaves para particionamento.
     */
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
            if (startKey == null && endKey == null) return true;
            if (startKey == null) return key.compareTo(endKey) <= 0;
            if (endKey == null) return key.compareTo(startKey) >= 0;
            return key.compareTo(startKey) >= 0 && key.compareTo(endKey) <= 0;
        }
        
        // Getters
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
    
    /**
     * Construtor para KeyRangePartition.
     */
    public KeyRangePartition(String nodeId, String componentType) {
        this.nodeId = nodeId;
        this.componentType = componentType;
    }
    
    /**
     * Configura callback para quando uma nova faixa é atribuída ao nó.
     */
    public KeyRangePartition onRangeAssigned(Consumer<PartitionRange> onRangeAssigned) {
        this.onRangeAssigned = onRangeAssigned;
        return this;
    }
    
    /**
     * Configura callback para mudanças na topologia do cluster.
     */
    public KeyRangePartition onTopologyChange(Consumer<List<ComponentInfo>> onTopologyChange) {
        this.onTopologyChange = onTopologyChange;
        return this;
    }
    
    /**
     * Configura callback para migração de dados.
     */
    public KeyRangePartition onDataMigration(Consumer<String> onDataMigration) {
        this.onDataMigration = onDataMigration;
        return this;
    }
    
    /**
     * Inicia o sistema de particionamento.
     */
    public void start() {
        if (isActive) return;
        
        isActive = true;
        LOGGER.info("Iniciando Key-Range Partition para nó: " + nodeId);
        
        // Agenda descoberta periódica de nós
        scheduler.scheduleAtFixedRate(this::discoverNodes, 0, 10, TimeUnit.SECONDS);
        
        // Agenda rebalanceamento periódico
        scheduler.scheduleAtFixedRate(this::rebalancePartitions, 5, 30, TimeUnit.SECONDS);
        
        // Agenda sincronização de metadados
        scheduler.scheduleAtFixedRate(this::synchronizeMetadata, 1, 15, TimeUnit.SECONDS);
    }
    
    /**
     * Para o sistema de particionamento.
     */
    public void stop() {
        if (!isActive) return;
        
        isActive = false;
        LOGGER.info("Parando Key-Range Partition para nó: " + nodeId);
        
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
    
    /**
     * Determina qual nó é responsável por uma chave específica.
     */
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
        
        // Se não encontrou, retorna o primeiro nó disponível (fallback)
        return allNodes.isEmpty() ? null : allNodes.get(0);
    }
    
    /**
     * Verifica se este nó é responsável por uma chave específica.
     */
    public boolean isResponsibleFor(String key) {
        return myRange != null && myRange.containsKey(key);
    }
    
    /**
     * Obtém a faixa atual deste nó.
     */
    public PartitionRange getMyRange() {
        return myRange;
    }
    
    /**
     * Obtém todas as faixas de particionamento.
     */
    public Map<String, PartitionRange> getAllRanges() {
        return new HashMap<>(partitionMap);
    }
    
    /**
     * Obtém lista de todos os nós conhecidos.
     */
    public List<ComponentInfo> getAllNodes() {
        return new ArrayList<>(allNodes);
    }
    
    /**
     * Adiciona um novo nó ao cluster.
     */
    public void addNode(ComponentInfo node) {
        if (!allNodes.contains(node)) {
            allNodes.add(node);
            LOGGER.info("Novo nó adicionado: " + node.getInstanceId());
            
            // Triggera rebalanceamento
            scheduler.schedule(this::rebalancePartitions, 2, TimeUnit.SECONDS);
            
            if (onTopologyChange != null) {
                onTopologyChange.accept(new ArrayList<>(allNodes));
            }
        }
    }
    
    /**
     * Remove um nó do cluster.
     */
    public void removeNode(String nodeId) {
        allNodes.removeIf(node -> node.getInstanceId().equals(nodeId));
        partitionMap.remove(nodeId);
        
        LOGGER.info("Nó removido: " + nodeId);
        
        // Triggera rebalanceamento imediato
        scheduler.schedule(this::rebalancePartitions, 1, TimeUnit.SECONDS);
        
        if (onTopologyChange != null) {
            onTopologyChange.accept(new ArrayList<>(allNodes));
        }
    }
    
    /**
     * Descobre outros nós do mesmo tipo no sistema.
     */
    private void discoverNodes() {
        // Implementação simplificada - em um sistema real, 
        // isso seria feito através de service discovery
        try {
            // Conecta ao gateway para descobrir outros nós
            try (Socket socket = new Socket("localhost", 8000)) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                out.println("DISCOVER:" + componentType);
                String response = in.readLine();
                
                if (response != null && response.startsWith("NODES:")) {
                    String nodesJson = response.substring(6);
                    Type listType = new TypeToken<List<ComponentInfo>>(){}.getType();
                    List<ComponentInfo> discoveredNodes = gson.fromJson(nodesJson, listType);
                    
                    // Atualiza lista de nós
                    boolean changed = false;
                    for (ComponentInfo node : discoveredNodes) {
                        if (!allNodes.contains(node)) {
                            allNodes.add(node);
                            changed = true;
                        }
                    }
                    
                    if (changed && onTopologyChange != null) {
                        onTopologyChange.accept(new ArrayList<>(allNodes));
                    }
                }
            }
        } catch (IOException e) {
            // Falha silenciosa - tentará novamente no próximo ciclo
        }
    }
    
    /**
     * Rebalanceia as partições entre os nós disponíveis.
     */
    private void rebalancePartitions() {
        if (allNodes.isEmpty()) return;
        
        LOGGER.info("Rebalanceando partições. Nós disponíveis: " + allNodes.size());
        
        // Ordena os nós por ID para garantir consistência
        List<ComponentInfo> sortedNodes = new ArrayList<>(allNodes);
        sortedNodes.sort(Comparator.comparing(ComponentInfo::getInstanceId));
        
        // Calcula novas faixas
        Map<String, PartitionRange> newPartitions = calculatePartitions(sortedNodes);
        
        // Verifica se houve mudança
        if (!newPartitions.equals(partitionMap)) {
            PartitionRange oldRange = myRange;
            
            // Atualiza as partições
            partitionMap.clear();
            partitionMap.putAll(newPartitions);
            
            // Atualiza minha faixa
            myRange = partitionMap.get(nodeId);
            
            if (myRange != null) {
                LOGGER.info("Nova faixa atribuída: " + myRange);
                
                if (onRangeAssigned != null) {
                    onRangeAssigned.accept(myRange);
                }
                
                // Se a faixa mudou, pode ser necessário migrar dados
                if (oldRange != null && !oldRange.equals(myRange)) {
                    triggerDataMigration(oldRange, myRange);
                }
            }
        }
    }
    
    /**
     * Calcula as partições baseado nos nós disponíveis.
     */
    private Map<String, PartitionRange> calculatePartitions(List<ComponentInfo> nodes) {
        Map<String, PartitionRange> partitions = new HashMap<>();
        
        if (nodes.isEmpty()) return partitions;
        
        if (nodes.size() == 1) {
            // Único nó - responsável por todas as chaves
            ComponentInfo node = nodes.get(0);
            partitions.put(node.getInstanceId(), 
                new PartitionRange(null, null, node.getInstanceId(), 
                    node.getInstanceId().equals(nodeId)));
        } else {
            // Múltiplos nós - divide o espaço de chaves
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
    
    /**
     * Calcula a chave de partição baseada no índice.
     */
    private String getPartitionKey(int index, int totalPartitions) {
        if (index == 0) return null; // Primeira partição começa do início
        if (index >= totalPartitions) return null; // Última partição vai até o fim
        
        // Usa caracteres ASCII para dividir o espaço
        char keyChar = (char) ('A' + (index * 26 / totalPartitions));
        return String.valueOf(keyChar);
    }
    
    /**
     * Sincroniza metadados com outros nós.
     */
    private void synchronizeMetadata() {
        // Em uma implementação real, isso sincronizaria metadados de particionamento
        // com outros nós para garantir consistência
    }
    
    /**
     * Triggera migração de dados quando necessário.
     */
    private void triggerDataMigration(PartitionRange oldRange, PartitionRange newRange) {
        if (onDataMigration != null) {
            String migrationInfo = String.format("Migração necessária: %s -> %s", 
                oldRange, newRange);
            onDataMigration.accept(migrationInfo);
        }
    }
}