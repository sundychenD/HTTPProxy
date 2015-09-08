import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Scanner;

/**
 * Created by chendi on 3/9/15.
 */

/*
* WebProxy is a simple HTTPMSG proxy server.
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
            while (true) {

                // Receive client request, establish new socket to remote server
                HTTPMSG clientReqMSG = formHTTPMSG(this.socket);
                System.out.println("====== Client request: \n" + clientReqMSG.getHeader().getHeaderStr());
                Socket serverSocket = new Socket(clientReqMSG.getHeader().getHost(), 80);

                // Send client request to remote server
                System.out.println("====== Forwarding request to server");
                forwardRequest(clientReqMSG, serverSocket);

                // Transferring remote server responses back to client
                BufferedInputStream forwardInputStream = new BufferedInputStream(serverSocket.getInputStream());
                BufferedOutputStream serverResponse = new BufferedOutputStream(this.socket.getOutputStream());
                returnResponse(forwardInputStream, serverResponse);

                //serverSocket.close();
                //this.socket.close();
                System.out.println("!== End of communication");

                /*
                if (cached(clientRequest)) {
                    if (modified(clientRequest)) {
                        // Original page updated, retrieve new page
                        cacheResponse();
                        forwardResponse();
                    } else {
                        // Original page not updated, return local cached response
                        forwardResponse();
                    }
                } else {
                    cacheResponse();
                    forwardReponse();
                }
                */
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println(e.getStackTrace());
        }
    }

    /*
    * Forward clinet request to remote server;
    * */
    private void forwardRequest(HTTPMSG clientReqMSG, Socket serverSocket) throws IOException {
        Header clientReqHeader = clientReqMSG.getHeader();
        Scanner bodyScanner = clientReqMSG.getBodyScanner();
        BufferedOutputStream serverOutput = new BufferedOutputStream(serverSocket.getOutputStream());

        // Send header in byte array
        byte[] headerByteArray = clientReqHeader.getHeaderByte();
        serverOutput.write(headerByteArray, 0, headerByteArray.length);

        // Send message body if request is POST type
        if (clientReqHeader.isPost()) {
            while (bodyScanner.hasNextByte()) {
                serverOutput.write((int) bodyScanner.nextByte());
            }
        }
        serverOutput.flush();
    }

    private HTTPMSG formHTTPMSG(Socket socket) throws IOException {
        BufferedInputStream buf_input = new BufferedInputStream(socket.getInputStream());
        Scanner sc = new Scanner(buf_input);
        Header clientRequestHeader = new Header(readHeader(sc));
        return new HTTPMSG(clientRequestHeader, sc);
    }

    /*
    * Read http header into String
    * */
    private String readHeader(Scanner input) {
        String header = "";
        String next;
        while(!(next = input.nextLine()).trim().equals("")) {
            header += next + "\n";
        }
        header += "\n";

        return header;
    }

    /*
    * Return the remote server response to the client
    * */
    private void returnResponse(BufferedInputStream serverResponse, BufferedOutputStream clientOutput) throws IOException {
        int numResponseBytes;
        byte[] buffer = new byte[1024];

        while ((numResponseBytes = serverResponse.read(buffer)) != -1) {
            System.out.println("Receiving from server" + numResponseBytes + " bytes");
            clientOutput.write(buffer, 0, numResponseBytes);
        }
        clientOutput.flush();
    }
}

/*
* Header Class
* */
class Header {
    private String rawHeader;
    private LinkedList<String> headerList;

    public Header(String rawHeader) {
        this.rawHeader = rawHeader;
        this.headerList = headerToList(rawHeader);
    }

    public String getHeaderStr() {
        return this.rawHeader;
    }

    /* Header in byte array */
    public byte[] getHeaderByte() {
        return this.rawHeader.getBytes(Charset.forName("UTF-8"));
    }

    public boolean isGet() {
        return this.headerList.getFirst().toUpperCase().startsWith("GET");
    }

    public boolean isHead() {
        return this.headerList.getFirst().toUpperCase().startsWith("HOST");
    }

    public boolean isPost() {
        return this.headerList.getFirst().toUpperCase().startsWith("POST");
    }

    public String getHost() {
        return getField("Host:");
    }

    public String getContentType() {
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


/*
* Client Request
*
* An abstraction of a client request
* */
class HTTPMSG {

    private Header header;
    private Scanner bodyScanner;

    public HTTPMSG(Header header, Scanner sc) {
        this.header = header;
        this.bodyScanner = sc;
    }

    public Header getHeader() {
        return this.header;
    }

    public Scanner getBodyScanner() {
        return this.bodyScanner;
    }
}