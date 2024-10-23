
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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

            System.out.println("Server: Header received");

            switch (action) {
                case "UPLOAD":
                    fileName = parts[1];
                    fileLength = Integer.parseInt(parts[2]);
                    receiveFile(serverSocket, clientAddress, clientPort);
                break;
                case "DOWNLOAD":
                   
                break;
                case "CREATEF":
                    
                break;
                case "DELETEF":
                    
                break;
                case "RENAMEF":
                    
                break;
                default:
                    // System.out.println("I don't know what do you want to do ¿?");
                break;
            }
        }
    }

    public static void receiveFile(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort) throws IOException {
        byte[] receiveData = new byte[BUFFER_SIZE];
        FileOutputStream fileOutputStream = new FileOutputStream(currentPath + fileName);

        boolean receiving = true;
        long totalBytesReceived = 0;
        int expectedSequenceNumber = 0; //Expected Sequence Number
        int numOfPackagesReceived = 0;

        while (receiving) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);

            //Reading the sequence number
            int sequenceNumber = byteArrayToInt(receivePacket.getData(), 0);
            System.out.println("SERVER: Package received: " + sequenceNumber);

            //Receiving package
            if (!receivedPackets.containsKey(sequenceNumber)) {
                receivedPackets.put(sequenceNumber, receivePacket.getData());
                System.out.println("SERVER: Receiving another package" + sequenceNumber);
            }
            
            if(sequenceNumber == expectedSequenceNumber){ //It's the expected package
                System.out.println("SERVER: It's the right package, saving package");
                fileOutputStream.write(receivePacket.getData(), 4, receivePacket.getLength() - 4);
                totalBytesReceived += receivePacket.getLength() - 4;

                //Send ACK to client
                if(numOfPackagesReceived == 5){
                    System.out.println("SERVER: 5 packages (A windows) received, sending ACK!");
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
                System.out.println("SERVER: Package received duplicated, Im sending the expected sequence number as ACK");
                //Send ACK to client
                if(numOfPackagesReceived < 5){
                    System.out.println("SERVER: Sending ACK of the package expected to continue recivieng packages until fill the window");
                    String ack = "ACKR;" + expectedSequenceNumber;
                    byte[] ackData = ack.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);
                }
            } else{
                System.out.println("SERVER: Package out of order :( \n Im sending the expected sequence number as ACK");
                if(numOfPackagesReceived < 5){
                    System.out.println("SERVER: Sending ACK of the package expected to continue recivieng packages until fill the window");
                    String ack = "ACKR;" + expectedSequenceNumber;
                    byte[] ackData = ack.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);
                }
            }
        }

        fileOutputStream.close();
        System.out.println("SERVER: File recieved and saved!");
    }

    //Convert first 4 bytes of package received into an int sequence number
    private static int byteArrayToInt(byte[] arr, int offset) {
        return ((arr[offset] & 0xFF) << 24) | ((arr[offset + 1] & 0xFF) << 16) |
               ((arr[offset + 2] & 0xFF) << 8) | (arr[offset + 3] & 0xFF);
    }
}
