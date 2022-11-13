package com.thoughtworks.ebikecharger;


import static com.thoughtworks.ebikecharger.Constants.HOUR_AS_MILLIS;

import com.alibaba.fastjson.JSON;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

public class Charger implements Runnable {

  private static final long FULL_CHARGE_TIME = 8; // as hours

  // 插入电源的时间
  private final AtomicLong pluggedInTime = new AtomicLong();

  private final AtomicBoolean isPlugged = new AtomicBoolean(false);

  private final Object lock = new Object();

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
    while(isPlugged.get()){
      try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        HttpPost httpPost = new HttpPost("http://127.0.0.1:9090/charger/energyKnots");
        List<Integer> energyKnots = generateEnergyKnots(System.currentTimeMillis(), pluggedInTime.get());
        String energyKnotsJSON = JSON.toJSONString(energyKnots);
        httpPost.setEntity(new StringEntity(energyKnotsJSON, StandardCharsets.UTF_8));
        httpClient.execute(httpPost);
        httpPost.clear();
        Thread.sleep(HOUR_AS_MILLIS);
      } catch (IOException | InterruptedException e) {
        throw new RuntimeException(e);
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
      System.out.println("[Charger日志][充电器]：检测到电源插入");
      HttpPost httpPost = new HttpPost("http://127.0.0.1:9090/charger/status");
      httpPost.setEntity(new StringEntity("plugIn", StandardCharsets.UTF_8));
      httpClient.execute(httpPost);
      httpPost.clear();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void sendPlugOutEvent() {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      System.out.println("[Charger日志][充电器]：检测到电源拔出");
      HttpPost httpPost = new HttpPost("http://127.0.0.1:9090/charger/status");
      httpPost.setEntity(new StringEntity("plugOut", StandardCharsets.UTF_8));
      httpClient.execute(httpPost);
      httpPost.clear();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isPlugged() {
    return isPlugged.get();
  }

}