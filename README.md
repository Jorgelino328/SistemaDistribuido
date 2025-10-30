# Sistema Distribuído de Armazenamento de Arquivos

## Visão Geral
Este projeto implementa um sistema distribuído de armazenamento de arquivos utilizando particionamento por faixa de chaves para escalabilidade horizontal. O sistema permite gerenciamento de usuários, autenticação e armazenamento/recuperação de arquivos através de múltiplos protocolos de comunicação.

## Arquitetura do Sistema
O sistema é composto por três componentes principais:
- **Gateway de API**: Responsável por receber requisições de clientes e roteá-las para os componentes apropriados. Também gerencia o registro e descoberta de nós.
- **UserService**: Serviço de gerenciamento de usuários, incluindo cadastro, autenticação, perfis e controle de sessões.
- **FileStorageService**: Serviço de armazenamento de arquivos, permitindo salvar e recuperar arquivos baseado em chaves de usuário.

## Padrões Implementados
- **Key-Range Partitioning**: Distribui dados horizontalmente entre múltiplos nós baseado em faixas de chaves
- **Service Discovery**: Descoberta automática de nós através do Gateway
- **Automatic Rebalancing**: Rebalanceamento automático quando nós entram ou saem do sistema

## Requisitos
- Java 11 ou superior
- Maven 3.6.0 ou superior
- JMeter 5.5 ou superior (para testes de carga)

## Como Executar

### Compilando o Projeto
```bash
mvn clean package
```

### Iniciando os Componentes
1. Inicie o Gateway de API primeiro:
```bash
java -jar target/distribuida.jar gateway
```

2. Inicie instâncias do UserService:
```bash
java -jar target/distribuida.jar userservice 1
java -jar target/distribuida.jar userservice 2
```

3. Inicie instâncias do FileStorageService:
```bash
java -jar target/distribuida.jar fileservice 1
java -jar target/distribuida.jar fileservice 2
```

### Configuração
As configurações do sistema estão no arquivo `config.properties`. As principais configurações incluem:
- Portas dos serviços HTTP, TCP e UDP
- Parâmetros de particionamento (tamanho da partição, threshold de rebalanceamento)
- Configurações do Gateway (host, porta de registro)

## Protocolos Suportados
O sistema suporta múltiplos protocolos de comunicação:
- **HTTP**: Para operações RESTful e interface web
- **TCP**: Para comunicação confiável entre componentes
- **UDP**: Para operações leves e heartbeat

## Funcionalidades

### UserService
- Cadastro de usuários com validação de dados
- Sistema de autenticação com tokens
- Gerenciamento de perfis de usuário
- Controle de sessões e presença

### FileStorageService
- Armazenamento de arquivos com chaves baseadas em usuário
- Recuperação de arquivos por chave
- Particionamento automático por faixa de chaves (A-M, N-Z)
- Suporte a múltiplos protocolos (HTTP, TCP, UDP)


