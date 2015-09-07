import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Scanner;

/**
 * Created by chendi on 3/9/15.
 */

/*
* WebProxy is a simple HTTP proxy server.
*
* To run it, give a port number as input and it
* would listen on that port for incoming http requests.
* */
public class WebProxy {

    private static int port;

    public static void main(String[] args) {
        WebProxy.port = Integer.parseInt(args[0]);
        ProxyServer p_server = new ProxyServer(WebProxy.port);
        p_server.start();
    }
}


/*
* A multi-threaded proxy server.
*
* Each time when a new http request come in, a new thread
* would be created to handle the request.
* */
class ProxyServer {
    private int listeningPort;

    public ProxyServer(int port) {
        this.listeningPort = port;
    }

    public void start() {

        try {
            ServerSocket serverSocket = new ServerSocket(this.listeningPort);
            Socket clientSocket;

            // Loop for creating new handling thread
            while (true) {
                clientSocket = serverSocket.accept();
                Runnable echoRunnable = new ProxyThreadRunnable(clientSocket);
                Thread echoThread = new Thread(echoRunnable);
                echoThread.start();
            }
        } catch (IOException io_e) {
            System.out.println("Cannot start proxy server");
            io_e.printStackTrace();
        }
    }
}


/*
* Proxy thread running module
*
* A thread runnable that handles each http request.
* */
class ProxyThreadRunnable implements Runnable {

    private Socket socket;

    public ProxyThreadRunnable(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        String input;
        try {

            byte[] requestBuffer = new byte[10240];
            while (true) {

                // Receive client request
                BufferedInputStream buf_input = new BufferedInputStream(this.socket.getInputStream());
                HTTP clientRequest = getClientRequest(buf_input);

                /*
                int requestLength = buf_input.read(requestBuffer);
                String browserRequest = new String(requestBuffer, 0, requestLength);
                System.out.println("== Client request: \n" + browserRequest);

                // Get remote server address
                String host = getHost(browserRequest);
                System.out.println("== Client wants to connect to host: " + host);

                // Establish new socket to remote server
                Socket hostSocket = new Socket(host, 80);
                forwardRequest(requestBuffer, requestLength, hostSocket);
                System.out.println("== Forwarding request to server");

                // Transferring remote server responses back to client
                BufferedInputStream forwardInputStream = new BufferedInputStream(hostSocket.getInputStream());
                BufferedOutputStream clientOutputStream = new BufferedOutputStream(this.socket.getOutputStream());
                returnResponse(forwardInputStream, clientOutputStream);
                System.out.println("== Return server response");

                hostSocket.close();
                */

                this.socket.close();
                System.out.println("!== End of communication");
            }
        } catch (IOException e) {
            System.out.println(e.getStackTrace());
        }
    }

    /*
    * Read client request content into a byte buffer
    * */
    private HTTP getClientRequest(BufferedInputStream input) {

        HTTP HTTP;
        String header = readHeader(input);
        ByteBuffer content_byte = readContent(input);

        return new HTTP(header, content_byte);
    }

    /*
    * Read http header into String
    * */
    private String readHeader(BufferedInputStream input) {
        String header = "";
        Scanner sc = new Scanner(input);

        String next;
        while(!(next = sc.nextLine()).equals("")) {
            header += next + "\n";
        }

        return header;
    }

    /*
    * Read the content of the HTTP request into a byte buffer
    * */
    private ByteBuffer readContent(BufferedInputStream input) {
        ByteBuffer content_byte = null;
        byte[] input_buf = new byte[1024];

        try {
            while (input.available() > 0) {
                input.read(input_buf);
                content_byte.put(input_buf);
            }
        } catch (IOException e) {
            System.out.println("Error read client request stream");
            e.printStackTrace();
        }

        return content_byte;
    }

    /*
    * Return the remote server response to the client
    * */
    private void returnResponse(BufferedInputStream serverResponse, BufferedOutputStream clientSocket) throws IOException {
        int numResponseBytes;
        byte[] buffer = new byte[1024];

        while ((numResponseBytes = serverResponse.read(buffer)) != -1) {
            System.out.println("Receiving from server" + numResponseBytes + " bytes");
            clientSocket.write(buffer, 0, numResponseBytes);
        }
        clientSocket.flush();
    }

    /*
    * Forward client request to remote server
    * */
    private void forwardRequest(byte[] requestBuffer, int buffer, Socket hostSocket) throws IOException {
        BufferedOutputStream forwardSocket = new BufferedOutputStream(hostSocket.getOutputStream());
        forwardSocket.write(requestBuffer, 0, buffer);
        forwardSocket.flush();
    }

}


/*
* Client Request
*
* An abstraction of a client request
* */
class HTTP {
    private String header;
    private LinkedList<String> headerList;
    private ByteBuffer byte_content;

    public HTTP(String header, ByteBuffer input) {
        this.header = header;
        this.headerList = headerToList(header);
        this.byte_content = input;
    }

    public String getHeader() {
        return this.header;
    }

    public ByteBuffer getContent() {
        return this.byte_content;
    }

    public String getHost() {
        return retrieveHost();
    }

    public String getContentType() {
        return retrieveContentType();
    }

    private String retrieveHost() {
        return getField("Host:");
    }

    private String retrieveContentType() {
        return getField("Content-Type:");
    }

    /*
    * Retrieve a specific field from headerList
    * */
    private String getField(String fieldName) {
        String field = null;

        String cur_field;
        for (int i = 0; i < this.headerList.size(); i++) {
            cur_field = this.headerList.get(i);
            if (cur_field.toUpperCase().startsWith(fieldName.toUpperCase())) {
                field = (cur_field.split(":")[1]).trim();
            }
        }
        return field;
    }

    /*
    * Transform header String into LinkedList
    * */
    private LinkedList<String> headerToList(String header) {
        LinkedList <String> headerList = new LinkedList<String>();
        String[] headerArr = header.split("\n");

        for (int i = 0; i < headerArr.length; i++) {
            headerList.add(headerArr[i]);
        }
        return headerList;
    }
}