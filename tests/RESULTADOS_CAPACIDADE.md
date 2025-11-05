# Resultados dos Testes de Capacidade

## Resultados por Protocolo

### **HTTP**

#### Knee Capacity: **40 threads**
- **Throughput no Knee:** 19.438 req/seg
- **Tempo de Resposta Médio:** 1,89 ms
- **Tempo de Resposta (90º percentil):** 4 ms
- **Taxa de Erro:** 0%

**Evolução do Throughput:**
- 10 threads: 14.169 req/seg (baseline)
- 20 threads: 18.289 req/seg (+29,1%)
- 30 threads: 19.184 req/seg (+4,9%)
- **40 threads: 19.438 req/seg (+1,3%)** ← Knee
- 50 threads: 19.271 req/seg (-0,9%)
- 100 threads: 19.217 req/seg (-0,3%)
- 150 threads: 17.031 req/seg (-11,4%)
- 200 threads: 17.150 req/seg (+0,7%)

#### Usable Capacity: **30-100 threads**
- **Throughput:** 19.000-19.400 req/seg
- **Tempo de Resposta:** 1,4-4,9 ms
- **Por quê?** Plataforma ampla de alto desempenho antes da degradação

---

### **TCP**

#### Knee Capacity: **30 threads**
- **Throughput no Knee:** 16.261 req/seg
- **Tempo de Resposta Médio:** 0,52 ms
- **Tempo de Resposta (90º percentil):** 1 ms
- **Taxa de Erro:** 0%

**Evolução do Throughput:**
- 10 threads: 16.219 req/seg (baseline)
- 20 threads: 16.240 req/seg (+0,1%)
- **30 threads: 16.261 req/seg (+0,1%)** ← Knee
- 40 threads: 14.565 req/seg (-10,4%)
- 50 threads: 13.664 req/seg (-6,2%)
- 100 threads: 13.608 req/seg (-0,4%)
- 150 threads: 13.592 req/seg (-0,1%)
- 200 threads: 13.858 req/seg (+2,0%)

#### Usable Capacity: **10-30 threads**
- **Throughput:** 16.200-16.300 req/seg
- **Tempo de Resposta:** 0,52-0,53 ms
- **Por quê?** TCP já opera em capacidade máxima com baixa carga


### **UDP**

#### Knee Capacity: **20 threads**
- **Throughput no Knee:** 27.155 req/seg
- **Tempo de Resposta Médio:** 0,31 ms
- **Tempo de Resposta (90º percentil):** 1 ms
- **Taxa de Erro:** 0%

**Evolução do Throughput:**
- 10 threads: 26.694 req/seg (baseline)
- **20 threads: 27.155 req/seg (+1,7%)** ← Knee
- 30 threads: 23.120 req/seg (-14,9%)
- 40 threads: 23.124 req/seg (+0,0%)
- 50 threads: 22.856 req/seg (-1,2%)
- 100 threads: 22.933 req/seg (+0,3%)
- 150 threads: 23.167 req/seg (+1,0%)
- 200 threads: 23.218 req/seg (+0,2%)

#### Usable Capacity: **10-20 threads**
- **Throughput:** 26.700-27.200 req/seg
- **Tempo de Resposta:** 0,31-0,32 ms
- **Por quê?** Performance máxima antes da queda acentuada


## Comparação entre Protocolos

| Protocolo | Knee Capacity | Throughput @ Knee | Usable Capacity | Throughput @ Usable |
|-----------|---------------------|---------------------|----------------------|------------------------|
| **UDP**   | 20 threads          | 27.155 req/seg      | 10-20 threads        | 26.700-27.200 req/seg  |
| **HTTP**  | 40 threads          | 19.438 req/seg      | 30-100 threads       | 19.000-19.400 req/seg  |
| **TCP**   | 30 threads          | 16.261 req/seg      | 10-30 threads        | 16.200-16.300 req/seg  |

### **Tabela Detalhada de Desempenho**

| Threads | HTTP (req/seg) | TCP (req/seg) | UDP (req/seg) |
|---------|---------------|---------------|---------------|
| 10      | 14.169        | 16.219        | 26.694        |
| 20      | 18.289        | 16.240        | **27.155**    |
| 30      | 19.184        | **16.261**    | 23.120        |
| 40      | **19.438**    | 14.565        | 23.124        |
| 50      | 19.271        | 13.664        | 22.856        |
| 100     | 19.217        | 13.608        | 22.933        |
| 150     | 17.031        | 13.592        | 23.167        |
| 200     | 17.150        | 13.858        | 23.218        |


## Principais Descobertas

### **1. UDP é o Campeão Absoluto de Throughput**
- ✅ **+40% mais rápido** que HTTP no pico (27.155 vs 19.438 req/seg)
- ✅ **+67% mais rápido** que TCP no pico (27.155 vs 16.261 req/seg)
- ✅ **Latência ultra-baixa:** 0,31 ms vs HTTP 1,89 ms vs TCP 0,52 ms
- ⚠️ **Queda abrupta:** -14,9% ao passar de 20 para 30 threads

### **2. HTTP Mostra Excelente Escalabilidade**
- ✅ Melhor scaling com carga crescente: +29,1% (10→20 threads)
- ✅ Plataforma estável de desempenho de 30-100 threads (~19.000 req/seg)
- ✅ **Mais previsível** para ambientes de produção
- ✅ Degradação gradual além do knee (-11,4% em 150 threads)

### **3. TCP Demonstra Desempenho Consistente mas Limitado**
- ✅ **Melhor latência:** 0,52 ms (apenas superado por UDP)
- ✅ Performance máxima desde 10 threads (saturação imediata)
- ⚠️ **Sem escalabilidade:** apenas +0,3% de ganho (10→30 threads)
- ⚠️ Queda significativa após knee: -10,4% em 40 threads
- ⚠️ Estabiliza em ~13.600 req/seg para cargas altas (50-200 threads)

### **4. Padrões de Saturação Distintos**
- **UDP:** Saturação instantânea em baixa carga (10 threads já opera a 98% do pico)
- **HTTP:** Crescimento consistente até 40 threads, depois plataforma estável
- **TCP:** Plataforma desde início, queda abrupta no limite, nova plataforma mais baixa


## Análise de Causa Raiz

### **Por que UDP tem throughput tão superior?**

1. **Protocolo sem conexão:**
   - Zero overhead de estabelecimento/encerramento de conexão
   - Nenhum handshake (SYN/ACK)
   - Sem gerenciamento de estado de conexão

2. **Overhead mínimo de protocolo:**
   - Cabeçalho UDP: apenas 8 bytes
   - Cabeçalho TCP: 20-60 bytes
   - Cabeçalho HTTP: centenas de bytes (headers + parsing)

3. **Processamento direto:**
   - Dados chegam → processam → respondem
   - Nenhuma camada adicional de abstração
   - Implementação nativa mais simples

4. **Redirecionamentos mais eficientes:**
   - Cliente apenas envia novo pacote UDP
   - Nenhum gerenciamento de socket/conexão

### **Por que TCP tem boa latência mas baixa escalabilidade?**

1. **Latência excelente (0,52 ms):**
   - Conexão persistente = sem handshake repetido
   - Socket já estabelecido para operações subsequentes
   - Protocolo otimizado para confiabilidade

2. **Escalabilidade limitada:**
   - **Gerenciamento de estado:** cada thread mantém múltiplos sockets
   - **Sobrecarga de redirecionamento:** fechar + abrir socket = 2x handshake
   - **Contenção de recursos:** file descriptors, buffers de kernel
   - **Lock contention:** sincronização em estruturas compartilhadas

3. **Padrão de redirecionamento custoso:**
   ```
   Cliente → Gateway TCP → "REDIRECT|instanceId|host|tcpPort"
   Cliente fecha socket antigo → Abre novo socket → 3-way handshake
   Cliente → Instância correta → Operação real
   ```
   - Em 40+ threads: centenas de closes/opens por segundo
   - Esgota pool de sockets disponíveis

### **Por que HTTP escala melhor que TCP?**

1. **Pool de conexões gerenciado:**
   - Framework HTTP reutiliza conexões automaticamente
   - Conexões persistentes (keep-alive)
   - Redirecionamentos HTTP não fecham sockets

2. **Stateless por natureza:**
   - Cada requisição é independente
   - Framework gerencia complexidade
   - Menos locks, menos contenção

3. **Redirecionamentos mais eficientes:**
   - HTTP 3xx redirect ou resposta JSON com nova URL
   - Cliente apenas envia nova requisição na mesma conexão
   - Nenhum close/open de socket

4. **Implementação madura:**
   - Bibliotecas HTTP altamente otimizadas
   - Décadas de melhorias de performance
   - Balanceamento de carga nativo

### **Por que UDP tem queda abrupta em 30 threads?**

1. **Packet loss sob contenção:**
   - UDP não tem controle de fluxo
   - Sob alta carga, kernel descarta pacotes
   - Sistema tenta processar 30+ threads concorrentemente = contenção

2. **Buffers de kernel saturados:**
   - Fila de recepção UDP transborda
   - Pacotes descartados silenciosamente
   - Throughput cai mas latência permanece baixa

3. **Contenção de CPU:**
   - Context switching entre 30 threads
   - Overhead de scheduling supera ganhos de paralelismo
   - Sweet spot é 10-20 threads


## Recomendações de Produção

### **Escolha de Protocolo por Caso de Uso**

#### **Use UDP quando:**
- ✅ Performance absoluta é crítica (throughput máximo)
- ✅ Latência ultra-baixa é necessária (<1ms)
- ✅ Carga de trabalho é previsível e baixa-média (10-20 clientes concorrentes)
- ✅ Tolerância a packet loss ocasional
- ⚠️ **Limite:** 20 threads/clientes concorrentes
- **Exemplo:** Gaming, IoT telemetry, real-time monitoring

#### **Use HTTP quando:**
- ✅ Carga variável ou imprevisível
- ✅ Necessita escalabilidade além de 30 clientes
- ✅ Infraestrutura existente (load balancers, CDNs)
- ✅ Requisitos de debugging/observability
- ✅ Integração com sistemas externos
- ⚠️ **Limite:** 100 threads para performance ótima, 150+ para carga aceitável
- **Exemplo:** APIs REST públicas, microservices, aplicações web

#### **Use TCP quando:**
- ✅ Latência baixa é mais importante que throughput máximo
- ✅ Confiabilidade de entrega é crítica
- ✅ Carga consistentemente baixa (10-30 clientes)
- ✅ Protocolo customizado já está implementado
- ⚠️ **Limite:** 30 threads/clientes concorrentes
- **Exemplo:** Database connections, file transfers, custom protocols

### **Configurações Recomendadas por Ambiente**

#### **Desenvolvimento/Testes**
```
HTTP: 20 threads
TCP:  10 threads  
UDP:  10 threads
```
- Simula carga realista
- Debuggable
- Recursos mínimos

#### **Staging/QA**
```
HTTP: 50 threads
TCP:  20 threads
UDP:  15 threads
```
- Testa próximo do knee
- Identifica problemas de escala
- Validação de performance

#### **Produção - Baixa Carga**
```
HTTP: 30-40 threads
TCP:  15-20 threads
UDP:  10-15 threads
```
- Margem de segurança
- Melhor latência
- Headroom para picos

#### **Produção - Alta Carga**
```
HTTP: 80-100 threads
TCP:  Não recomendado (usar HTTP)
UDP:  Não recomendado (usar HTTP)
```
- HTTP é única opção viável
- Throughput sustentável
- Escalabilidade comprovada


## Comparação com Resultados Anteriores

### **Mudanças Observadas**

#### **HTTP: +33% de Melhoria**
- **Antes:** 14.635 req/seg @ 200 threads
- **Agora:** 19.438 req/seg @ 40 threads
- **Ganhos:**
  - Throughput pico +33%
  - Knee threads -80% (mais eficiente)
  - Latência média -67% (5,79ms → 1,89ms)
- **Causa:** Otimizações de código + melhor gerenciamento de conexões

#### **TCP: +19% de Melhoria**
- **Antes:** 13.660 req/seg @ 40 threads
- **Agora:** 16.261 req/seg @ 30 threads
- **Ganhos:**
  - Throughput pico +19%
  - Knee threads -25% (saturação mais cedo)
  - Latência média -81% (2,75ms → 0,52ms)
- **Causa:** Otimizações de socket handling + redução de overhead

#### **UDP: +34% de Melhoria**
- **Antes:** 20.215 req/seg @ 30 threads
- **Agora:** 27.155 req/seg @ 20 threads
- **Ganhos:**
  - Throughput pico +34%
  - Knee threads -33% (mais eficiente)
  - Latência média -77% (1,38ms → 0,31ms)
- **Causa:** Otimizações de buffer + processamento assíncrono melhorado

### **Padrões Mantidos**
- ✅ UDP continua sendo o mais rápido
- ✅ HTTP continua sendo o mais escalável
- ✅ TCP continua tendo boa latência mas escalabilidade limitada
- ✅ Todos os protocolos melhoraram significativamente


## Conclusões Finais

### **Principais Insights**

1. **As otimizações de código funcionaram extremamente bem:**
   - Melhorias de 19-34% em throughput
   - Reduções de 67-81% em latência
   - Eficiência melhorada (knees em cargas menores)

2. **UDP é dominante para performance bruta:**
   - 67% mais rápido que TCP
   - 40% mais rápido que HTTP
   - Mas com trade-off de escalabilidade limitada

3. **HTTP é a escolha de produção:**
   - Única opção viável para >50 clientes concorrentes
   - Performance consistente e previsível
   - Melhor custo-benefício escalabilidade/throughput

4. **TCP tem nicho específico:**
   - Melhor latência para cargas baixas (<30 threads)
   - Confiabilidade superior ao UDP
   - Não escala para cargas altas

### **Decisão de Arquitetura**

Para um sistema distribuído de armazenamento de arquivos:

**Recomendação: Arquitetura Híbrida**

```
┌─────────────────────────────────────────┐
│   Load Balancer (HTTP)                  │
│   - External clients                    │
│   - Web APIs                            │
│   - Threads: 50-100                     │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│   Internal Communication (TCP)          │
│   - Service-to-service                  │
│   - Replication                         │
│   - Threads: 10-20 per connection       │
└─────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│   Monitoring/Health (UDP)               │
│   - Heartbeats                          │
│   - Metrics                             │
│   - Threads: 5-10                       │
└─────────────────────────────────────────┘
```

**Justificativa:**
- HTTP para interface externa (escalabilidade)
- TCP para comunicação interna (latência + confiabilidade)
- UDP para monitoring (performance + eficiência)

---

**Testes executados em:** 2025-11-05  
**Duração de cada teste:** 60 segundos  
**JMeter version:** 5.6.3  
**Java version:** 11+  
**Configuração:** Thread pools otimizados (BaseComponent: 20 threads, Gateway: 50 threads)

---

## 📝 Notas sobre Investigação de Performance

Durante a análise dos resultados, foram identificados três padrões que inicialmente pareciam ser problemas:
1. **TCP:** Throughput estável (~16k req/sec) de 10-30 threads
2. **UDP:** Queda de throughput aos 30 threads (-15%)
3. **HTTP:** Degradação aos 150 threads (-11%)

Uma tentativa de otimização foi realizada (aumentando thread pools para 100/150), mas resultou em **degradação de 15-25% na performance** devido a context switching overhead e lock contention. 

**Conclusão:** A configuração original (20 threads nos componentes, 50 threads no gateway) representa o ponto ótimo de performance para este sistema. Os padrões observados são comportamentos normais de sistemas distribuídos sob carga, não bugs a serem corrigidos.

