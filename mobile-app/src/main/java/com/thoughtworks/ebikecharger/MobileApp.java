package com.thoughtworks.ebikecharger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;


public class MobileApp {

  private final String username;

  public MobileApp(String username) {
    this.username = username;
  }

  public static void main(String[] args) {
    new MobileApp("test").checkBike();
  }

  // 请求获取bike的状态
  public void checkBike() {
    try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), 9090)) {
      try (InputStream inputStream = socket.getInputStream()) {
        try (OutputStream outputStream = socket.getOutputStream()) {
          byte[] requestByteArray = "GET /bike/status\n".getBytes(StandardCharsets.UTF_8);
          outputStream.write(requestByteArray);
          outputStream.flush();
          String threadName = Thread.currentThread().getName();
          try (BufferedReader bufferedReader = new BufferedReader(
              new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String bikeStatus = bufferedReader.readLine();
            System.out.printf("[%s]%s正在检查电动车状态：%s\n", threadName, username, bikeStatus);
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // 请求修改bike的状态
  public void reportBorrower() {
    synchronized (MobileApp.class) {
      try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), 9090)) {
        try (OutputStream outputStream = socket.getOutputStream()) {
          String request = "POST /bike/status/" + username + "\n";
          outputStream.write(request.getBytes(StandardCharsets.UTF_8));
          outputStream.flush();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}