package com.test.testApplication.service.CrptApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final CounterSingleton counter;
    @Value("${crptapi.apiUrl}") //take url from application.yaml
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public CrptApi(int requestLimit, TimeUnit timeUnit) {
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        this.counter = CounterSingleton.getInstance();
    }

    public void sendPostRequest(Document document, String signature) throws IOException, InterruptedException {
        synchronized (counter) {
            long currentTime = System.currentTimeMillis();
            long intervalInMillis = timeUnit.toMillis(1);

            while (counter.getCount() >= requestLimit) {
                long elapsedTime = currentTime - counter.getLastResetTime();
                if (elapsedTime >= intervalInMillis) {
                    counter.resetCount(currentTime);
                    break;
                }
                counter.wait(intervalInMillis - elapsedTime);
            }
            counter.countPlus();
        }

        URL url = new URL(apiUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", signature);
        con.setDoOutput(true);

        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json = ow.writeValueAsString(document);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = con.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Unexpected code " + responseCode);
        }

        synchronized (counter) {
            counter.countMinus();
            counter.notifyAll();
        }
    }

    public static class CounterSingleton {
        private static CounterSingleton instance;
        private int count = 0;
        private long lastResetTime;

        private CounterSingleton() {
            lastResetTime = System.currentTimeMillis();
        }

        public static synchronized CounterSingleton getInstance() {
            if (instance == null) {
                instance = new CounterSingleton();
            }
            return instance;
        }

        public synchronized int getCount() {
            return count;
        }

        public synchronized void countPlus() {
            count++;
        }

        public synchronized void countMinus() {
            count--;
        }

        public synchronized void resetCount(long currentTime) {
            count = 0;
            lastResetTime = currentTime;
        }

        public synchronized long getLastResetTime() {
            return lastResetTime;
        }
    }

    public static class Document {
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        public static class Product {
            private String certificate_document;
            private String certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private String production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;
        }
    }
}
