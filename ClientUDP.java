import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class ClientUDP {
    private static final int SERVER_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;
    private static final int WINDOW_SIZE = 5; 
    private static final String SERVER_IP = "localhost";
    private static String fileName = "";
    private static String destinationPath = "./Server/";

    public static void main(String[] args) throws IOException {
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress serverAddress = InetAddress.getByName(SERVER_IP);
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("¿What do you want to do?");
            System.out.print("1. UPLOAD\n2. DOWNLOAD\n3. CREATE FOLDER\n4. DELETE FOLDER\n5. RENAME FILE");
            String command = scanner.nextLine();

            switch(command){
                case "1": //UPLOAD
                    System.out.println("Name of File: ");
                    fileName = scanner.nextLine();
                    System.out.println("File Location: ");
                    destinationPath+= scanner.nextLine();
                    sendFileWithMetadata(clientSocket, serverAddress, fileName, destinationPath);
                break;
                case "2": //DOWNLOAD

                break;
                case "3":

                break;
                case "4":

                break;
                case "5":

                break;
            }
            //Reset variables
            destinationPath = "./Server/";
        }
    }

    //UPLOAD
    public static void sendFileWithMetadata(DatagramSocket clientSocket, InetAddress serverAddress, String fileName, String destinationPath) throws IOException {
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
        
        //Sending file
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] fileBuffer = new byte[BUFFER_SIZE - 4];
        byte[] sendData = new byte[BUFFER_SIZE];
        
        int bytesRead;
        int sequenceNumber = 0;
        int windowStart = 0; //Window's index
        int windowEnd = WINDOW_SIZE - 1;  // Fin de la ventana
        int lastAckReceived = -1;

        while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
            boolean ackReceived = false;

            while(!ackReceived){
                //Add sequence number to the package
                addSequenceNumberToPacket(sendData, sequenceNumber);

                //Copy file data to the package (after the 4 bytes of sequence-number)
                System.arraycopy(fileBuffer, 0, sendData, 4, bytesRead);

                //Sending package
                DatagramPacket filePacket = new DatagramPacket(sendData, bytesRead + 4, serverAddress, SERVER_PORT);
                clientSocket.send(filePacket);

                try { //Waiting for Server's ACK
                    byte[] ackBuffer = new byte[BUFFER_SIZE];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    clientSocket.setSoTimeout(1000); //Wait 1000 for ACK
                    clientSocket.receive(ackPacket);

                    //Read ACK
                    String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    String[] ackParts = ack.split(";");
                    int ackSequenceNumber = Integer.parseInt(ackParts[1]);

                    if (ackSequenceNumber == sequenceNumber) {
                        ackReceived = true; //We received the right ACK
                        sequenceNumber++;
                        windowStart++; //Slide Window
                    }
                } catch (IOException e) {
                    //If we don't receive the right ACK there was an error, so we need to resend the package
                    System.out.println("No se recibió ACK para el paquete con secuencia: " + sequenceNumber + ". Retransmitiendo...");
                }
            }
        }
        
        fileInputStream.close();
        System.out.println("CLIENT: File sent successful!");
    }

    private static void addSequenceNumberToPacket(byte[] packet, int sequenceNumber) {
        packet[0] = (byte) (sequenceNumber >> 24);
        packet[1] = (byte) (sequenceNumber >> 16);
        packet[2] = (byte) (sequenceNumber >> 8);
        packet[3] = (byte) sequenceNumber;
    }
    
}
