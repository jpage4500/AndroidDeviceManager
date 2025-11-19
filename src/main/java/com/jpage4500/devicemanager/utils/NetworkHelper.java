package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class NetworkHelper {
    private static final Logger log = LoggerFactory.getLogger(NetworkHelper.class);

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    public static class HttpResponse {
        public int status;                          // -1 for error
        public String body;                         // response body or error message
    }

    public static class HttpDataResponse extends HttpResponse {
        public byte[] data;
    }

    /**
     * GET request
     */
    public HttpResponse getRequest(String urlStr) {
        return getRequest(urlStr, null);
    }

    /**
     * GET request
     */
    public HttpResponse getRequest(String urlStr, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            HttpURLConnection conn = createConnection(urlStr);
            addHeaders(conn, headers);

            response.status = conn.getResponseCode();
            response.body = readResponse(conn);
            log.trace("getRequest: {}, http:{}", urlStr, response.status);
        } catch (Exception e) {
            log.error("getRequest: error connecting to hub: {}, {}", urlStr, e.getMessage());
            response.status = -1;
            response.body = e.getMessage();
        }
        return response;
    }

    /**
     * POST request
     */
    public HttpResponse postRequest(String urlStr, String body) {
        return postRequest(urlStr, body, null);
    }

    /**
     * POST request
     */
    public HttpResponse postRequest(String urlStr, String body, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            HttpURLConnection conn = createPostConnection(urlStr);
            addHeaders(conn, headers);

            // Send body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            response.status = conn.getResponseCode();
            log.trace("postRequest: {}, http:{}, body:{}", urlStr, response.status, body);
            response.body = readResponse(conn);
            // only log body if error
            if (response.status != 200) {
                log.trace("postRequest: {}", response.body);
            }
        } catch (Exception e) {
            log.error("postRequest: error connecting to hub: {}, {}", urlStr, e.getMessage());
            response.status = -1;
            response.body = e.getMessage();
        }
        return response;
    }

    /**
     * download URL to byte array
     */
    public HttpDataResponse download(String urlStr, Map<String, String> headers) {
        HttpDataResponse response = new HttpDataResponse();
        try {
            HttpURLConnection conn = createConnection(urlStr);
            addHeaders(conn, headers);

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
                inputStream.close();
                log.trace("download: {}, http:{}, size:{} bytes", urlStr, response.status, response.data.length);
            } else {
                log.warn("download: failed, status: {}", response.status);
                response.body = readResponse(conn);
            }
        } catch (Exception e) {
            log.error("download: error connecting to hub: {}, {}", urlStr, e.getMessage());
            response.status = -1;
            response.body = e.getMessage();
        }
        return response;
    }

    /**
     * download file from URL
     */
    public HttpResponse downloadFile(String urlStr, File file, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            HttpURLConnection conn = createConnection(urlStr);
            addHeaders(conn, headers);

            DataInputStream dis = new DataInputStream(conn.getInputStream());
            byte[] buffer = new byte[1024];
            int length;

            FileOutputStream fos = new FileOutputStream(file);
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.close();
            dis.close();
            response.status = conn.getResponseCode();
        } catch (Exception e) {
            log.error("download: error connecting to hub: {}, {}", urlStr, e.getMessage());
            response.status = -1;
            response.body = e.getMessage();
        }
        return response;
    }

    /**
     * upload file to URL
     */
    public HttpResponse upload(String urlStr, File file, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            HttpURLConnection conn = createPostConnection(urlStr);

            // Set content type for file upload
            String boundary = "===" + System.currentTimeMillis() + "===";
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            addHeaders(conn, headers);

            try (OutputStream os = conn.getOutputStream();
                 FileInputStream fis = new FileInputStream(file)) {

                // Write multipart form data
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);

                // Add file part
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
                writer.append("Content-Type: application/octet-stream\r\n");
                writer.append("\r\n");
                writer.flush();

                // Write file content
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();

                // End of multipart/form-data
                writer.append("\r\n");
                writer.append("--").append(boundary).append("--").append("\r\n");
                writer.flush();
            }

            response.status = conn.getResponseCode();
            log.trace("upload: {}, http:{}, file:{}", urlStr, response.status, file.getName());
            // Read response if available
            response.body = readResponse(conn);

            // Log body if error
            if (response.status != 200 && response.body != null) {
                log.trace("upload: {}", response.body);
            }
        } catch (Exception e) {
            log.error("upload: error uploading file: {}, {}", urlStr, e.getMessage());
            response.status = -1;
            response.body = e.getMessage();
        }
        return response;
    }

    /**
     * create HttpURLConnection (GET)
     */
    private HttpURLConnection createConnection(String urlStr) throws IOException {
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
    private HttpURLConnection createPostConnection(String urlStr) throws IOException {
        HttpURLConnection connection = createConnection(urlStr);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        return connection;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
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

    private InputStream getInputStream(HttpURLConnection conn) throws IOException {
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

    private void addHeaders(HttpURLConnection conn, Map<String, String> headers) {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                conn.setRequestProperty(key, value);
            }
        }
    }

}
