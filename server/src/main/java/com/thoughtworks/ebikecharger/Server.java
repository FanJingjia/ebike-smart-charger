package com.thoughtworks.ebikecharger;

import static com.thoughtworks.ebikecharger.Constants.GET_METHOD;
import static com.thoughtworks.ebikecharger.Constants.HTTP_STATUS_OK;
import static com.thoughtworks.ebikecharger.Constants.POST_METHOD;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Server {

  static final String CHARGER_FILE_NAME = "charger.txt";
  static final String BIKE_FILE_NAME = "bike.txt";
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

  private final ObjectMapper mapper = new ObjectMapper();

  public void init() {
    try {
      initFile(CHARGER_FILE_NAME);
      initFile(BIKE_FILE_NAME);
    } catch (IOException e) {
      System.out.println("初始化数据文件异常");
      e.printStackTrace();
    }
    httpServer.createContext("/charger/init", httpExchange -> {
      InputStream requestBody = httpExchange.getRequestBody();
      BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
      String requestBodyStr = reader.readLine();
      Map<String, String> requestMap = mapper.readValue(requestBodyStr, new TypeReference<>() {
      });
      String id = requestMap.get("id");
      String isPlugged = requestMap.get("isPlugged");
      try {
        initCharger(id + " " + isPlugged + "\n");
      } catch (IOException e) {
        System.out.println("写文件异常");
        e.printStackTrace();
      }
      httpExchange.sendResponseHeaders(HTTP_STATUS_OK, "initCharger".length());
      httpExchange.getResponseBody().write("initCharger".getBytes());
    });
    httpServer.createContext("/bike/init", httpExchange -> {
      InputStream requestBody = httpExchange.getRequestBody();
      BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
      String requestBodyStr = reader.readLine();
      Map<String, Bike> requestMap = mapper.readValue(requestBodyStr, new TypeReference<>() {
      });
      Bike bike = requestMap.get("bike");
      try {
        initBike(bike);
      } catch (IOException e) {
        System.out.println("写文件异常");
        e.printStackTrace();
      }
      httpExchange.sendResponseHeaders(HTTP_STATUS_OK, "initBike".length());
      httpExchange.getResponseBody().write("initBike".getBytes());
    });
    httpServer.createContext("/charger/energyKnots", httpExchange -> {
      InputStream requestBody = httpExchange.getRequestBody();
      BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
      String requestBodyStr = reader.readLine();
      Map<String, String> requestMap = mapper.readValue(requestBodyStr, new TypeReference<>() {
      });
      String energyKnots = requestMap.get("energyKnots");
      receiveEnergyKnots(energyKnots);
      httpExchange.sendResponseHeaders(HTTP_STATUS_OK, "energyKnots".length());
      httpExchange.getResponseBody().write("energyKnots".getBytes());
    });
    httpServer.createContext("/charger/status", httpExchange -> {
      InputStream requestBody = httpExchange.getRequestBody();
      BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
      String requestBodyStr = reader.readLine();
      Map<String, String> requestMap = mapper.readValue(requestBodyStr, new TypeReference<>() {
      });
      boolean plugIn = Boolean.parseBoolean(requestMap.get("plugIn"));
      String id = requestMap.get("id");
      receivePlugEvent(id, plugIn);

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

  public void receivePlugEvent(String id, boolean plugIn) throws IOException {
    electricityReadWriteLock.writeLock().lock();
    electricityStatus = plugIn;
    electricityReadWriteLock.writeLock().unlock();
    electricityReadWriteLock.readLock().lock();
    receivePlugEventAndWriteChargerFile(id, plugIn);
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

  private void receivePlugEventAndWriteChargerFile(String id, Boolean plugIn) throws IOException {
    Path path = Path.of(CHARGER_FILE_NAME);
    String[] chargerLines = Files.readString(path).split("\n");
    Files.writeString(path, "");
    for (String chargerLine : chargerLines) {
      String chargerId = chargerLine.split(" ")[0];
      if (id.equals(chargerId)) {
        String line = chargerId + " " + plugIn + "\n";
        Files.writeString(path, line, StandardOpenOption.APPEND);
      } else {
        Files.writeString(path, chargerLine + "\n", StandardOpenOption.APPEND);
      }
    }
  }

  private void initCharger(String str) throws IOException {
    Path path = Path.of(CHARGER_FILE_NAME);
    Files.writeString(path, str, StandardOpenOption.APPEND);
  }

  private void initBike(Bike bike) throws IOException {
    Path path = Path.of(BIKE_FILE_NAME);
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(bike.getId())
        .append(" ")
        .append(bike.getStatus())
        .append(" ")
        .append(bike.getUsername());
    Files.writeString(path, stringBuilder.toString(), StandardOpenOption.APPEND);
  }

  private void initFile(String filePath) throws IOException {
    Path path = Path.of(filePath);
    Files.writeString(path, "");
  }
}
