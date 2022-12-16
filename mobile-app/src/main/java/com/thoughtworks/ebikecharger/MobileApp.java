package com.thoughtworks.ebikecharger;

import static com.thoughtworks.ebikecharger.Constants.HTTP_STATUS_OK;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;


public class MobileApp {

  private final String username;

  private final ObjectMapper mapper = new ObjectMapper();

  public MobileApp(String username) {
    this.username = username;
  }

  public static void main(String[] args) {
    new MobileApp("test").checkBike();
  }

  // 请求获取bike的状态
  public void checkBike() {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet httpGet = new HttpGet("http://127.0.0.1:9999/bike/status");
      CloseableHttpResponse response = httpClient.execute(httpGet);

      if (response.getCode() == HTTP_STATUS_OK) {
        HttpEntity entity = response.getEntity();
        InputStream contentInputStream = entity.getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(contentInputStream, StandardCharsets.UTF_8));
        String line = reader.readLine();
        String threadName = Thread.currentThread().getName();
        System.out.printf("[%s]%s正在检查电动车状态：%s\n", threadName, username, line);
        reader.close();
        contentInputStream.close();
      }
      response.close();
      httpGet.clear();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  // 请求修改bike的状态
  public void reportBorrower(String bikeId) {
    synchronized (MobileApp.class) {
      try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        HttpPost httpPost = new HttpPost("http://127.0.0.1:9999/bike/status");
        Map<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("bikeId", bikeId);
        String paramsJson = mapper.writeValueAsString(params);
        httpPost.setEntity(new StringEntity(paramsJson));
        httpClient.execute(httpPost);
        httpPost.clear();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}