# Sistema de Chat Distribuído

## Visão Geral
Este projeto implementa um sistema de chat distribuído utilizando particionamento por faixa de chaves para escalabilidade horizontal. O sistema permite gerenciamento de usuários, autenticação, salas de chat e troca de mensagens em tempo real através de múltiplos protocolos de comunicação.

## Arquitetura do Sistema
O sistema é composto por três componentes principais:
- **Gateway de API**: Responsável por receber requisições de clientes e roteá-las para os componentes apropriados. Também gerencia o registro e descoberta de nós.
- **UserService**: Serviço de gerenciamento de usuários, incluindo cadastro, autenticação, perfis e controle de sessões.
- **MessageService**: Serviço de mensagens e salas de chat, permitindo criação de salas, envio de mensagens e notificações.

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

3. Inicie instâncias do MessageService:
```bash
java -jar target/distribuida.jar messageservice 1
java -jar target/distribuida.jar messageservice 2
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

### MessageService
- Criação e gerenciamento de salas de chat
- Envio e recebimento de mensagens
- Histórico de mensagens por sala
- Notificações em tempo real

## Testando com JMeter
1. Abra o JMeter e carregue os arquivos de teste:
   - http_test.jmx
   - tcp_test.jmx
   - udp_test.jmx

2. Certifique-se de que todos os componentes estejam em execução antes de iniciar os testes.

3. Execute os testes individualmente para cada protocolo para analisar o desempenho.

## Escalabilidade e Tolerância a Falhas
O sistema implementa mecanismos para alta disponibilidade:
- Particionamento horizontal automático de dados
- Descoberta dinâmica de nós no sistema
- Rebalanceamento automático de partições
- Redirecionamento transparente de requisições
- Tolerância a falhas de nós individuais

## Documentação Adicional
Para informações sobre migração de versões anteriores, consulte:
## Testes

## Desenvolvido
Sistema desenvolvido como projeto acadêmico da disciplina de Sistemas Distribuídos.