import common.config.SystemConfig;
import component.UserService;
import component.MessageService;
import gateway.APIGateway;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Classe principal para o sistema distribuído.
 * Fornece uma interface de linha de comando para iniciar e parar os componentes.
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    
    /**
     * Método principal para iniciar o sistema distribuído.
     * 
     * @param args Argumentos da linha de comando
     */
    public static void main(String[] args) {
        SystemConfig.getInstance();
        
        // Valores padrão
        String componentType = "gateway";
        int instanceNumber = 1;
        
        // Analisa os argumentos da linha de comando
        if (args.length > 0) {
            componentType = args[0].toLowerCase();
        }
        
        // Verifica se um número de instância foi especificado
        if (args.length > 1) {
            try {
                instanceNumber = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                // LOGGER.warning("Número de instância inválido: " + args[1]);
                printUsage();
                System.exit(1);
            }
        }
        
        // Inicia o componente com base no tipo
        switch (componentType) {
            case "gateway":
                startGateway();
                break;
            case "userservice":
                startUserService(instanceNumber);
                break;
            case "messageservice":
                startMessageService(instanceNumber);
                break;
            // Mantém compatibilidade com nomes antigos
            case "componenta":
                startUserService(instanceNumber);
                break;
            case "componentb":
                startMessageService(instanceNumber);
                break;
            default:
                // LOGGER.warning("Tipo de componente desconhecido: " + componentType);
                printUsage();
                System.exit(1);
        }
    }
    
    /**
     * Inicia o Gateway de API.
     */
    private static void startGateway() {
        // LOGGER.info("Iniciando o Gateway de API...");
        
        APIGateway gateway = new APIGateway();
        gateway.start();
        
        // Adiciona um hook para desligamento
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::stop));
        
        // Aguarda o comando do usuário para parar
        waitForExitCommand(gateway::stop);
    }
    
    /**
     * Inicia uma instância do UserService.
     * 
     * @param instanceNumber Número da instância a iniciar (1 ou 2)
     */
    private static void startUserService(int instanceNumber) {
        // LOGGER.info("Iniciando o UserService (instância " + instanceNumber + ")...");
        
        SystemConfig config = SystemConfig.getInstance();
        String host = "localhost";
        String gatewayHost = config.getGatewayHost();
        int gatewayRegistrationPort = config.getRegistrationPort();
        
        int httpPort, tcpPort, udpPort;
        
        if (instanceNumber == 1) {
            httpPort = config.getIntProperty("componentA.http.port", 8181);
            tcpPort = config.getIntProperty("componentA.tcp.port", 8182);
            udpPort = config.getIntProperty("componentA.udp.port", 8183);
        } else {
            httpPort = config.getIntProperty("componentA.http.port." + instanceNumber, 8191);
            tcpPort = config.getIntProperty("componentA.tcp.port." + instanceNumber, 8192);
            udpPort = config.getIntProperty("componentA.udp.port." + instanceNumber, 8193);
        }
        
        UserService component = new UserService(
            host, httpPort, tcpPort, udpPort, gatewayHost, gatewayRegistrationPort
        );
        component.start();
        
        // Adiciona um hook para desligamento
        Runtime.getRuntime().addShutdownHook(new Thread(component::stop));
        
        // Aguarda o comando do usuário para parar
        waitForExitCommand(component::stop);
    }
    
    /**
     * Inicia uma instância do MessageService.
     * 
     * @param instanceNumber Número da instância a iniciar (1 ou 2)
     */
    private static void startMessageService(int instanceNumber) {
        // LOGGER.info("Iniciando o MessageService (instância " + instanceNumber + ")...");
        
        SystemConfig config = SystemConfig.getInstance();
        String host = "localhost";
        String gatewayHost = config.getGatewayHost();
        int gatewayRegistrationPort = config.getRegistrationPort();
        
        int httpPort, tcpPort, udpPort;
        
        if (instanceNumber == 1) {
            httpPort = config.getIntProperty("componentB.http.port", 8281);
            tcpPort = config.getIntProperty("componentB.tcp.port", 8282);
            udpPort = config.getIntProperty("componentB.udp.port", 8283);
        } else {
            httpPort = config.getIntProperty("componentB.http.port." + instanceNumber, 8291);
            tcpPort = config.getIntProperty("componentB.tcp.port." + instanceNumber, 8292);
            udpPort = config.getIntProperty("componentB.udp.port." + instanceNumber, 8293);
        }
        
        MessageService component = new MessageService(
            host, httpPort, tcpPort, udpPort, gatewayHost, gatewayRegistrationPort
        );
        component.start();
        
        // Adiciona um hook para desligamento
        Runtime.getRuntime().addShutdownHook(new Thread(component::stop));
        
        // Aguarda o comando do usuário para parar
        waitForExitCommand(component::stop);
    }
    
    /**
     * Aguarda o usuário digitar 'exit' para parar o componente.
     * 
     * @param stopHandler Runnable a ser executado ao parar
     */
    private static void waitForExitCommand(Runnable stopHandler) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Digite 'exit' para parar o componente...");
        
        try {
            while (true) {
                if (scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim().toLowerCase();
                    if ("exit".equals(command)) {
                        System.out.println("Parando o componente...");
                        stopHandler.run();
                        System.out.println("Componente parado.");
                        break;
                    }
                } else {
                    // Se não há entrada disponível, aguarda um pouco
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (java.util.NoSuchElementException e) {
            // Se não há entrada disponível, apenas mantém o componente rodando
            System.out.println("Executando em modo daemon - use CTRL+C para parar...");
            try {
                // Aguarda indefinidamente até interrupção
                Thread.currentThread().join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } finally {
            scanner.close();
        }
    }
    
    /**
     * Imprime as instruções de uso.
     */
    private static void printUsage() {
        System.out.println("Uso: java -jar sistema-distribuido.jar [tipoComponente] [numeroInstancia]");
        System.out.println("  onde tipoComponente é um dos seguintes:");
        System.out.println("    gateway       - Inicia o Gateway de API");
        System.out.println("    userservice   - Inicia uma instância do UserService (gerenciamento de usuários)");
        System.out.println("    messageservice - Inicia uma instância do MessageService (sistema de mensagens)");
        System.out.println("    componentA    - Inicia uma instância do Componente A (alias para userservice)");
        System.out.println("    componentB    - Inicia uma instância do Componente B (alias para messageservice)");
        System.out.println("  numeroInstancia é opcional (padrão: 1):");
        System.out.println("    1            - Primeira instância do componente");
        System.out.println("    2            - Segunda instância do componente");
    }
}
