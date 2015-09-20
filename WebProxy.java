import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.HashMap;
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
    private BufferedOutputStream clientResponse;
    private File cacheDir;
    private String[] censorWords;

    public ProxyThreadRunnable(Socket socket, HashMap<String, String> cache) throws IOException {
        this.socket = socket;
        this.cache = cache;
        this.clientResponse = new BufferedOutputStream(socket.getOutputStream());
        this.cacheDir = new File("ProxyCachedFile");
        this.censorWords = readFromCensorFile();

        createCacheDir();
    }

    public void run() {
        String input;
        try {
            while (true) {

                // Receive client request, establish new socket to remote server
                HTTPMSG clientReqMSG = formHTTPMSG(this.socket);
                Header requestHeader = clientReqMSG.getHeader();
                String requestAddr = requestHeader.getRequest();
                System.out.println("====== Client request: \n" + clientReqMSG.getHeader().getHeaderStr());

                // Connect to remote server
                Socket serverSocket = new Socket(clientReqMSG.getHeader().getHost(), 80);

                // Check cache for GET requests
                if (requestHeader.isGet()) {
                    if (isCached(requestAddr) && notModified()) {
                        returnCache(requestAddr, this.clientResponse);
                    } else {
                        // Send client request to remote server
                        forwardRequest(clientReqMSG, serverSocket);

                        // Form server response http message
                        HTTPMSG serverResponseMSG = formHTTPMSG(serverSocket);

                        // Cache and return response
                        cacheAndReturnResponse(requestAddr, serverResponseMSG.getHeader(),
                                               serverResponseMSG.getBodyScanner(),
                                               this.clientResponse);
                    }
                } else {
                    // Send client request to remote server
                    forwardRequest(clientReqMSG, serverSocket);

                    // Get server response stream
                    BufferedInputStream serverReponse = new BufferedInputStream(serverSocket.getInputStream());

                    // Transferring remote server responses back to client
                    returnResponse(serverReponse, this.clientResponse);
                }

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
            e.printStackTrace();
        }
    }

    private boolean notModified() {
        return true;
    }

    /*
    * Read censor file content into string array
    * */
    private String[] readFromCensorFile() throws IOException {
        LinkedList<String> censorWords = new LinkedList<String>();
        FileInputStream fis = new FileInputStream("censor.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));

        String line = null;
        while ((line = br.readLine()) != null) {
            censorWords.add(line);
        }

        br.close();
        return censorWords.toArray(new String[censorWords.size()]);
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
    private void returnCache(String reqAddr, BufferedOutputStream responseOutput) throws FileNotFoundException {
        System.out.println("====== Hit cache " + reqAddr);

        String fileAddr = this.cache.get(reqAddr);
        BufferedInputStream cacheObj = new BufferedInputStream(new FileInputStream(fileAddr));

        try {
            int numResponseBytes;
            byte[] buffer = new byte[1024];

            while ((numResponseBytes = cacheObj.read(buffer)) != -1) {
                System.out.println("Return from cache " + numResponseBytes + " bytes");
                responseOutput.write(buffer, 0, numResponseBytes);
            }
            responseOutput.flush();
        } catch (IOException e) {
            System.out.println("Cannot read from cached file");
            e.printStackTrace();
        }
    }

    /*
    * Cache response to local file and at the same time return to client
    * */
    private void cacheAndReturnResponse(String reqAddr,
                                        Header responseHeader,
                                        Scanner serverResponseMSG,
                                        BufferedOutputStream clientResponse) throws IOException {
        System.out.println("===== Server response header \n" + responseHeader.getHeaderStr());

        if (responseHeader.isTextContent()) {
            // Apply censorship for text content before cache or return
            System.out.println("====== Cache and censor response " + reqAddr);
            censorCacheAndReturn(reqAddr, serverResponseMSG, clientResponse);

        } else {
            // Cache and return original content for General Media type
            System.out.println("====== Cache original response " + reqAddr);
            normalCacheAndReturn(reqAddr, serverResponseMSG, clientResponse);
        }
    }

    /*
    * Apply censorship to text messages
    * */
    private void censorCacheAndReturn(String reqAddr,
                                      Scanner serverReponse,
                                      BufferedOutputStream clientResponse) throws IOException {
        File localFile = createCacheFile(reqAddr);
        BufferedOutputStream localFileStream = new BufferedOutputStream(new FileOutputStream(localFile));

        String cur_line = null;
        String cur_censored_read = null;
        byte[] strToByte = null;

        while ((cur_line = serverReponse.nextLine()) != null) {
            System.out.println("Receiving and Cache from server, apply censorship");
            cur_censored_read = censorString(cur_line);
            strToByte = cur_censored_read.getBytes(Charset.forName("UTF-8"));

            localFileStream.write(strToByte, 0, strToByte.length);
            clientResponse.write(strToByte, 0, strToByte.length);
        }
        clientResponse.flush();
        localFileStream.flush();

        // Close local cache file
        localFileStream.close();
    }

    /*
    * Replace all the censored words with 3 dashes "---"
    * */
    private String censorString(String input) {
        for (int i = 0; i < this.censorWords.length; i++) {
            input = input.replaceAll(this.censorWords[i], "---");
        }
        return input;
    }

    /*
    * Cache and return original content
    * */
    private void normalCacheAndReturn(String reqAddr,
                                      Scanner serverResponse,
                                      BufferedOutputStream clientResponse) throws IOException {

        File localFile = createCacheFile(reqAddr);
        BufferedOutputStream localFileStream = new BufferedOutputStream(new FileOutputStream(localFile));

        int curByte;

        while (serverResponse.hasNextByte()) {
            curByte = (int) serverResponse.nextByte();
            localFileStream.write(curByte);
            clientResponse.write(curByte);
        }
        clientResponse.flush();
        localFileStream.flush();

        // Close local cache file
        localFileStream.close();
    }

    /*
    * Create local file and add to cache list
    * Put filePath into cache hashmap and create new file
    * */
    private File createCacheFile(String cacheRequAddr) throws IOException {

        String cacheFileAddr = "ProxyCachedFile/" + cacheRequAddr.hashCode();
        this.cache.put(cacheRequAddr, cacheFileAddr);

        System.out.println("====== Create Cache File Address at: " + cacheFileAddr);
        File cacheFile = new File(cacheFileAddr);
        cacheFile.createNewFile();
        return cacheFile;
    }

    /*
    * Create Cache Directory
    * */
    private void createCacheDir() {
        if (!cacheDir.exists()) {
            System.out.println("====== Create Cache Folder: ProxyCachedFile");
            cacheDir.mkdir();
        }
    }

    /*
    * Forward clinet request to remote server;
    * */
    private void forwardRequest(HTTPMSG clientReqMSG, Socket serverSocket) throws IOException {
        System.out.println("====== Forwarding request to server");

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

    /*
    * Form a HTTPMSG class
    * */
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

    public String getRequest() { return this.headerList.getFirst().split(" ")[1]; }

    public String getContentType() {
        return getField("Content-Type:");
    }

    public Boolean isTextContent() { return this.getContentType().equals("text/html");}
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