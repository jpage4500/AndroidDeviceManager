package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class NetworkHelper {
    private static final Logger log = LoggerFactory.getLogger(NetworkHelper.class);

    private static final int CONNECT_TIMEOUT = 10000;   // default connection timeout (10 secs)
    private static final int READ_TIMEOUT = 20000;      // default read timeout (20 secs)
    private static final int UPLOAD_TIMEOUT = 1200000;  // longer timeout for actions like uploading files or downloading screenshots

    // track network requests for matching up request/response when logging
    private static final AtomicInteger requestNumber = new AtomicInteger();

    public static class HttpResponse {
        public int status;                          // -1 for error
        public String body;                         // response body or error message
        public int requestNumber;
        public Timer timer;

        public HttpResponse() {
            requestNumber = NetworkHelper.requestNumber.incrementAndGet();
            timer = new Timer();
        }
    }

    public static class HttpDataResponse extends HttpResponse {
        public long dataSize;
        public byte[] data;
    }

    /**
     * GET request
     */
    public static HttpResponse getRequest(String urlStr) {
        return getRequest(urlStr, null);
    }

    /**
     * GET request
     */
    public static HttpResponse getRequest(String urlStr, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            HttpURLConnection conn = createConnection(urlStr);
            addHeaders(conn, headers);
            logRequest(conn, response, null);
            response.status = conn.getResponseCode();
            response.body = readResponse(conn);
            logResponse(conn, response);
        } catch (Exception e) {
            response.status = -1;
            response.body = e.getMessage();
            logError(urlStr, response);
        }
        return response;
    }

    /**
     * POST request
     */
    public static HttpResponse postRequest(String urlStr, String body) {
        return postRequest(urlStr, body, null);
    }

    /**
     * POST request
     */
    public static HttpResponse postRequest(String urlStr, String body, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            HttpURLConnection conn = createPostConnection(urlStr);
            addHeaders(conn, headers);
            logRequest(conn, response, body);

            // send body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            response.status = conn.getResponseCode();
            response.body = readResponse(conn);
            logResponse(conn, response);
        } catch (Exception e) {
            response.status = -1;
            response.body = e.getMessage();
            logError(urlStr, response);
        }
        return response;
    }

    /**
     * download URL to byte array
     */
    public static HttpDataResponse download(String urlStr, Map<String, String> headers) {
        HttpDataResponse response = new HttpDataResponse();
        try {
            HttpURLConnection conn = createConnection(urlStr);
            // wait longer for actions such as screenshot
            conn.setReadTimeout(UPLOAD_TIMEOUT);
            addHeaders(conn, headers);
            logRequest(conn, response, null);

            response.status = conn.getResponseCode();

            if (response.status >= 200 && response.status < 300) {
                InputStream inputStream = getInputStream(conn);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                response.data = baos.toByteArray();
                response.dataSize = response.data.length;
                inputStream.close();
            } else {
                response.body = readResponse(conn);
            }
            logResponse(conn, response);
        } catch (Exception e) {
            response.status = -1;
            response.body = e.getMessage();
            logError(urlStr, response);
        }
        return response;
    }

    /**
     * download file from URL
     */
    public static HttpResponse downloadFile(String urlStr, File file, Map<String, String> headers) {
        HttpDataResponse response = new HttpDataResponse();
        try {
            HttpURLConnection conn = createConnection(urlStr);
            addHeaders(conn, headers);
            logRequest(conn, response, null);

            DataInputStream dis = new DataInputStream(conn.getInputStream());
            byte[] buffer = new byte[1024];
            int length;

            FileOutputStream fos = new FileOutputStream(file);
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.close();
            dis.close();
            response.dataSize = file.length();
            response.status = conn.getResponseCode();
            logResponse(conn, response);
        } catch (Exception e) {
            response.status = -1;
            response.body = e.getMessage();
            logError(urlStr, response);
        }
        return response;
    }

    /**
     * upload file to URL
     */
    public static HttpResponse upload(String urlStr, File file, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            HttpURLConnection conn = createPostConnection(urlStr);
            conn.setReadTimeout(UPLOAD_TIMEOUT);

            // set content type for file upload
            String boundary = "===" + System.currentTimeMillis() + "===";
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            addHeaders(conn, headers);
            logRequest(conn, response, null);

            try (OutputStream os = conn.getOutputStream();
                 FileInputStream fis = new FileInputStream(file)) {

                // write multipart form data
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);

                // add file part
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
                writer.append("Content-Type: application/octet-stream\r\n");
                writer.append("\r\n");
                writer.flush();

                // write file content
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();

                // end of multipart/form-data
                writer.append("\r\n");
                writer.append("--").append(boundary).append("--").append("\r\n");
                writer.flush();
            }

            response.status = conn.getResponseCode();
            // read response if available
            response.body = readResponse(conn);
            logResponse(conn, response);
        } catch (Exception e) {
            response.status = -1;
            response.body = e.getMessage();
            logError(urlStr, response);
        }
        return response;
    }

    /**
     * create HttpURLConnection (GET)
     */
    private static HttpURLConnection createConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        return conn;
    }

    /**
     * create HttpURLConnection (POST)
     */
    private static HttpURLConnection createPostConnection(String urlStr) throws IOException {
        HttpURLConnection connection = createConnection(urlStr);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        return connection;
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        if (conn.getContentLength() != 0) {
            InputStream inputStream = getInputStream(conn);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line.trim());
                }
                return sb.toString();
            }
        }
        return null;
    }

    private static InputStream getInputStream(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
        String encoding = conn.getContentEncoding();
        if ("gzip".equalsIgnoreCase(encoding)) {
            inputStream = new GZIPInputStream(inputStream);
        } else if ("deflate".equalsIgnoreCase(encoding)) {
            inputStream = new InflaterInputStream(inputStream);
        }
        return inputStream;
    }

    private static void addHeaders(HttpURLConnection conn, Map<String, String> headers) {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                conn.setRequestProperty(key, value);
            }
        }
    }

    /**
     * log request:
     * >> 1) INFO: http://192.168.0.95:8766/api/info
     * >> 2) INFO: https://server-name.dev:443/api/info
     * >> 3) INFO: POST http://192.168.0.95:8766/api/info "{key:value}"
     */
    private static void logRequest(HttpURLConnection connection, HttpResponse response, String body) {
        String url = connection.getURL().toString();
        String lastPath = getLastPath(url);
        String method = connection.getRequestMethod();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(">> %d) %s: ", response.requestNumber, lastPath));
        if (!TextUtils.equalsIgnoreCase(method, "GET")) {
            sb.append(method + " ");
        }
        sb.append(url);
        if (body != null) {
            sb.append(String.format(" \"%s\"", body));
        }
        log.trace(sb.toString());
    }

    /**
     * log response:
     * << 1) INFO: 200ms, OK: http://192.168.0.95:8766/api/info, "{key:value}"
     * << 2) INFO: 20s, ERROR:401, "Connection Failed", http://192.168.0.95:8766/api/info
     */
    private static void logResponse(HttpURLConnection connection, HttpResponse response) {
        String url = connection.getURL().toString();
        String lastPath = getLastPath(url);
        String method = connection.getRequestMethod();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<< %d) %s: %s, ", response.requestNumber, lastPath, response.timer));
        if (response.status == 200) {
            if (response.body != null) {
                sb.append(FileUtils.bytesToDisplayString(response.body.length())).append(", ");
            } else if (response instanceof HttpDataResponse dataResponse && dataResponse.dataSize > 0) {
                sb.append(FileUtils.bytesToDisplayString(dataResponse.dataSize)).append(", ");
            }
        } else {
            sb.append(String.format("ERROR:%d, \"%s\", ", response.status, response.body));
        }
        if (!TextUtils.equalsIgnoreCase(method, "GET")) {
            sb.append(method).append(" ");
        }
        sb.append(url);
        if (response.body != null && response.status == 200) {
            sb.append(String.format(", \"%s\"", TextUtils.truncate(response.body, 1000)));
        }
        log.trace(sb.toString());
    }

    private static void logError(String url, HttpResponse response) {
        String lastPath = getLastPath(url);
        log.error("<< {}) {}: {}: ERROR: \"{}\": {}", response.requestNumber, lastPath, response.timer, response.body, url);
    }

    private static String getLastPath(String url) {
        if (url == null) return null;
        String[] splitArr = url.split("/");
        String lastPath = splitArr[splitArr.length - 1];
        int pos = TextUtils.indexOf(lastPath, '?');
        if (pos > 0) lastPath = lastPath.substring(0, pos);
        return lastPath.toUpperCase();
    }

}
