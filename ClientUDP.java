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
                    System.out.println("Name of file: ");
                    fileName = scanner.nextLine();
                    System.out.println("Where is file?");
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
        byte[] fileBuffer = new byte[BUFFER_SIZE];
        
        int bytesRead;
        while ((bytesRead = fileInputStream.read(fileBuffer)) != -1) {
            DatagramPacket filePacket = new DatagramPacket(fileBuffer, bytesRead, serverAddress, SERVER_PORT);
            clientSocket.send(filePacket);
        }
        
        fileInputStream.close();
        System.out.println("CLIENT: File sent successful!");
    }

    
}
