# Migração para Key-Range Partitioning

## Resumo das Mudanças

Este projeto foi migrado de um sistema baseado em **Leader-Follower** e **Heartbeat** patterns para um sistema baseado em **Key-Range Partitioning**. Esta migração oferece melhor escalabilidade horizontal e eliminação de pontos únicos de falha.

## Alterações Principais

### 1. Padrões Removidos

#### Leader-Follower Pattern (`LeaderFollower.java`)
- **Removido**: Sistema de eleição de líder
- **Removido**: Replicação de estado líder-seguidor
- **Removido**: Gerenciamento de termo e liderança

#### Heartbeat Pattern (`HeartbeatPattern.java`)
- **Removido**: Sistema de monitoramento de saúde
- **Removido**: Detecção de falhas baseada em heartbeat
- **Removido**: Marcação de nós como suspeitos/mortos

### 2. Novo Padrão Implementado

#### Key-Range Partitioning (`KeyRangePartition.java`)
- **Adicionado**: Particionamento automático do espaço de chaves
- **Adicionado**: Descoberta automática de nós
- **Adicionado**: Rebalanceamento automático de partições
- **Adicionado**: Roteamento baseado em faixa de chaves

## Arquitetura Anterior vs Nova

### Arquitetura Anterior (Leader-Follower)
```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Leader    │◄───┤  Follower   │◄───┤  Follower   │
│ (Todas as   │    │ (Réplica)   │    │ (Réplica)   │
│  escritas)  │    │             │    │             │
└─────────────┘    └─────────────┘    └─────────────┘
       │                   │                   │
       └───────── Replicação de Estado ───────┘
```

### Nova Arquitetura (Key-Range Partitioning)
```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Nó A      │    │    Nó B     │    │    Nó C     │
│ (Chaves     │    │ (Chaves     │    │ (Chaves     │
│  A-H)       │    │  I-P)       │    │  Q-Z)       │
└─────────────┘    └─────────────┘    └─────────────┘
       │                   │                   │
       └──────── Sem Replicação ───────────────┘
```

## Componentes Modificados

### 1. BaseComponent.java
**Antes:**
- `HeartbeatPattern heartbeat`
- `LeaderFollower leaderFollower`
- `boolean isLeader`
- `int leaderPort`

**Depois:**
- `KeyRangePartition keyRangePartition`
- Métodos abstratos para callbacks de particionamento
- Métodos para verificar responsabilidade por chaves

### 2. ComponentA.java (Armazenamento Chave-Valor)
**Antes:**
- Operações só no líder
- Redirecionamento para líder em escritas
- Replicação de estado para seguidores

**Depois:**
- Operações no nó responsável pela chave
- Redirecionamento baseado em faixa de chaves
- Sem replicação (dados únicos por partição)

### 3. ComponentB.java (Processamento de Eventos)
**Antes:**
- Eventos processados apenas no líder
- Sincronização de lista de eventos

**Depois:**
- Eventos roteados para nó responsável
- Processamento distribuído por faixa

### 4. ComponentInfo.java
**Adicionado:**
- `String instanceId` - ID único para cada instância
- `getInstanceId()` - Getter para identificação única

### 5. ComponentRegistry.java (Gateway)
**Adicionado:**
- Suporte para descoberta de nós (`DISCOVER:type`)
- Resposta com lista de nós em formato JSON

## Benefícios da Migração

### Escalabilidade
- **Antes**: Limitado pela capacidade do líder
- **Depois**: Escala horizontalmente com número de nós

### Disponibilidade
- **Antes**: Ponto único de falha (líder)
- **Depois**: Falha de um nó afeta apenas sua partição

### Performance
- **Antes**: Todas escritas através do líder
- **Depois**: Escritas distribuídas entre nós

### Consistência
- **Antes**: Eventual consistency através de replicação
- **Depois**: Consistency por partição (sem conflitos)

## Como Funciona o Particionamento

### 1. Descoberta de Nós
```java
// Nós se registram no Gateway e descobrem outros nós
keyRangePartition.discoverNodes()
```

### 2. Cálculo de Partições
```java
// Divide espaço de chaves alfabeticamente
Node 1: A-H
Node 2: I-P  
Node 3: Q-Z
```

### 3. Roteamento de Requests
```java
// Determina nó responsável por uma chave
ComponentInfo responsibleNode = getResponsibleNode(key);
if (!isResponsibleFor(key)) {
    // Redireciona para nó correto
    return "REDIRECT|" + responsibleNode.getInstanceId();
}
```

### 4. Rebalanceamento Automático
```java
// Quando nós entram/saem, recalcula partições
scheduler.scheduleAtFixedRate(this::rebalancePartitions, 5, 30, TimeUnit.SECONDS);
```

## Configuração

### Arquivo `config.properties`
```properties
# Removido: Configurações de Heartbeat e Leader-Follower
# heartbeat.interval.ms=5000
# leader.election.timeout.ms=10000

# Adicionado: Configurações de Particionamento
partition.discovery.interval.ms=10000
partition.rebalance.interval.ms=30000
partition.metadata.sync.interval.ms=15000
```

## Testando o Sistema

Execute o script de teste:
```bash
./test_key_range_partition.sh
```

O script:
1. Inicia Gateway e múltiplas instâncias
2. Testa operações com diferentes chaves
3. Demonstra roteamento automático
4. Mostra distribuição de dados

## Operações Suportadas

### ComponentA (Chave-Valor)
- `GET|key` - Obtém valor (roteado automaticamente)
- `SET|key|value` - Define valor (roteado automaticamente)
- `RANGE` - Mostra faixa de chaves do nó
- `INFO` - Informações do nó e sua partição

### ComponentB (Eventos)
- `ADD_EVENT|data` - Adiciona evento (roteado automaticamente)
- `GET_EVENTS` - Lista eventos do nó
- `COUNT` - Conta eventos do nó
- `RANGE` - Mostra faixa do nó

## Monitoramento

### Logs de Particionamento
```
INFO: Key-Range Partition inicializado para nó: abc12345
INFO: Nova faixa atribuída: Range[A-H] -> abc12345 (responsible: true)
INFO: Detectou mudança na topologia. Nós: 3
```

### Verificação de Status
```bash
# Verifica faixa de um nó
curl "http://localhost:8080/componenta/RANGE"
# Resposta: RANGE|A|H|nodeId|true

# Informações do nó
curl "http://localhost:8080/componenta/INFO"  
# Resposta: INFO|Componente A|nodeId|5|RANGE:A-H
```

## Considerações de Implementação

### Limitações Atuais
1. **Particionamento Simples**: Usa divisão alfabética básica
2. **Sem Replicação**: Dados não são replicados entre nós
3. **Descoberta Centralizada**: Depende do Gateway para descoberta

### Melhorias Futuras Sugeridas
1. **Consistent Hashing**: Para melhor distribuição
2. **Replicação Opcional**: Para maior disponibilidade
3. **Service Discovery**: Sistema descentralizado
4. **Migração Online**: Movimentação de dados sem downtime

## Conclusão

A migração para Key-Range Partitioning eliminou com sucesso os problemas de escalabilidade e pontos únicos de falha do sistema anterior, proporcionando:

- ✅ **Escalabilidade Horizontal**: Adicionar nós aumenta capacidade
- ✅ **Eliminação do Líder**: Não há mais ponto único de falha  
- ✅ **Distribuição Automática**: Dados distribuídos automaticamente
- ✅ **Roteamento Inteligente**: Requests direcionados ao nó correto
- ✅ **Rebalanceamento Dinâmico**: Adaptação automática a mudanças

O sistema está agora preparado para crescer horizontalmente e lidar com cargas de trabalho distribuídas de forma mais eficiente.