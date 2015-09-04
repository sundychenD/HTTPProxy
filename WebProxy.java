import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

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
                this.socket.close();
                System.out.println("!== End of communication");
            }
        } catch (IOException e) {
            System.out.println(e.getStackTrace());
        }
    }

    private void returnResponse(BufferedInputStream forwardInputStream, BufferedOutputStream clientOutputStream) throws IOException {
        int numResponseBytes;
        byte[] buffer = new byte[1024];
        while ((numResponseBytes = forwardInputStream.read(buffer)) != -1) {
            System.out.println("Receiving from server" + numResponseBytes + " bytes");
            clientOutputStream.write(buffer, 0, numResponseBytes);
        }
        clientOutputStream.flush();
    }

    private void forwardRequest(byte[] requestBuffer, int buffer, Socket hostSocket) throws IOException {
        BufferedOutputStream forwardSocket = new BufferedOutputStream(hostSocket.getOutputStream());
        forwardSocket.write(requestBuffer, 0, buffer);
        forwardSocket.flush();
    }

    private String getHost(String browserRequest) {
        int start = browserRequest.indexOf("Host: ") + 6;
        int end = browserRequest.indexOf('\n', start);

        return browserRequest.substring(start, end - 1);
    }
}