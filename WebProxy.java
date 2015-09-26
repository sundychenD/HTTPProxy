import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
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
    private HashMap<String, String> cacheModifedTime;

    public ProxyServer(int port) {
        this.listeningPort = port;
    }

    public void start() {

        try {
            this.cache = new HashMap<String, String>();
            this.cacheModifedTime = new HashMap<String, String>();
            ServerSocket serverSocket = new ServerSocket(this.listeningPort);
            Socket clientSocket;

            // Loop for creating new handling thread
            while (true) {
                clientSocket = serverSocket.accept();
                Runnable echoRunnable = new ProxyThreadRunnable(clientSocket, this.cache, this.cacheModifedTime);
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
    private HashMap<String, String> cacheModifiedTime;
    private OutputStream serverResponse;
    private InputStream clientInput;
    private File cacheDir;

    public ProxyThreadRunnable(Socket socket, HashMap<String, String> cache, HashMap<String, String> cacheModifiedTime) throws IOException {
        this.socket = socket;
        this.cache = cache;
        this.cacheModifiedTime = cacheModifiedTime;
        this.cacheDir = new File("ProxyCachedFile");
        this.serverResponse = socket.getOutputStream();
        this.clientInput = this.socket.getInputStream();

        createCacheDir();
    }

    public void run() {
        String input;
        boolean continueProcess = true;
        try {
            while (continueProcess) {

                // Client Stream
                InputStream clientRequestStream = this.clientInput;
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
                Header clientRequestHeader = null;

                boolean hasNotReadHeader = true;
                boolean continueReadFromClient = true;

                boolean continueReadFromServer = true;

                while (continueReadFromClient) {
                    numBytesRead = clientRequestStream.read(buffer);

                    if (numBytesRead == -1) {
                        continueReadFromServer = false;
                        break;
                    }

                    totalContentBytesRead += numBytesRead;

                    if (hasNotReadHeader) {
                        headerEnd = getHeaderEnd(buffer);
                        clientRequestHeader = new Header(getHeader(buffer, headerEnd));
                        contentLength = clientRequestHeader.getContentLength();
                        totalContentBytesRead = numBytesRead - headerEnd;

                        // Establish Socket Connection with remote Server
                        //serverSocket = new Socket("www.example.com", 443);
                        serverSocket = new Socket(clientRequestHeader.getHost(), clientRequestHeader.getPort());
                        serverOutputStream = serverSocket.getOutputStream();
                        serverInputStream = serverSocket.getInputStream();

                        // Return Local Cached File
                        String requestAddr = clientRequestHeader.getRequest();
                        if (clientRequestHeader.isGet() && isCached(requestAddr)) {
                            if (this.cacheModifiedTime.get(requestAddr) != null
                                    && notModified(clientRequestHeader, serverOutputStream, serverInputStream)) {
                                returnCache(clientRequestHeader.getRequest(), clientOutputStream);
                                continueReadFromServer = false;
                                break;
                            }
                        }

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

                    System.out.println("===== Client Request to address: " + clientRequestHeader.getHeaderStr());
                }





                // ========== Return Server Response to Client
                int numBytesRead_Server = 0;
                int totalContentBytesRead_Server = 0;
                int contentLength_Server = 0;
                int headerEnd_Server = 0;
                byte[] buffer_Server = new byte[8190];
                Header serverResponseHeader = null;

                boolean hasNotReadHeader_Server = true;
                boolean hasCreateLocalFile = false;

                // Local file output
                OutputStream localFileStream = null;
                File localFile;

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

                    int attempts = 0;
                    while (serverInputStream.available() == 0 && attempts < 5)
                    {
                        attempts++;
                        Thread.sleep(10);
                    }

                    if (serverInputStream.available() != 0) {
                        // Return response to client
                        //lastModifiedTime = "Fri, 09 Jan 2015 11:27:40 GMT";
                        if (!hasCreateLocalFile) {
                            localFile = createCacheFile(clientRequestHeader.getRequest(), serverResponseHeader.getLastModifiedTime());
                            localFileStream = new FileOutputStream(localFile);

                            hasCreateLocalFile = true;
                        }

                        localFileStream.write(buffer_Server, 0, numBytesRead_Server);
                        clientOutputStream.write(buffer_Server, 0, numBytesRead_Server);

                        localFileStream.flush();
                        clientOutputStream.flush();

                        System.out.println("===== Server Response header: " + serverResponseHeader.getHeaderStr());

                        continue;
                    } else {

                        if (!hasCreateLocalFile) {
                            localFile = createCacheFile(clientRequestHeader.getRequest(), serverResponseHeader.getLastModifiedTime());
                            localFileStream = new FileOutputStream(localFile);

                            hasCreateLocalFile = true;
                        }

                        localFileStream.write(buffer_Server, 0, numBytesRead_Server);
                        clientOutputStream.write(buffer_Server, 0, numBytesRead_Server);

                        localFileStream.flush();
                        clientOutputStream.flush();

                        if (totalContentBytesRead_Server > contentLength_Server) {
                            continueReadFromServer = false;
                        } else {
                            continueReadFromServer = true;
                        }

                        System.out.println("===== Server Response header: " + serverResponseHeader.getHeaderStr());
                    }
                }

                if (serverSocket != null) {
                    serverSocket.close();
                }
                if (localFileStream != null) {
                    localFileStream.close();
                }
                this.socket.close();
                continueProcess = false;
                System.out.println("!== End of communication");
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();

            // Return bad request
            String badRequest502 = "HTTP/1.0 502 Bad Gateway\r\n\r\n502 Bad Request\r\n";
            byte[] badRequestByteArray = badRequest502.getBytes();

            try {
                this.serverResponse.write(badRequestByteArray, 0, badRequestByteArray.length);
                this.serverResponse.flush();
                this.socket.close();
            } catch (IOException e1) {
                System.out.println(e.getMessage());
                e1.printStackTrace();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*
    * Send to server if modified
    * */
    private boolean notModified(Header requestHeader, OutputStream serverOutputStream, InputStream serverInputStream) throws IOException{
        String lastModifiedTime = getCacheLastModifiedTime(requestHeader.getRequest());
        String ifModifiedMSG = formIfModifiedMSG(requestHeader, lastModifiedTime);

        // Send not Modified MSG to server
        byte[] modifiedMSGByteArray = ifModifiedMSG.getBytes();
        serverOutputStream.write(modifiedMSGByteArray, 0, modifiedMSGByteArray.length);
        serverOutputStream.flush();
        System.out.println("===== If modified header \n");
        System.out.println(ifModifiedMSG);


        // Read Server Response
        byte[] buffer_Server = new byte[8190];
        int headerEnd_Server;
        Header serverResponseHeader;
        String responseHeaderStr;

        serverInputStream.read(buffer_Server);
        headerEnd_Server = getHeaderEnd(buffer_Server);
        serverResponseHeader = new Header(getHeader(buffer_Server, headerEnd_Server));
        responseHeaderStr = serverResponseHeader.getHeaderStr();

        if (responseHeaderStr.contains("304") && responseHeaderStr.contains("Not Modified")) {
            System.out.println("===== Cache not modified");
            System.out.println("===== Server not modified response header \n");
            System.out.println(responseHeaderStr);
            return true;
        }
        return false;
    }

    /*
    * Return a simple HTTP header contains if modified field
    * */
    private String formIfModifiedMSG(Header requestHeader, String lastModifiedTime) {
        String trimedHeaderStr = requestHeader.getHeaderStr().trim();
        String lastModifiedField = "If-Modified-Since: ".concat(lastModifiedTime);
        return trimedHeaderStr.concat("\r\n").concat(lastModifiedField).concat("\r\n\r\n");
    }

    /*
    * Retrieve last modified time of the cached file
    * */
    private String getCacheLastModifiedTime(String requestAddress) {
        return this.cacheModifiedTime.get(requestAddress);
    }

    /*
     * Get the end of header from input byte array
     */
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
                responseOutput.flush();
            }
        } catch (IOException e) {
            System.out.println("Cannot read from file");
            e.printStackTrace();
        }
    }

    /*
    * Create Cache Directory
    * */
    private void createCacheDir() {
        if (!this.cacheDir.exists()) {
            System.out.println("====== Create Cache Folder: ProxyCachedFile");
            this.cacheDir.mkdir();
        }
    }

    /*
    * Create local file and add to cache list
    * */
    private File createCacheFile(String cacheRequAddr, String lastModifiedTime) throws IOException {

        String cacheFileAddr = "ProxyCachedFile/" + getHashCode(cacheRequAddr);
        this.cache.put(cacheRequAddr, cacheFileAddr);
        this.cacheModifiedTime.put(cacheRequAddr, lastModifiedTime);

        System.out.println("====== Create Cache File Address at: " + cacheFileAddr);
        File cacheFile = new File(cacheFileAddr);
        cacheFile.createNewFile();
        return cacheFile;
    }

    private long getHashCode(String cacheRequAddr) {
        return cacheRequAddr.hashCode();
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

    public boolean hasLastModifiedTime() {
        return getField("Last-Modified:") != null;
    }

    public String getLastModifiedTime() {return getField("Last-Modified:");}

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
                field = (cur_field.substring(cur_field.indexOf(':') + 1)).trim();
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