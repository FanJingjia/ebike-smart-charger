package com.thoughtworks.ebikecharger;

import static com.thoughtworks.ebikecharger.Constants.GET_METHOD;
import static com.thoughtworks.ebikecharger.Constants.HTTP_STATUS_OK;
import static com.thoughtworks.ebikecharger.Constants.POST_METHOD;

import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Server {

  private final HttpServer httpServer;

  private boolean electricityStatus = false;

  private String borrower = "";

  private final ReadWriteLock electricityReadWriteLock = new ReentrantReadWriteLock();

  private final ReadWriteLock borrowerReadWriteLock = new ReentrantReadWriteLock();

  public Server() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(Constants.PORT), 0);
  }

  public void start() {
    httpServer.start();
  }

  public void init() {
    httpServer.createContext("/charger/energyKnots", httpExchange -> {
      InputStream requestBody = httpExchange.getRequestBody();
      BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
      String energyKnots = reader.readLine();
      receiveEnergyKnots(energyKnots);
      httpExchange.sendResponseHeaders(HTTP_STATUS_OK, "energyKnots".length());
      httpExchange.getResponseBody().write("energyKnots".getBytes());
    });
    httpServer.createContext("/charger/status", httpExchange -> {
      InputStream requestBody = httpExchange.getRequestBody();
      BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
      String chargerStatus = reader.readLine();
      receivePlugEvent(chargerStatus.equals("plugIn"));

      httpExchange.sendResponseHeaders(HTTP_STATUS_OK, "chargerStatus".length());
      httpExchange.getResponseBody().write("chargerStatus".getBytes());
    });

    httpServer.createContext("/bike/status", httpExchange -> {
      String requestMethod = httpExchange.getRequestMethod();
      if (POST_METHOD.equals(requestMethod)) {
        //请求修改bike状态
        try (InputStream requestBody = httpExchange.getRequestBody()) {
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody))) {
            String username = reader.readLine();
            receiveBorrower(username);
            httpExchange.sendResponseHeaders(HTTP_STATUS_OK, "bikeStatus".length());
            httpExchange.getResponseBody().write("bikeStatus".getBytes());
            httpExchange.close();
          }
        }
      } else if (GET_METHOD.equals(requestMethod)) {
        //请求获取bike状态
        String bikeStatus = checkBikeStatus();
        httpExchange.sendResponseHeaders(HTTP_STATUS_OK, bikeStatus.getBytes().length);
        httpExchange.getResponseBody().write(bikeStatus.getBytes());
      }
    });
  }

  public void receivePlugEvent(boolean plugIn) {
    electricityReadWriteLock.writeLock().lock();
    electricityStatus = plugIn;
    electricityReadWriteLock.writeLock().unlock();
    electricityReadWriteLock.readLock().lock();
    if (electricityStatus) {
      System.out.println("[Server日志][电动车]：进入充电状态");
    } else {
      System.out.println("[Server日志][电动车]：解除充电状态");
    }
    electricityReadWriteLock.readLock().unlock();
  }

  private void receiveEnergyKnots(String energyKnots) {
    electricityReadWriteLock.readLock().lock();
    if (electricityStatus) {
      System.out.println("[Server日志][电动车]当前的充电功率曲线为:" + energyKnots);
    }
    electricityReadWriteLock.readLock().unlock();
  }

  private String checkBikeStatus() {
    StringBuilder bikeStatus = new StringBuilder();
    electricityReadWriteLock.readLock().lock();
    if (electricityStatus) {
      bikeStatus.append("电动车正在充电");
    } else {
      bikeStatus.append("电动车未处于充电状态");
    }
    electricityReadWriteLock.readLock().unlock();
    bikeStatus.append(",");
    borrowerReadWriteLock.readLock().lock();
    if (borrower.length() == 0) {
      bikeStatus.append("目前电动车处于闲置状态");
    } else {
      bikeStatus.append(String.format("目前%s正在使用电动车", borrower));
    }
    borrowerReadWriteLock.readLock().unlock();
    return bikeStatus.toString();
  }

  public void receiveBorrower(String username) {
    borrowerReadWriteLock.writeLock().lock();
    System.out.printf("已经上报%s\n", username);
    borrower = username;
    borrowerReadWriteLock.writeLock().unlock();
  }
}
