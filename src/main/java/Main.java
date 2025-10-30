import common.config.SystemConfig;
import component.UserService;
import component.FileStorageService;
import gateway.APIGateway;
import java.util.Scanner;

public class Main {
    
    public static void main(String[] args) {
        SystemConfig.getInstance();
        
        String componentType = "gateway";
        int instanceNumber = 1;
        
        if (args.length > 0) {
            componentType = args[0].toLowerCase();
        }
        
        if (args.length > 1) {
            try {
                instanceNumber = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                printUsage();
                System.exit(1);
            }
        }
        
        switch (componentType) {
            case "gateway":
                startGateway();
                break;
            case "userservice":
                startUserService(instanceNumber);
                break;
            case "fileservice":
                startFileService(instanceNumber);
                break;
            default:
                printUsage();
                System.exit(1);
        }
    }
    
 
    private static void startGateway() {
        
        APIGateway gateway = new APIGateway();
        gateway.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::stop));
        
        waitForExitCommand(gateway::stop);
    }
    

    private static void startUserService(int instanceNumber) {
        
        SystemConfig config = SystemConfig.getInstance();
        String host = "localhost";
        String gatewayHost = config.getGatewayHost();
        int gatewayRegistrationPort = config.getRegistrationPort();
        
        int httpPort, tcpPort, udpPort;
        
        if (instanceNumber == 1) {
            httpPort = config.getIntProperty("userService.http.port", 8181);
            tcpPort = config.getIntProperty("userService.tcp.port", 8182);
            udpPort = config.getIntProperty("userService.udp.port", 8183);
        } else {
            httpPort = config.getIntProperty("userService.http.port." + instanceNumber, 8191);
            tcpPort = config.getIntProperty("userService.tcp.port." + instanceNumber, 8192);
            udpPort = config.getIntProperty("userService.udp.port." + instanceNumber, 8193);
        }
        
        UserService component = new UserService(
            host, httpPort, tcpPort, udpPort, gatewayHost, gatewayRegistrationPort
        );
        component.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(component::stop));
        
        waitForExitCommand(component::stop);
    }
    

    private static void startFileService(int instanceNumber) {
        SystemConfig config = SystemConfig.getInstance();
        String host = "localhost";
        String gatewayHost = config.getGatewayHost();
        int gatewayRegistrationPort = config.getRegistrationPort();
        
        int httpPort, tcpPort, udpPort;
        
        if (instanceNumber == 1) {
            httpPort = config.getIntProperty("fileService.http.port", 8281);
            tcpPort = config.getIntProperty("fileService.tcp.port", 8282);
            udpPort = config.getIntProperty("fileService.udp.port", 8283);
        } else {
            httpPort = config.getIntProperty("fileService.http.port." + instanceNumber, 8291);
            tcpPort = config.getIntProperty("fileService.tcp.port." + instanceNumber, 8292);
            udpPort = config.getIntProperty("fileService.udp.port." + instanceNumber, 8293);
        }
        
        FileStorageService component = new FileStorageService(
            host, httpPort, tcpPort, udpPort, gatewayHost, gatewayRegistrationPort
        );
        component.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(component::stop));
        
        waitForExitCommand(component::stop);
    }
    
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
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (java.util.NoSuchElementException e) {
            System.out.println("Executando em modo daemon - use CTRL+C para parar...");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        } finally {
            scanner.close();
        }
    }
    
    private static void printUsage() {
        System.out.println("Uso: java -jar sistema-distribuido.jar [tipoComponente] [numeroInstancia]");
        System.out.println("  onde tipoComponente é um dos seguintes:");
        System.out.println("    gateway       - Inicia o Gateway de API");
        System.out.println("    userservice   - Inicia uma instância do UserService (gerenciamento de usuários)");
        System.out.println("    fileservice   - Inicia uma instância do FileStorageService (armazenamento de arquivos)");
        System.out.println("  numeroInstancia é opcional (padrão: 1):");
        System.out.println("    1            - Primeira instância do componente");
        System.out.println("    2            - Segunda instância do componente");
    }
}
