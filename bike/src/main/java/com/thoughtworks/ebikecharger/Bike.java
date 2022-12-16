package com.thoughtworks.ebikecharger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class Bike {

  private final String id;

  // status分为闲置和使用
  private String status;

  // 使用者名称=使用者名称/空字符串
  private String username;

  private final ObjectMapper mapper = new ObjectMapper();

  public Bike(String id) {
    this.id = id;
    this.status = "plugOut";
    this.username = "";
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost("http://127.0.0.1:9999/bike/init");
      Map<String, Bike> params = new HashMap<>();
      params.put("bike", this);
      String paramsJson = mapper.writeValueAsString(params);
      httpPost.setEntity(new StringEntity(paramsJson, ContentType.APPLICATION_JSON));
      httpClient.execute(httpPost);
      httpPost.clear();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void plugIn() {
    setStatus("plugIn");
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost("http://127.0.0.1:9999/bike/status");
      Map<String, Bike> params = new HashMap<>();
      params.put("bike", this);
      String paramsJson = mapper.writeValueAsString(params);
      httpPost.setEntity(new StringEntity(paramsJson, ContentType.APPLICATION_JSON));
      httpClient.execute(httpPost);
      httpPost.clear();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void plugOut() {
    setStatus("plugOut");
  }

  public String getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public String getUsername() {
    return username;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
