/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.zain.almksazain.utlities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author jgithu
 */
public class Httpcall {

    private final Logger loggger = LogManager.getLogger(Httpcall.class);

    @SuppressWarnings("empty-statement")
    public HashMap httpPOST(String message, String httpsURL, HashMap requestMap) {

        int conresponse;
        HashMap responseMap = new HashMap();
        String response = "";
        try {
            URL obj = new URL(httpsURL);
            HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setReadTimeout(45000);
            conn.setConnectTimeout(30000);
            conn.setRequestProperty("content-type", "application/json");
            OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
            osw.write(message);
            osw.flush();
            osw.close();
            String res = "";
            InputStreamReader in;
            try {
                in = new InputStreamReader(conn.getInputStream(), "UTF-8");
            } catch (IOException e) {
                System.out.println(e);
                in = new InputStreamReader(conn.getErrorStream(), "UTF-8");
            }

            try (Reader reader = new BufferedReader(in)) {
                for (int c; (c = reader.read()) >= 0; res = res + ((char) c));
            }
            conresponse = conn.getResponseCode();

            loggger.info("MESSAGE_FROM_MQ  RESPONSE " + res);

            responseMap.put("mqresponse", res);
            responseMap.put("mqresponseCode", conresponse);
            if (conresponse == 200) {
                responseMap.put("mqStatus", "000");
                responseMap.put("mqStatusDescription", "Successful");
            } else {
                responseMap.put("mqStatus", "057");
                responseMap.put("mqStatusDescription", "Not Successful, response code (" + conresponse + ")");
            }

        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            System.out.println(sw);
            if (sw.toString().contains("TimeoutException")) {
                responseMap.put("mqStatus", "091");
                responseMap.put("mqStatusDescription", "Not Successful TimeoutException: Cannot reach USSD Receiver");
            } else {
                responseMap.put("mqStatus", "057");
                responseMap.put("mqStatusDescription", "Not Successful");
            }
        }

        return responseMap;

    }

}
