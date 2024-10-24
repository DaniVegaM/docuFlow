
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ServerUDP {

    private static final int SERVER_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;

    private static String action = "";
    private static String fileName = "";
    private static int fileLength = 0;
    private static String currentPath = "";
    private static Map<Integer, byte[]> receivedPackets = new HashMap<>(); //Store received packages

    public static void main(String[] args) throws IOException {
        DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT); //Socket
        byte[] receiveData = new byte[BUFFER_SIZE]; //Buffer to recieve data

        System.out.println("Servidor UDP esperando comandos...");

        while (true) {
            //Receive Header
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);

            //Extract header and save it on variables
            String command = new String(receivePacket.getData(), 0, receivePacket.getLength());

            String parts[] = command.split(";");
            action = parts[0];
            if(parts.length > 3){
                currentPath = parts[3];
            }

            InetAddress clientAddress = receivePacket.getAddress();
            int clientPort = receivePacket.getPort();

            // System.out.println("Server: Header received");

            switch (action) {
                case "UPLOAD":
                    fileName = parts[1];
                    fileLength = Integer.parseInt(parts[2]);
                    receiveFile(serverSocket, clientAddress, clientPort);
                break;
                case "DOWNLOAD":
                    // System.out.println("DOWNLOADING");
                    fileName = parts[1];
                    currentPath = parts[2];
                    sendFile(serverSocket, clientAddress, clientPort, fileName);
                break;
                case "CREATEF":
                System.out.println("PARTS1: " + parts[1]);
                System.out.println("PARTS2: " + parts[2]);
                    String folderPath = "./Server/" + ((Arrays.stream(parts).anyMatch(x -> x.equals("x")))?"":parts[2] + "/") + parts[1];
                    System.out.println("CREATE: " + ((Arrays.stream(parts).anyMatch(x -> x.equals("x")))?"":parts[2] + "/") + parts[1]);
                    boolean created = createFolder(folderPath);
                    if (created) {
                        System.out.println("Folder Created Succesfully!");
                    } else {
                        System.out.println("There's an error :(");
                }
                break;
                case "DELETEF":
                    File folderToDelete = new File("./Server/" + ((Arrays.stream(parts).anyMatch(x -> x.equals("x")))?"":parts[2] + "/") + parts[1]);
                    System.out.println("DELETE: " + ((Arrays.stream(parts).anyMatch(x -> x.equals("x")))?"":parts[2] + "/") + parts[1]);
                    boolean deleted = deleteFolder(folderToDelete);
                    if (deleted) {
                        System.out.println("Folder deleted succesfully");
                    } else {
                        System.out.println("There's an error :(");
                    }
                break;
                case "RENAME":
                    File oldFile = new File("./Server/" + ((Arrays.stream(parts).anyMatch(x -> x.equals("x")))?"":parts[3] + "/") + parts[1]);
                    File newFile = new File("./Server/" + ((Arrays.stream(parts).anyMatch(x -> x.equals("x")))?"":parts[3] + "/") + parts[2]);
                    
                    boolean renamed = oldFile.renameTo(newFile);
                    
                    if (renamed) {
                        System.out.println("File renamed succesfully!");
                    } else {
                        System.out.println("There's an error :(");
                    }
                break;
                default:
                    // System.out.println("I don't know what do you want to do ¿?");
                break;
            }
        }
    }

    public static void receiveFile(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort) throws IOException {
        byte[] receiveData = new byte[BUFFER_SIZE];
        FileOutputStream fileOutputStream = new FileOutputStream(((currentPath != "" || currentPath != " ")? currentPath + "/" : currentPath) + fileName);

        boolean receiving = true;
        long totalBytesReceived = 0;
        int expectedSequenceNumber = 0; //Expected Sequence Number
        int numOfPackagesReceived = 0;

        while (receiving) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);

            //Reading the sequence number
            int sequenceNumber = byteArrayToInt(receivePacket.getData(), 0);
            // System.out.println("SERVER: Package received: " + sequenceNumber);

            //Receiving package
            if (!receivedPackets.containsKey(sequenceNumber)) {
                receivedPackets.put(sequenceNumber, receivePacket.getData());
                // System.out.println("SERVER: Receiving another package" + sequenceNumber);
            }
            
            if(sequenceNumber == expectedSequenceNumber){ //It's the expected package
                // System.out.println("SERVER: It's the right package, saving package");
                fileOutputStream.write(receivePacket.getData(), 4, receivePacket.getLength() - 4);
                totalBytesReceived += receivePacket.getLength() - 4;

                //Send ACK to client
                if(numOfPackagesReceived == 5){
                    // System.out.println("SERVER: 5 packages (A window) received, sending ACK!");
                    String ack = "ACK;" + sequenceNumber;
                    byte[] ackData = ack.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);

                    //Reset variables
                    numOfPackagesReceived = 0;

                }

                expectedSequenceNumber++;
                numOfPackagesReceived++;

                if (totalBytesReceived >= fileLength) { //Server received the entire file
                    receiving = false;
                }
            } else if(sequenceNumber < expectedSequenceNumber){ //If we receive a duplicated package
                // System.out.println("SERVER: Package received duplicated, Im sending the expected sequence number as ACK");
                //Send ACK to client
                if(numOfPackagesReceived < 5){
                    // System.out.println("SERVER: Sending ACK of the package expected to continue recivieng packages until fill the window");
                    String ack = "ACKR;" + expectedSequenceNumber;
                    byte[] ackData = ack.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);
                }
            } else{
                // System.out.println("SERVER: Package out of order :( \n Im sending the expected sequence number as ACK");
                if(numOfPackagesReceived < 5){
                    // System.out.println("SERVER: Sending ACK of the package expected to continue recivieng packages until fill the window");
                    String ack = "ACKR;" + expectedSequenceNumber;
                    byte[] ackData = ack.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);
                }
            }
        }

        fileOutputStream.close();
        // System.out.println("SERVER: File recieved and saved!");
    }

    //Convert first 4 bytes of package received into an int sequence number
    private static int byteArrayToInt(byte[] arr, int offset) {
        return ((arr[offset] & 0xFF) << 24) | ((arr[offset + 1] & 0xFF) << 16) |
               ((arr[offset + 2] & 0xFF) << 8) | (arr[offset + 3] & 0xFF);
    }

    public static void sendFile(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, String fileName) throws IOException {
        File file = new File(currentPath + fileName);
        if (!file.exists()) {
            System.out.println("SERVER ERROR: The file doesn't exist");
            return;
        }

        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] fileBuffer = new byte[BUFFER_SIZE - 4];
        byte[] sendData = new byte[BUFFER_SIZE];
        int bytesRead;
        int sequenceNumber = 0;

        while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
            addSequenceNumberToPacket(sendData, sequenceNumber);
            System.arraycopy(fileBuffer, 0, sendData, 4, bytesRead);

            // Enviar paquete
            DatagramPacket filePacket = new DatagramPacket(sendData, bytesRead + 4, clientAddress, clientPort);
            serverSocket.send(filePacket);

            // Esperar ACK del cliente
            DatagramPacket ackPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
            serverSocket.receive(ackPacket);
            String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
            String[] ackParts = ack.split(";");

            if (ackParts[0].equals("ACK")) {
                int ackSequenceNumber = Integer.parseInt(ackParts[1]);
                sequenceNumber++; // Solo incrementar si se recibió el ACK correctamente
            }
        }

        fileInputStream.close();
    }

    private static void addSequenceNumberToPacket(byte[] packet, int sequenceNumber) {
        packet[0] = (byte) (sequenceNumber >> 24);
        packet[1] = (byte) (sequenceNumber >> 16);
        packet[2] = (byte) (sequenceNumber >> 8);
        packet[3] = (byte) sequenceNumber;
    }

    public static boolean deleteFolder(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file); // Llamada recursiva
                }
            }
        }
        return folder.delete(); // Elimina el archivo o carpeta
    }

    public static boolean createFolder(String path) {
        File newFolder = new File(path);
        return newFolder.mkdir(); // Devuelve true si se crea la carpeta
    }
}
