# Resultados dos Testes de Capacidade

## Resultados por Protocolo

### **HTTP**

#### Knee Capacity: **100 threads**
- **Throughput no Knee:** 16.094 req/seg
- **Tempo de Resposta Médio:** 5,79 ms
- **Tempo de Resposta (90º percentil):** 14 ms
- **Taxa de Erro:** 0%

**Evolução do Throughput:**
- 10 threads: 13.047 req/seg (baseline)
- 20 threads: 14.535 req/seg (+11,4%)
- 50 threads: 15.227 req/seg (+4,8%)
- **100 threads: 16.094 req/seg (+5,7%)** ← Knee
- 150 threads: 14.839 req/seg (-7,8%)
- 200 threads: 14.635 req/seg (-1,4%)

#### Usable Capacity: **20-50 threads**
- **Throughput:** 14.500-15.200 req/seg
- **Tempo de Resposta:** 1,2-3,0 ms
- **Por quê?** Melhor eficiência antes dos retornos decrescentes começarem

---

### **TCP**

#### Knee Capacity: **40 threads**
- **Throughput no Knee:** 13.660 req/seg
- **Tempo de Resposta Médio:** 2,75 ms
- **Tempo de Resposta (90º percentil):** 5 ms
- **Taxa de Erro:** 0%

**Evolução do Throughput:**
- 10 threads: 10.213 req/seg (baseline)
- 20 threads: 12.669 req/seg (+24,0%)
- 30 threads: 13.521 req/seg (+6,7%)
- **40 threads: 13.660 req/seg (+1,0%)** ← Knee
- 50 threads: 12.596 req/seg (-7,8%)
- 100 threads: 11.228 req/seg (-10,9%)
- 150 threads: 11.889 req/seg (+5,9% - inconsistente)
- 200 threads: 11.277 req/seg (-5,2%)

#### Usable Capacity: **20-30 threads**
- **Throughput:** 12.600-13.500 req/seg
- **Tempo de Resposta:** 1,5-2,1 ms
- **Por quê?** Melhor eficiência antes do knee, evitando sobrecarga de gerenciamento de conexões


### **UDP**

#### Knee Capacity: **30 threads**
- **Throughput no Knee:** 20.215 req/seg
- **Tempo de Resposta Médio:** 1,38 ms
- **Tempo de Resposta (90º percentil):** 3 ms
- **Taxa de Erro:** 0%

**Evolução do Throughput:**
- 10 threads: 16.477 req/seg (baseline)
- 20 threads: 19.554 req/seg (+18,7%)
- **30 threads: 20.215 req/seg (+3,4%)** ← Knee
- 40 threads: 19.109 req/seg (-5,5%)
- 50 threads: 18.562 req/seg (-2,9%)
- 100 threads: 17.402 req/seg (-6,3%)
- 150 threads: 19.478 req/seg (+11,9% - inconsistente)
- 200 threads: 17.614 req/seg (-9,6%)

#### Usable Capacity: **20-30 threads**
- **Throughput:** 19.500-20.200 req/seg
- **Tempo de Resposta:** 0,9-1,4 ms
- **Por quê?** Zona de desempenho máximo (usable = knee para UDP)


## Comparação entre Protocolos

| Protocolo | Knee Capacity | Throughput @ Knee | Usable Capacity | Throughput @ Usable |
|-----------|---------------------|---------------------|----------------------|------------------------|
| **HTTP**  | 100 threads         | 16.094 req/seg      | 20-50 threads        | 14.500-15.200 req/seg  |
| **UDP**   | 30 threads          | 20.215 req/seg      | 20-30 threads        | 19.500-20.200 req/seg  |
| **TCP**   | 40 threads          | 13.660 req/seg      | 20-30 threads        | 12.600-13.500 req/seg  |

### **Tabela Detalhada de Desempenho**

| Threads | HTTP (req/seg) | TCP (req/seg) | UDP (req/seg) |
|---------|---------------|---------------|---------------|
| 10      | 13.047        | 10.213        | 16.477        |
| 20      | 14.535        | 12.669        | 19.554        |
| 30      | -             | 13.521        | **20.215**    |
| 40      | -             | **13.660**    | 19.109        |
| 50      | 15.227        | 12.596        | 18.562        |
| 100     | **16.094**    | 11.228        | 17.402        |
| 150     | 14.839        | 11.889        | 19.478        |
| 200     | 14.635        | 11.277        | 17.614        |


## Principais Descobertas

### **1. UDP é o Campeão de Throughput**
- ✅ **+26% mais rápido** que HTTP no pico
- ✅ **+48% mais rápido** que TCP no pico
- ✅ **Menor latência:** 1,38 ms vs HTTP 5,79 ms vs TCP 2,75 ms

### **2. HTTP Escala Melhor**
- ✅ Sustenta alto desempenho em uma **faixa mais ampla de threads** (20-100)
- ✅ Maior capacidade do knee (100 threads)
- ✅ Mais robusto para cargas de trabalho de alta concorrência em produção

### **3. TCP Tem Mais Dificuldades**
- ⚠️ Saturação mais cedo (40 threads)
- ⚠️ Throughput de pico mais baixo
- ⚠️ Mais instável em alta carga
- **Causa:** Sobrecarga de gerenciamento de conexões + lógica de redirecionamento de particionamento por faixa de chaves

### **4. Todos os Protocolos Mostram Instabilidade Além do Knee**
- Após atingir o Knee, todos os protocolos exibem comportamento errático
- Oscilações de throughput devido a contenção de recursos (CPU, memória, locks)
- TCP e UDP mostram padrões particularmente instáveis em 100+ threads


## Análise de Causa Raiz

### **Por que TCP tem desempenho pior?**

1. **Sobrecarga de Redirecionamento:**
   - TCP deve **fechar e abrir novos sockets** em cada redirecionamento de partição
   - HTTP apenas **envia nova requisição** (pool de conexões gerencia)
   - Custo duplo de handshake (SYN/ACK) em cada redirecionamento

2. **Gerenciamento de Estado:**
   - TCP mantém estado de socket, rastreia qual instância possui qual faixa de chaves
   - HTTP é stateless - cada requisição é independente
   - Mais memória, mais locks, mais contenção em alta concorrência

3. **Padrão de Redirecionamento:**
   ```
   Gateway recebe requisição → verifica faixa de chaves → envia "REDIRECT|instanceId|host|httpPort"
   Cliente deve parsear → fechar socket → calcular tcpPort (httpPort+1) → abrir novo socket
   ```
   - Acontece em **toda** operação CREATE/STORE/RETRIEVE para chaves em instância não-gateway
   - Em 50+ threads: centenas de redirecionamentos/seg → centenas de ciclos fechar/abrir socket

### **Por que UDP tem melhor desempenho?**

1. **Sem conexão:** Nenhuma sobrecarga de estabelecimento/encerramento de conexão
2. **Stateless:** Nenhum estado de conexão para gerenciar
3. **Menor overhead de protocolo:** Cabeçalhos mais simples que TCP
4. **Redirecionamentos mais baratos:** Apenas envia novo pacote, sem gerenciamento de conexão

### **Por que HTTP é mais estável?**

1. **Natureza stateless:** Lida melhor com sobrecarga
2. **Pool de conexões:** Framework gerencia reutilização de conexões
3. **Parsing mais simples:** Redirecionamentos HTTP 3xx ou status de resposta JSON
4. **Implementação madura:** Bibliotecas HTTP altamente otimizadas

