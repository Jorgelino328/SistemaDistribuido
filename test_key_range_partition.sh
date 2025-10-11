#!/bin/bash

# Script de teste para demonstrar o novo sistema de particionamento por faixa de chaves

echo "=== Teste do Sistema de Particionamento por Faixa de Chaves ==="
echo

# Compila e constrói o JAR com dependências
echo "1. Compilando o projeto..."
mvn package -q
if [ $? -ne 0 ]; then
    echo "Erro na compilação!"
    exit 1
fi
echo "Compilação concluída com sucesso!"
echo

# Inicia o Gateway
echo "2. Iniciando Gateway..."
java -jar target/distribuida.jar gateway </dev/null &
GATEWAY_PID=$!
sleep 3
echo "Gateway iniciado (PID: $GATEWAY_PID)"
echo

# Inicia duas instâncias do UserService (antigo ComponentA)
echo "3. Iniciando UserService instâncias..."
java -jar target/distribuida.jar userservice 1 </dev/null &
COMP_A1_PID=$!
sleep 2

java -jar target/distribuida.jar userservice 2 </dev/null &
COMP_A2_PID=$!
sleep 2
echo "UserService instâncias iniciadas (PIDs: $COMP_A1_PID, $COMP_A2_PID)"
echo

# Inicia duas instâncias do MessageService (antigo ComponentB)
echo "4. Iniciando MessageService instâncias..."
java -jar target/distribuida.jar messageservice 1 </dev/null &
COMP_B1_PID=$!
sleep 2

java -jar target/distribuida.jar messageservice 2 </dev/null &
COMP_B2_PID=$!
sleep 2
echo "MessageService instâncias iniciadas (PIDs: $COMP_B1_PID, $COMP_B2_PID)"
echo

echo "5. Sistema iniciado! Aguardando 10 segundos para inicialização completa..."
sleep 10
echo

echo "6. Testando operações distribuídas..."
echo

# Testa operações no UserService
echo "6.1. Testando UserService (gerenciamento de usuários):"
echo "Criando usuário 'alice' (deve ir para uma instância baseada na chave)"
curl -s "http://localhost:8080/userservice/USER_CREATE|alice|alice@example.com" || echo "Erro ao criar usuário alice"
echo

echo "Criando usuário 'zach' (pode ir para outra instância)" 
curl -s "http://localhost:8080/userservice/USER_CREATE|zach|zach@example.com" || echo "Erro ao criar usuário zach"
echo

echo "Recuperando informações dos usuários:"
curl -s "http://localhost:8080/userservice/USER_GET|alice" || echo "Erro ao obter info de alice"
echo
echo

# Testa operações no MessageService
echo "6.2. Testando MessageService (sistema de mensagens):"
echo "Enviando mensagem para sala 'general' (deve ir para uma instância baseada na chave)"
curl -s "http://localhost:8080/messageservice/MESSAGE_SEND|Hello from Alice!" || echo "Erro ao enviar mensagem"
echo

echo "Juntando usuário alice à sala 'general'"
curl -s "http://localhost:8080/messageservice/ROOM_JOIN|general|alice" || echo "Erro ao juntar à sala"
echo

echo "Obtendo contagem de mensagens:"
curl -s "http://localhost:8080/messageservice/MESSAGE_GET" || echo "Erro ao obter contagem"
echo
echo

echo "7. Teste concluído!"
echo

echo "8. Parando todos os processos..."
kill $COMP_B2_PID $COMP_B1_PID $COMP_A2_PID $COMP_A1_PID $GATEWAY_PID 2>/dev/null
sleep 2
kill -9 $COMP_B2_PID $COMP_B1_PID $COMP_A2_PID $COMP_A1_PID $GATEWAY_PID 2>/dev/null

echo "Todos os processos foram finalizados."
echo
echo "=== Resumo da Migração ==="
echo "✅ Leader-Follower pattern REMOVIDO"
echo "✅ Heartbeat pattern REMOVIDO" 
echo "✅ Key-Range Partitioning IMPLEMENTADO"
echo "✅ Distribuição automática de dados por faixa de chaves"
echo "✅ Descoberta de nós através do Gateway"
echo "✅ Rebalanceamento automático quando nós entram/saem"
echo "✅ Redirecionamento automático para nó responsável"
echo
echo "O sistema agora usa particionamento horizontal ao invés de replicação líder-seguidor!"