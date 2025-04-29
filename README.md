# README.md - Sistema Distribuído Tolerante a Falhas

## Visão Geral
Este projeto implementa um sistema distribuído tolerante a falhas baseado em padrões descritos no livro "Patterns of Distributed Systems" de Unmesh Joshi. O sistema consiste em múltiplos componentes que se comunicam através de um API Gateway, utilizando diversos protocolos de comunicação.

## Estrutura do Projeto
O sistema é composto por três componentes principais:
- **API Gateway**: Responsável por receber requisições de clientes e roteá-las para os componentes apropriados.
- **ComponentA**: Implementa um serviço de armazenamento chave-valor.
- **ComponentB**: Implementa um serviço de processamento de eventos.

## Requisitos
- Java 11 ou superior
```bash
mvn clean package
```

### Iniciando os Componentes
1. Inicie o API Gateway primeiro:
```bash
java -jar target/distribuida.jar gateway
```

2. Inicie instâncias do ComponentA:
```bash
java -jar target/distribuida.jar componenta
```

3. Inicie instâncias do ComponentB:
```bash
java -jar target/distribuida.jar componentb
```

### Configuração
As configurações do sistema estão no arquivo `config.properties`. Para executar múltiplas instâncias de um componente, modifique as portas usando parâmetros de linha de comando:

```bash

```

## Protocolos Suportados
O sistema suporta os seguintes protocolos:
- HTTP
- TCP
- UDP

## Padrões Implementados
- **Heartbeat**: Para monitoramento da disponibilidade dos componentes
- **Leader-Follower**: Para replicação de estado e tolerância a falhas
- Outros padrões do GoF (Gang of Four)

## Testando com JMeter
1. Abra o JMeter e carregue os arquivos de teste:
   - http_test.jmx
   - tcp_test.jmx
   - udp_test.jmx

2. Certifique-se de que todos os componentes estejam em execução antes de iniciar os testes.

3. Execute os testes individualmente para cada protocolo para analisar o desempenho.

## Tolerância a Falhas
O sistema implementa mecanismos de tolerância a falhas:
- Detecção de falhas através do padrão Heartbeat
- Reeleição de líder no padrão Leader-Follower
- Reconexão automática de componentes

---

Desenvolvido como parte do projeto acadêmico da disciplina de Sistemas Distribuídos na UFRN.
- Maven 3.6.0 ou superior
- JMeter 5.5 ou superior (para testes de carga)

## Como Executar

### Compilando o Projeto