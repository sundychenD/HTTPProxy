import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

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

    public HashMap <String, String> cache;

    public ProxyServer(int port) {
        this.listeningPort = port;
    }

    public void start() {

        try {
            this.cache = new HashMap<String, String>();
            ServerSocket serverSocket = new ServerSocket(this.listeningPort);
            Socket clientSocket;

            // Loop for creating new handling thread
            while (true) {
                clientSocket = serverSocket.accept();
                Runnable echoRunnable = new ProxyThreadRunnable(clientSocket, this.cache);
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
    private HashMap<String, String> cache;
    private OutputStream serverResponse;

    public ProxyThreadRunnable(Socket socket, HashMap<String, String> cache) throws IOException {
        this.socket = socket;
        this.cache = cache;
        this.serverResponse = socket.getOutputStream();
    }

    public void run() {
        String input;
        boolean continueProcess = true;
        try {
            while (continueProcess) {

                // Client Stream
                InputStream clientRequestStream = this.socket.getInputStream();
                OutputStream clientOutputStream = this.serverResponse;
                // Server Stream
                Socket serverSocket = null;
                OutputStream serverOutputStream = null;
                InputStream serverInputStream = null;



                // ========== Forward Client Request To Server
                int numBytesRead = 0;
                int totalContentBytesRead = 0;
                int contentLength = 0;
                int headerEnd = 0;
                byte[] buffer = new byte[8190];
                Header clientRequestHeader;

                boolean hasNotReadHeader = true;
                boolean continueReadFromClient = true;
                while (continueReadFromClient) {

                    numBytesRead = clientRequestStream.read(buffer);
                    totalContentBytesRead += numBytesRead;

                    if (numBytesRead == 0) {
                        break;
                    }

                    if (hasNotReadHeader) {
                        headerEnd = getHeaderEnd(buffer);
                        clientRequestHeader = new Header(getHeader(buffer, headerEnd));
                        contentLength = clientRequestHeader.getContentLength();
                        totalContentBytesRead = numBytesRead - headerEnd;

                        // Establish Socket Connection with remote Server
                        serverSocket = new Socket(clientRequestHeader.getHost(), clientRequestHeader.getPort());
                        serverOutputStream = serverSocket.getOutputStream();
                        serverInputStream = serverSocket.getInputStream();

                        hasNotReadHeader = false;
                    }

                    if (totalContentBytesRead > contentLength) {
                        continueReadFromClient = false;
                    } else {
                        continueReadFromClient = true;
                    }

                    // Forward Request to remote Server
                    serverOutputStream.write(buffer, 0, numBytesRead);
                    serverOutputStream.flush();
                }





                // ========== Return Server Response to Client
                int numBytesRead_Server = 0;
                int totalContentBytesRead_Server = 0;
                int contentLength_Server = 0;
                int headerEnd_Server = 0;
                byte[] buffer_Server = new byte[49152];
                Header serverResponseHeader;

                boolean hasNotReadHeader_Server = true;
                boolean continueReadFromServer = true;
                while (continueReadFromServer) {
                    numBytesRead_Server = serverInputStream.read(buffer_Server);
                    totalContentBytesRead_Server += numBytesRead_Server;

                    if (hasNotReadHeader_Server) {
                        headerEnd_Server = getHeaderEnd(buffer_Server);
                        serverResponseHeader = new Header(getHeader(buffer_Server, headerEnd_Server));
                        contentLength_Server = serverResponseHeader.getContentLength();
                        totalContentBytesRead_Server = numBytesRead_Server - headerEnd_Server;

                        hasNotReadHeader_Server = false;
                    }

                    if (totalContentBytesRead_Server > contentLength_Server) {
                        continueReadFromServer = false;
                    } else {
                        continueReadFromServer = true;
                    }

                    // Forward Request to remote Server
                    clientOutputStream.write(buffer_Server, 0, numBytesRead_Server);
                    clientOutputStream.flush();
                }

                /*

                // Receive client request, establish new socket to remote server
                HTTPMSG clientReqMSG = formHTTPMSG(this.socket);
                Header requestHeader = clientReqMSG.getHeader();
                String requestAddr = requestHeader.getRequest();
                System.out.println("====== Client request: \n" + clientReqMSG.getHeader().getHeaderStr());

                // Connect to remote server
                Socket serverSocket = new Socket(clientReqMSG.getHeader().getHost(), clientReqMSG.getHeader().getPort());
                InputStream serverInputStream = serverSocket.getInputStream();

                // Check cache for GET requests
                if (requestHeader.isGet()) {
                    if (isCached(requestAddr)) {
                        System.out.println("====== Hit cache " + requestAddr);
                        returnCache(requestAddr, this.serverResponse);
                    } else {
                        // Send client request to remote server
                        System.out.println("====== Forwarding request to server");
                        forwardRequest(clientReqMSG, serverSocket);

                        // Cache and return response
                        System.out.println("====== Cache response " + requestAddr);
                        cacheAndReturnResponse(requestAddr, serverInputStream, this.serverResponse);
                    }
                } else {
                    // Send client request to remote server
                    System.out.println("====== Forwarding request to server");
                    forwardRequest(clientReqMSG, serverSocket);

                    // Transferring remote server responses back to client
                    returnResponse(serverInputStream, this.serverResponse);
                }
                */

                serverSocket.close();
                this.socket.close();
                continueProcess = false;
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
            e.printStackTrace();
        }
    }

    private int getHeaderEnd(byte[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            if (i <= buffer.length - 4) {
                if (buffer[i] == 13 && buffer[i + 1] == 10 && buffer[i + 2] == 13 && buffer[i + 3] == 10) {
                    return i + 3;
                }
            }
        }
        System.out.println("===== Cannot read header end, header too long");
        String header = new String(buffer);
        System.out.println("===== Header Received: \n" + header);
        return buffer.length - 1;
    }

    private String getHeader(byte[] buffer, int headerEnd) {
        byte[] headerByteArray = Arrays.copyOfRange(buffer, 0, headerEnd);
        return new String(headerByteArray);
    }

    /*
    * Check if an object is cached on the proxy server
    * */
    private boolean isCached(String requestAddr) {
        return this.cache.containsKey(requestAddr);
    }

    /*
    * Read cached file from disc and return to client
    * */
    private void returnCache(String reqAddr, OutputStream responseOutput) throws FileNotFoundException {
        String fileAddr = this.cache.get(reqAddr);
        InputStream cacheObj = new FileInputStream(fileAddr);

        try {
            int numResponseBytes;
            byte[] buffer = new byte[1024];

            while ((numResponseBytes = cacheObj.read(buffer)) != -1) {
                System.out.println("Return from cache " + numResponseBytes + " bytes");
                responseOutput.write(buffer, 0, numResponseBytes);
            }
            responseOutput.flush();
            responseOutput.close();
        } catch (IOException e) {
            System.out.println("Cannot read from file");
            e.printStackTrace();
        }
    }

    /*
    * Cache response to local file and at the same time return to client
    * */
    private void cacheAndReturnResponse(String reqAddr,
                                       InputStream forwardInputStream,
                                       OutputStream serverResponse) throws IOException {
        File localFile = createCacheFile(reqAddr);
        OutputStream localFileStream = new FileOutputStream(localFile);

        int numResponseBytes;
        byte[] buffer = new byte[1024];

        while ((numResponseBytes = forwardInputStream.read(buffer)) != -1) {
            System.out.println("Receiving and Cache from server " + numResponseBytes + " bytes");
            localFileStream.write(buffer, 0, numResponseBytes);
            serverResponse.write(buffer, 0, numResponseBytes);
        }
        serverResponse.flush();
        localFileStream.flush();

        // Close local file
        localFileStream.close();
        serverResponse.close();
    }

    /*
    * Create local file and add to cache list
    * */
    private File createCacheFile(String cacheRequAddr) throws IOException {

        // Create new directory for storing caches
        File cacheDir = new File("ProxyCachedFile");
        if (!cacheDir.exists()) {
            System.out.println("====== Create Cache Folder: ProxyCachedFile");
            cacheDir.mkdir();
        }

        String cacheFileAddr = "ProxyCachedFile/" + cacheRequAddr.hashCode();

        // Put filePath into cache hashmap and create new file
        this.cache.put(cacheRequAddr, cacheFileAddr);
        File cacheFile = new File(cacheFileAddr);

        System.out.println("====== Create Cache File Address at: " + cacheFileAddr);
        cacheFile.createNewFile();
        return cacheFile;
    }

    /*
    * Forward clinet request to remote server;
    * */
    private void forwardRequest(HTTPMSG clientReqMSG, Socket serverSocket) throws IOException {
        Header clientReqHeader = clientReqMSG.getHeader();
        InputStreamReader clientInputReader = clientReqMSG.getbodyReader();
        OutputStream serverOutput = serverSocket.getOutputStream();

        // Send header in byte array
        byte[] headerByteArray = clientReqHeader.getHeaderByte();
        serverOutput.write(headerByteArray, 0, headerByteArray.length);

        // Send message body if request is POST type

        String postBody;
        if (clientReqHeader.isPost()) {
            System.out.println("===== Encounter Post Request");
            while (!(postBody = this.readLine(clientInputReader)).trim().isEmpty()) {
                System.out.println("===== Send Post Request:\n " + postBody);
                serverOutput.write(postBody.getBytes());
            }
        }
        serverOutput.flush();
    }

    /*
    * Form a HTTPMSG class
    * */
    private HTTPMSG formHTTPMSG(Socket socket) throws IOException {
        InputStream buf_input = socket.getInputStream();
        InputStreamReader inputReader = new InputStreamReader(buf_input, StandardCharsets.US_ASCII);
        Header clientRequestHeader = new Header(readHeader(inputReader));
        return new HTTPMSG(clientRequestHeader, inputReader);
    }

    /*
    * Read buffered input stream in lines
    * */
    private String readLine(InputStreamReader r) throws IOException {
        // HTTP carries both textual and binary elements.
        // Not using BufferedReader.readLine() so it does
        // not "steal" bytes from BufferedInputStream...

        // HTTP itself only allows 7bit ASCII characters
        // in headers, but some header values may be
        // further encoded using RFC 2231 or 5987 to
        // carry Unicode characters ...

        String[] result = new String[2];
        StringBuilder sb = new StringBuilder();

        result[1] = "continue";
        char[] c = new char[1];
        while (r.ready() && r.read(c) >= 0) {
            if (c[0] == '\n') break;
            if (c[0] == '\r') {
                r.read(c);
                if ((c[0] < 0) || (c[0] == '\n')) break;
                sb.append('\r');
            }
            sb.append(c);
        }
        sb.append('\n');
        return sb.toString();
    }

    /*
    * Read http header into String
    * */
    private String readHeader(InputStreamReader input) throws IOException {
        String header = "";
        String next;
        while(!(next = this.readLine(input)).trim().isEmpty()) {
            header += next;
        }
        header += "\r\n";

        return header;
    }

    /*
    * Return the remote server response to the client
    * */
    private void returnResponse(InputStream serverResponse, OutputStream clientOutput) throws IOException {
        int numResponseBytes;
        byte[] buffer = new byte[1024];

        while (((numResponseBytes = serverResponse.read(buffer)) != -1)) {
            System.out.println("Receiving from server " + numResponseBytes + " bytes");
            clientOutput.write(buffer, 0, numResponseBytes);

            if ((serverResponse.available() == 0)) {
                break;
            }
        }
        clientOutput.flush();
        clientOutput.close();
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
        return this.rawHeader.getBytes(StandardCharsets.US_ASCII);
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
        String hostField = getField("Host:");
        if (hostField.contains(":")) {
            return hostField.split(":")[0];
        } else {
            return hostField;
        }
    }

    public int getPort() {
        String hostField = getField("Host:");
        if (hostField.contains(":")) {
            return Integer.parseInt(hostField.split(":")[1]);
        } else {
            return 80;
        }
    }

    public String getRequest() {
        try {
            return this.headerList.getFirst().split(" ")[1];
        }catch (IndexOutOfBoundsException e) {
            System.out.println(e.getMessage());
            System.out.println("===== Index out of bound, raw header is: <" + this.headerList.getFirst()+ " >");
            e.printStackTrace();
            return "LOCALHOST";
        }
    }

    public String getContentType() {
        return getField("Content-Type:");
    }

    public int getContentLength() {
        for (int i = 0; i < this.headerList.size(); i++) {
            if (this.headerList.get(i).contains("Content-Length:")) {
                return Integer.parseInt(getField("Content-Length:"));
            }
        }
        return 0;
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
    private InputStreamReader bodyReader;

    public HTTPMSG(Header header, InputStreamReader inputReader) {
        this.header = header;
        this.bodyReader = inputReader;
    }

    public Header getHeader() {
        return this.header;
    }

    public InputStreamReader getbodyReader() {
        return this.bodyReader;
    }
}