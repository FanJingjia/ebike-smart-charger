package com.thoughtworks.ebikecharger;

import static com.thoughtworks.ebikecharger.Constants.HTTP_STATUS_OK;
import static com.thoughtworks.ebikecharger.Constants.SPACE;

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
        initCharger(id + SPACE + isPlugged + "\n");
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
      Map<String, String> requestMap = mapper.readValue(requestBodyStr, new TypeReference<>() {
      });
      String id = requestMap.get("id");
      String status = requestMap.get("status");
      String username = requestMap.get("username");
      try {
        initBike(id,status,username);
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

    httpServer.createContext("/bike/plugOut", httpExchange -> {
      try (InputStream requestBody = httpExchange.getRequestBody()) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody))) {
          String requestBodyStr = reader.readLine();
          Map<String, String> requestMap = mapper.readValue(requestBodyStr, new TypeReference<>() {
          });
          String username = requestMap.get("username");
          String bikeId = requestMap.get("bikeId");
          receiveBorrowerAndWriteBikeFile(username, bikeId);
          httpExchange.sendResponseHeaders(HTTP_STATUS_OK, "reportBorrower".length());
          httpExchange.getResponseBody().write("reportBorrower".getBytes());
        }
      }
    });
    httpServer.createContext("/bike/status", httpExchange -> {
      String bikeStatus = checkBikeStatus();
      httpExchange.sendResponseHeaders(HTTP_STATUS_OK, bikeStatus.getBytes().length);
      httpExchange.getResponseBody().write(bikeStatus.getBytes());
    });
    httpServer.createContext("/bike/plugIn", httpExchange -> {
      try (InputStream requestBody = httpExchange.getRequestBody()) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody))) {
          String requestBodyStr = reader.readLine();
          Map<String, String> requestMap = mapper.readValue(requestBodyStr, new TypeReference<>() {
          });
          String id = requestMap.get("id");
          receiveBikePlugInEvent(id);
          httpExchange.sendResponseHeaders(HTTP_STATUS_OK, "reportBorrower".length());
          httpExchange.getResponseBody().write("reportBorrower".getBytes());
        }
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

  private String checkBikeStatus() throws IOException {
    StringBuilder allBikeStatus = new StringBuilder();
    Path path = Path.of(BIKE_FILE_NAME);
    String bikeFileContent = Files.readString(path);
    String[] bikeLines = bikeFileContent.split("\n");
    for (String bikeLine : bikeLines) {
      String bikeId = bikeLine.split(SPACE)[0];
      String bikeStatus = bikeLine.split(SPACE)[1];
      if (bikeStatus.equals("plugIn")) {
        allBikeStatus.append("自行车")
            .append(bikeId)
            .append("正在充电")
            .append("\n");
      } else {
        String username = bikeLine.split(SPACE)[2];
        allBikeStatus.append(username)
            .append("正在使用自行车")
            .append(bikeId)
            .append("\n");
      }
    }
    return allBikeStatus.toString();
  }

  public void receiveBorrowerAndWriteBikeFile(String username, String bikeId) throws IOException {
    borrowerReadWriteLock.writeLock().lock();
    System.out.printf("%s已经骑走自行车%s\n", username, bikeId);
    borrower = username;
    borrowerReadWriteLock.writeLock().unlock();
    Path path = Path.of(BIKE_FILE_NAME);
    String[] bikeLines = Files.readString(path).split("\n");
    Files.writeString(path, "");
    for (String bikeLine : bikeLines) {
      String tmpBikeId = bikeLine.split(SPACE)[0];
      if (bikeId.equals(tmpBikeId)) {
        String line = tmpBikeId + SPACE + "plugOut" + SPACE + username + "\n";
        Files.writeString(path, line, StandardOpenOption.APPEND);
      } else {
        Files.writeString(path, bikeLine + "\n", StandardOpenOption.APPEND);
      }
    }
  }

  private void receivePlugEventAndWriteChargerFile(String id, Boolean plugIn) throws IOException {
    Path path = Path.of(CHARGER_FILE_NAME);
    String[] chargerLines = Files.readString(path).split("\n");
    Files.writeString(path, "");
    for (String chargerLine : chargerLines) {
      String chargerId = chargerLine.split(SPACE)[0];
      if (id.equals(chargerId)) {
        String line = chargerId + SPACE + plugIn + "\n";
        Files.writeString(path, line, StandardOpenOption.APPEND);
      } else {
        Files.writeString(path, chargerLine + "\n", StandardOpenOption.APPEND);
      }
    }
  }

  private void receiveBikePlugInEvent(String bikeId) throws IOException {
    Path path = Path.of(BIKE_FILE_NAME);
    String[] bikeLines = Files.readString(path).split("\n");
    Files.writeString(path, "");
    for (String bikeLine : bikeLines) {
      String tmpBikeId = bikeLine.split(SPACE)[0];
      if (bikeId.equals(tmpBikeId)) {
        String line = tmpBikeId + SPACE + "plugIn" + "\n";
        Files.writeString(path, line, StandardOpenOption.APPEND);
      } else {
        Files.writeString(path, bikeLine + "\n", StandardOpenOption.APPEND);
      }
    }
  }

  private void initCharger(String str) throws IOException {
    Path path = Path.of(CHARGER_FILE_NAME);
    Files.writeString(path, str, StandardOpenOption.APPEND);
  }

  private void initBike(String id,String status,String username) throws IOException {
    Path path = Path.of(BIKE_FILE_NAME);
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(id)
        .append(SPACE)
        .append(status)
        .append(SPACE)
        .append(username);
    Files.writeString(path, stringBuilder.toString(), StandardOpenOption.APPEND);
  }

  private void initFile(String filePath) throws IOException {
    Path path = Path.of(filePath);
    Files.writeString(path, "");
  }
}
