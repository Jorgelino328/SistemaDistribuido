

import common.config.SystemConfig;
import component.ComponentA;
import component.ComponentB;
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
        
        
        // Analisa os argumentos da linha de comando
        String componentType = "gateway";
        if (args.length > 0) {
            componentType = args[0].toLowerCase();
        }
        
        // Inicia o componente com base no tipo
        switch (componentType) {
            case "gateway":
                startGateway();
                break;
            case "componenta":
                startComponentA();
                break;
            case "componentb":
                startComponentB();
                break;
            default:
                LOGGER.warning("Tipo de componente desconhecido: " + componentType);
                printUsage();
                System.exit(1);
        }
    }
    
    /**
     * Inicia o Gateway de API.
     */
    private static void startGateway() {
        LOGGER.info("Iniciando o Gateway de API...");
        
        APIGateway gateway = new APIGateway();
        gateway.start();
        
        // Adiciona um hook para desligamento
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::stop));
        
        // Aguarda o comando do usuário para parar
        waitForExitCommand(gateway::stop);
    }
    
    /**
     * Inicia uma instância do Componente A.
     */
    private static void startComponentA() {
        LOGGER.info("Iniciando o Componente A...");
        
        SystemConfig config = SystemConfig.getInstance();
        String host = "localhost";
        int httpPort = config.getComponentAHttpPort();
        int tcpPort = config.getComponentATcpPort();
        int udpPort = config.getComponentAUdpPort();
        int grpcPort = config.getComponentAGrpcPort();
        String gatewayHost = config.getGatewayHost();
        int gatewayRegistrationPort = config.getRegistrationPort();
        
        ComponentA component = new ComponentA(
            host, httpPort, tcpPort, udpPort, grpcPort, gatewayHost, gatewayRegistrationPort
        );
        component.start();
        
        // Adiciona um hook para desligamento
        Runtime.getRuntime().addShutdownHook(new Thread(component::stop));
        
        // Aguarda o comando do usuário para parar
        waitForExitCommand(component::stop);
    }
    
    /**
     * Inicia uma instância do Componente B.
     */
    private static void startComponentB() {
        LOGGER.info("Iniciando o Componente B...");
        
        SystemConfig config = SystemConfig.getInstance();
        String host = "localhost";
        int httpPort = config.getComponentBHttpPort();
        int tcpPort = config.getComponentBTcpPort();
        int udpPort = config.getComponentBUdpPort();
        int grpcPort = config.getComponentBGrpcPort();
        String gatewayHost = config.getGatewayHost();
        int gatewayRegistrationPort = config.getRegistrationPort();
        
        ComponentB component = new ComponentB(
            host, httpPort, tcpPort, udpPort, grpcPort, gatewayHost, gatewayRegistrationPort
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
        
        while (true) {
            String command = scanner.nextLine().trim().toLowerCase();
            if ("exit".equals(command)) {
                System.out.println("Parando o componente...");
                stopHandler.run();
                System.out.println("Componente parado.");
                break;
            }
        }
        
        scanner.close();
    }
    
    /**
     * Imprime as instruções de uso.
     */
    private static void printUsage() {
        System.out.println("Uso: java -jar sistema-distribuido.jar [tipoComponente]");
        System.out.println("  onde tipoComponente é um dos seguintes:");
        System.out.println("    gateway     - Inicia o Gateway de API");
        System.out.println("    componentA  - Inicia uma instância do Componente A");
        System.out.println("    componentB  - Inicia uma instância do Componente B");
    }
}