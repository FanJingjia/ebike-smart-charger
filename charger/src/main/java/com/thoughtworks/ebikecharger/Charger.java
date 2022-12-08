package com.thoughtworks.ebikecharger;

import static com.thoughtworks.ebikecharger.Constants.HOUR_AS_MILLIS;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class Charger implements Runnable {

  private static final long FULL_CHARGE_TIME = 8; // as hours

  private final ObjectMapper mapper = new ObjectMapper();

  private String id;

  //init a charger
  public Charger(String id) {
    this.id = id;
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost("http://127.0.0.1:9999/charger/init");
      Map<String, String> params = new HashMap<>();
      params.put("id", getId());
      params.put("isPlugged", String.valueOf(isPlugged.get()));
      String paramsJson = mapper.writeValueAsString(params);
      httpPost.setEntity(new StringEntity(paramsJson, ContentType.APPLICATION_JSON));
      httpClient.execute(httpPost);
      httpPost.clear();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private final AtomicLong pluggedInTime = new AtomicLong();

  private final AtomicBoolean isPlugged = new AtomicBoolean(false);

  private final Object lock = new Object();

  public String getId() {
    return id;
  }

  public void plugIn() {
    synchronized (lock) {
      isPlugged.set(true);
      pluggedInTime.set(System.currentTimeMillis());
      sendPlugInEvent();
    }
  }

  public void plugOut() {
    if (isPlugged()) {
      synchronized (lock) {
        isPlugged.set(false);
        sendPlugOutEvent();
      }
    }
  }


  @Override
  public void run() {
    for (; ; ) {
      while (isPlugged()) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
          HttpPost httpPost = new HttpPost("http://127.0.0.1:9999/charger/energyKnots");
          httpPost.setHeader("Content-Type", "application/json;charset=UTF-8");
          List<Integer> energyKnots = generateEnergyKnots(System.currentTimeMillis(), pluggedInTime.get());
          Map<String, String> params = new HashMap<>();
          params.put("energyKnots", energyKnots.toString());
          String paramsJson = mapper.writeValueAsString(params);
          httpPost.setEntity(new StringEntity(paramsJson, ContentType.APPLICATION_JSON));
          httpClient.execute(httpPost);
          httpPost.clear();
        } catch (IOException e) {
          System.out.println("充电器" + getId() + "的httpClient传输充电曲线异常");
        }
        try {
          Thread.sleep(HOUR_AS_MILLIS);
        } catch (InterruptedException e) {
          System.out.println("当前线程sleep出现异常");
        }
      }
    }
  }

  protected List<Integer> generateEnergyKnots(long now, long from) {
    if ((now - from) / HOUR_AS_MILLIS >= FULL_CHARGE_TIME + 1) {
      return Collections.emptyList();
    }
    List<Integer> knots = new ArrayList<>(10);
    long start = now - HOUR_AS_MILLIS;
    long slice = HOUR_AS_MILLIS / 10;
    for (int i = 0; i <= 9; i++) {
      knots.add((start + slice * i - from) / HOUR_AS_MILLIS >= FULL_CHARGE_TIME ? 0 : 10);
    }
    return knots;
  }

  private void sendPlugInEvent() {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      System.out.println("[Charger日志][充电器" + getId() + "]：检测到电源插入");
      sendHttpPostEvent(httpClient);
    } catch (IOException e) {
      System.out.println("充电器" + getId() + "的httpClient传输plugIn事件异常");
      e.printStackTrace();
    }
  }

  private void sendPlugOutEvent() {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      System.out.println("[Charger日志][充电器" + getId() + "]：检测到电源拔出");
      sendHttpPostEvent(httpClient);
    } catch (IOException e) {
      System.out.println("充电器" + getId() + "的httpClient传输plugOut事件异常");
      e.printStackTrace();
    }
  }

  private boolean isPlugged() {
    return isPlugged.get();
  }

  private void sendHttpPostEvent(CloseableHttpClient httpClient) throws IOException {
    HttpPost httpPost = new HttpPost("http://127.0.0.1:9999/charger/status");
    Map<String, String> params = new HashMap<>();
    params.put("id", getId());
    params.put("plugIn", String.valueOf(isPlugged.get()));
    String paramsJson = mapper.writeValueAsString(params);
    httpPost.setEntity(new StringEntity(paramsJson));
    httpClient.execute(httpPost);
    httpPost.clear();
  }
}