import java.io.File;
import java.io.FileInputStream;
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
    private static String destinationPath = "./Server/";

    public static void main(String[] args) throws IOException, InterruptedException {
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
                    Thread.sleep(10); //PRETTY IMPORTANT!!!!!!!
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
                    System.out.println("CLIENT: ACK received for all packages in a window");
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
                        System.out.println("CLIENT: I received an strange Datagram :/");
                    }
                } catch (IOException e) {
                    // Resend if timeout occurs
                    if (attempts > 0) {
                        System.out.println("CLIENT: Timeout, resending unacknowledged packets");
                        for (int packetToResend : sentPackets) {
                            addSequenceNumberToPacket(sendData, packetToResend);
                            DatagramPacket resendPacket = new DatagramPacket(sendData, BUFFER_SIZE, serverAddress, SERVER_PORT);
                            clientSocket.send(resendPacket);
                        }
                        attempts--;
                    } else {
                        System.out.println("CLIENT: ERROR, maximum number of resend attempts reached");
                        return;
                    }
                }
            }
        }
        
        fileInputStream.close();
        System.out.println("CLIENT: File sent successfully!");
    }    

    private static void addSequenceNumberToPacket(byte[] packet, int sequenceNumber) {
        packet[0] = (byte) (sequenceNumber >> 24);
        packet[1] = (byte) (sequenceNumber >> 16);
        packet[2] = (byte) (sequenceNumber >> 8);
        packet[3] = (byte) sequenceNumber;
    }
    

    
}

//For avoid problems with final variables
class IntWrapper {
    public int value;

    public IntWrapper(int initialValue) {
        this.value = initialValue;
    }
}
