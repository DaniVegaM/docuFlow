import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class ClientUDP {
    private static final int SERVER_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;
    private static final int WINDOW_SIZE = 5; 
    private static final String SERVER_IP = "localhost";
    private static String fileName = "";
    private static String folderPath = "";
    private static String destinationPath = "./Server/";
    private static String newFileName = "";
    private static String filePath = "";

    public static void main(String[] args) throws IOException, InterruptedException {
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress serverAddress = InetAddress.getByName(SERVER_IP);
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("¿What do you want to do?");
            System.out.println("1. UPLOAD\n2. DOWNLOAD\n3. CREATE FOLDER\n4. DELETE FOLDER\n5. RENAME FILE");
            System.out.print("Enter an option: ");
            String command = scanner.nextLine();

            LoadingScreen loadingScreen = new LoadingScreen();
            Thread loadingThread = new Thread(loadingScreen);

            switch(command){
                case "1": //UPLOAD
                    System.out.println("\nName of File: ");
                    fileName = scanner.nextLine();
                    System.out.println("\nFile Location: ");
                    destinationPath+= scanner.nextLine();

                    loadingThread.start();
                    sendFileWithMetadata(clientSocket, serverAddress, fileName, destinationPath);
                    loadingScreen.stopLoading();
                    loadingThread.join();
            
                    System.out.print("\r\033[1;32mFile sent successfully!                \033[0m\n");

                    Thread.sleep(1000);
                    clearConsole();
                break;
                case "2": //DOWNLOAD
                    System.out.println("File name: ");
                    fileName = scanner.nextLine();
                    System.out.println("File location: ");
                    destinationPath += scanner.nextLine();
                
                    loadingThread.start();
                    requestFile(clientSocket, serverAddress, fileName, destinationPath);
                    loadingScreen.stopLoading();
                    loadingThread.join();
                
                    System.out.print("\r\033[1;32mFile downloaded successfully!                \033[0m\n");
                    Thread.sleep(1000);
                    clearConsole();
                break;
                case "3": //CREATEF
                    System.out.println("Folder name: ");
                    fileName = scanner.nextLine();
                    System.out.println("Where do you want to create the folder? (location): ");
                    folderPath = scanner.nextLine();
                
                    loadingThread.start();
                    createFORdeleteFORrenameFile(clientSocket, serverAddress, fileName, folderPath, "C");
                    loadingScreen.stopLoading();
                    loadingThread.join();
            
                    System.out.print("\r\033[1;32mFolder created succesfuly!                \033[0m\n");

                    Thread.sleep(1000);
                    clearConsole();
                break;
                case "4": //DELETEF
                    System.out.println("Folder name: ");
                    fileName = scanner.nextLine();
                    System.out.println("Where is the folder? (location): ");
                    folderPath = scanner.nextLine();
                
                    loadingThread.start();
                    createFORdeleteFORrenameFile(clientSocket, serverAddress, fileName, folderPath, "D");
                    loadingScreen.stopLoading();
                    loadingThread.join();
            
                    System.out.print("\r\033[1;32mFolder deleted succesfuly!                \033[0m\n");

                    Thread.sleep(1000);
                    clearConsole();
                break;
                case "5": //RENAME
                    System.out.println("Original name of file");
                    fileName = scanner.nextLine();
                    System.out.println("New name of file");
                    newFileName = scanner.nextLine();
                    System.out.println("File location?: ");
                    filePath = scanner.nextLine();
                
                    loadingThread.start();
                    createFORdeleteFORrenameFile(clientSocket, serverAddress, fileName, "", "R");
                    loadingScreen.stopLoading();
                    loadingThread.join();
            
                    System.out.print("\r\033[1;32mFolder created succesfuly!                \033[0m\n");

                    Thread.sleep(1000);
                    clearConsole();
                break;
            }
            //Reset variables
            destinationPath = "./Server/";
        }
    }

    //UPLOAD
    public static void sendFileWithMetadata(DatagramSocket clientSocket, InetAddress serverAddress, String fileName, String destinationPath) throws IOException, InterruptedException {
        File file = new File("./Client/" + fileName);
        if (!file.exists()) {
            System.out.println("CLIENT ERROR: The file doesn't exist");
            return;
        }
        
        //Header
        String header = "UPLOAD;" + file.getName() + ";" + file.length() + ";" + destinationPath; 
        byte[] headerBytes = header.getBytes();
        
        //Sending the header
        DatagramPacket headerPacket = new DatagramPacket(headerBytes, headerBytes.length, serverAddress, SERVER_PORT);
        clientSocket.send(headerPacket);
        
        //Setting File
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] fileBuffer = new byte[BUFFER_SIZE - 4];
        byte[] sendData = new byte[BUFFER_SIZE];
        
        int bytesRead;
        int sequenceNumber = 0;
        int windowStart = 0; 
        IntWrapper lastAckReceived = new IntWrapper(-1);
        int attempts = 3; //Number of attempts to resend packages
    
        //Queue to remember which packets we sent
        Queue<Integer> sentPackets = new LinkedList<>();
    
        while ((bytesRead = fileInputStream.read(fileBuffer)) != -1 || !sentPackets.isEmpty()) { //SENDING PACKAGES
    
            // If there is space in the window, keep sending packets
            if(sentPackets.isEmpty()){
                attempts = 3;
                while (windowStart - lastAckReceived.value < WINDOW_SIZE && bytesRead != -1) {
                    addSequenceNumberToPacket(sendData, sequenceNumber);
                    System.arraycopy(fileBuffer, 0, sendData, 4, bytesRead);
                    
                    // Send packet
                    DatagramPacket filePacket = new DatagramPacket(sendData, bytesRead + 4, serverAddress, SERVER_PORT);
                    clientSocket.send(filePacket);
                    sentPackets.add(sequenceNumber);
        
                    sequenceNumber++;
                    bytesRead = fileInputStream.read(fileBuffer);

                    //Wait until send the next package
                    Thread.sleep(1); //PRETTY IMPORTANT!!!!!!!
                }
            }
    
            // Handling ACKs
            boolean ackReceived = false;
            while (!ackReceived && !sentPackets.isEmpty()) {
                try {
                    byte[] ackBuffer = new byte[BUFFER_SIZE];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    clientSocket.setSoTimeout(1000); //Wait for 1 second
                    clientSocket.receive(ackPacket);
    
                    // Read ACK
                    // System.out.println("CLIENT: ACK received for all packages in a window");
                    String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    String[] ackParts = ack.split(";");
                    if (ackParts[0].equals("ACK")){
                        int ackSequenceNumber = Integer.parseInt(ackParts[1]);
        
                        if (ackSequenceNumber > lastAckReceived.value) { 
                            lastAckReceived.value = ackSequenceNumber;
                            // Remove acknowledged packets
                            sentPackets.removeIf(seqNum -> seqNum <= lastAckReceived.value);
                            ackReceived = true;
                        }
                    } else{
                        // System.out.println("CLIENT: I received an strange Datagram :/");
                    }
                } catch (IOException e) {
                    // Resend if timeout occurs
                    if (attempts > 0) {
                        // System.out.println("CLIENT: Timeout, resending unacknowledged packets");
                        for (int packetToResend : sentPackets) {
                            addSequenceNumberToPacket(sendData, packetToResend);
                            DatagramPacket resendPacket = new DatagramPacket(sendData, BUFFER_SIZE, serverAddress, SERVER_PORT);
                            clientSocket.send(resendPacket);
                        }
                        attempts--;
                    } else {
                        // System.out.println("CLIENT: ERROR, maximum number of resend attempts reached");
                        return;
                    }
                }
            }
        }
        
        fileInputStream.close();
        // System.out.println("CLIENT: File sent successfully!");
    }    

    private static void addSequenceNumberToPacket(byte[] packet, int sequenceNumber) {
        packet[0] = (byte) (sequenceNumber >> 24);
        packet[1] = (byte) (sequenceNumber >> 16);
        packet[2] = (byte) (sequenceNumber >> 8);
        packet[3] = (byte) sequenceNumber;
    }

    public static void requestFile(DatagramSocket clientSocket, InetAddress serverAddress, String fileName, String destinationPath) throws IOException {
        // Enviar la solicitud de descarga
        String requestHeader = "DOWNLOAD;" + fileName + ";" + destinationPath;
        byte[] requestBytes = requestHeader.getBytes();
        DatagramPacket requestPacket = new DatagramPacket(requestBytes, requestBytes.length, serverAddress, SERVER_PORT);
        clientSocket.send(requestPacket);

        // Preparar para recibir el archivo
        FileOutputStream fileOutputStream = new FileOutputStream("./Client/" + fileName);
        byte[] receiveData = new byte[BUFFER_SIZE];
        boolean receiving = true;
        int expectedSequenceNumber = 0;

        while (receiving) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);

            // Leer el número de secuencia
            int sequenceNumber = byteArrayToInt(receivePacket.getData(), 0);
            if (sequenceNumber == expectedSequenceNumber) { // Es el paquete esperado
                fileOutputStream.write(receivePacket.getData(), 4, receivePacket.getLength() - 4);
                expectedSequenceNumber++;

                // Enviar ACK al servidor
                String ack = "ACK;" + sequenceNumber;
                byte[] ackData = ack.getBytes();
                DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, serverAddress, SERVER_PORT);
                clientSocket.send(ackPacket);
            }
            
            if (receivePacket.getLength() < BUFFER_SIZE) { // Si el tamaño del paquete es menor que el buffer, se considera que ha terminado la transmisión
                receiving = false;
            }
        }

        fileOutputStream.close();
    }

    private static int byteArrayToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               (bytes[offset + 3] & 0xFF);
    }
    
    private static void createFORdeleteFORrenameFile(DatagramSocket clientSocket, InetAddress serverAddress, String folderName, String destinationPath, String mode) throws IOException{
        if(mode.equals("C")){ //Create Folder
            if(destinationPath == "" || destinationPath == " "){
                destinationPath = "x";
            }
            //Header
            String header = "CREATEF;" + folderName + ";" + destinationPath;
            byte[] headerBytes = header.getBytes();
            
            //Sending the header
            DatagramPacket headerPacket = new DatagramPacket(headerBytes, headerBytes.length, serverAddress, SERVER_PORT);
            clientSocket.send(headerPacket);
        } else if(mode.equals("D")){
            if(destinationPath == "" || destinationPath == " "){
                destinationPath = "x";
            }
            //Header
            String header = "DELETEF;" + folderName + ";" + destinationPath;
            byte[] headerBytes = header.getBytes();
            
            //Sending the header
            DatagramPacket headerPacket = new DatagramPacket(headerBytes, headerBytes.length, serverAddress, SERVER_PORT);
            clientSocket.send(headerPacket);
        } else{
            if(filePath == "" || filePath == " "){
                filePath = "x";
            }
            //Header
            String header = "RENAME;" + fileName + ";" + newFileName + ";" + filePath;
            byte[] headerBytes = header.getBytes();
            
            //Sending the header
            DatagramPacket headerPacket = new DatagramPacket(headerBytes, headerBytes.length, serverAddress, SERVER_PORT);
            clientSocket.send(headerPacket);
        }
    }

    public static void clearConsole() {
        try {
            String operatingSystem = System.getProperty("os.name");

            if (operatingSystem.contains("Windows")) {
                //WINDOWs
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                //UNIX
                System.out.print("\033[H\033[2J");  
                System.out.flush();
            }
        } catch (Exception e) {
            System.out.println("Error al limpiar la consola: " + e.getMessage());
        }
    }
    

    
}

//For avoid problems with final variables
class IntWrapper {
    public int value;

    public IntWrapper(int initialValue) {
        this.value = initialValue;
    }
}

//LOADING SCREEN
class LoadingScreen implements Runnable {
    private boolean loading = true;

    @Override
    public void run() {
        String[] loadingSymbols = {"|", "/", "-", "\\"}; // Símbolos de carga animada
        String[] colors = {
            "\033[1;31m", // Rojo
            "\033[1;32m", // Verde
            "\033[1;33m", // Amarillo
            "\033[1;34m", // Azul
            "\033[1;35m", // Magenta
            "\033[1;36m"  // Cian
        };

        int i = 0;
        while (loading) {
            // Cambia el color y símbolo en cada ciclo
            String color = colors[i % colors.length];
            String symbol = loadingSymbols[i % loadingSymbols.length];
            
            // Imprime la animación de carga
            System.out.print("\r" + color + "Loading " + symbol + " " + getProgressBar(i % 20) + " \033[0m");

            i++;
            try {
                Thread.sleep(200); // Cambia el símbolo y color cada 200 ms
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Crear barra de progreso dinámica
    private String getProgressBar(int progress) {
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            if (i < progress) {
                bar.append("=");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");
        return bar.toString();
    }

    public void stopLoading() {
        this.loading = false;
    }
}