package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    /**
     * GET request
     */
    public HttpResponse getRequest(String urlStr) {
        // TODO: come up with some default headers
        return getRequest(urlStr, null);
    }

    /**
     * GET request
     */
    public HttpResponse getRequest(String urlStr, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(false);

            addHeaders(conn, headers);

            response.status = conn.getResponseCode();
            log.debug("getRequest: {}, http:{}", urlStr, response.status);
            // http:302 has no body
            if (conn.getContentLength() == 0) return response;

            InputStream inputStream = getInputStream(conn);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                response.body = sb.toString();
            }
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
        // TODO: come up with some default headers
        return postRequest(urlStr, body, null);
    }

    /**
     * POST request
     */
    public HttpResponse postRequest(String urlStr, String body, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);

            addHeaders(conn, headers);

            // Send body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            response.status = conn.getResponseCode();
            log.debug("postRequest: {}, http:{}, body:{}", urlStr, response.status, body);
            // http:302 has no body
            if (conn.getContentLength() == 0) return response;

            InputStream inputStream = getInputStream(conn);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line.trim());
                }
                response.body = sb.toString();
            }
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
     * download file from URL
     */
    public HttpResponse download(String urlStr, File file, Map<String, String> headers) {
        HttpResponse response = new HttpResponse();
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(false);

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
            log.error("postRequest: error connecting to hub: {}, {}", urlStr, e.getMessage());
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
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);

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
            log.debug("upload: {}, http:{}, file:{}", urlStr, response.status, file.getName());

            // Read response if available
            if (conn.getContentLength() != 0) {
                InputStream inputStream = getInputStream(conn);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line.trim());
                    }
                    response.body = sb.toString();
                }
            }

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
        // Set request headers if provided
        boolean hasReferer = false;
        if (headers != null) {
            //log.trace("addHeaders: {}", GsonHelper.toJson(headers));
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                if ("Referer".equalsIgnoreCase(key)) {
                    hasReferer = true;
                }
                String value = entry.getValue();
                conn.setRequestProperty(key, value);
            }
        }
        if (!hasReferer) {
            // always add referer header
            conn.setRequestProperty("Referer", conn.getURL().toString());
            //log.trace("addHeaders: Referer: {}", conn.getURL().toString());
        }
    }

}
