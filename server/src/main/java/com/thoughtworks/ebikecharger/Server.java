package com.thoughtworks.ebikecharger;

import static com.thoughtworks.ebikecharger.Constants.GET_METHOD;
import static com.thoughtworks.ebikecharger.Constants.HOUR_AS_MILLIS;
import static com.thoughtworks.ebikecharger.Constants.POST_METHOD;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Server implements Runnable {

  ServerSocket serverSocket = new ServerSocket(9090);

  private boolean electricityStatus = false;

  private String borrower = "";

  public Server() throws IOException {
    // create this empty constructor to throw IOException
  }

  @Override
  public void run() {
    while (true) {
      Socket accept;
      try {
        accept = serverSocket.accept();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      new Thread(new Handler(accept)).start();
    }
  }

  class Handler implements Runnable {

    private final Socket acceptSocket;


    private final ReadWriteLock electricityReadWriteLock = new ReentrantReadWriteLock();

    private final ReadWriteLock borrowerReadWriteLock = new ReentrantReadWriteLock();

    public Handler(Socket acceptSocket) {
      this.acceptSocket = acceptSocket;
    }

    @Override
    public void run() {
      try (InputStream inputStream = acceptSocket.getInputStream()) {
        try (OutputStream outputStream = acceptSocket.getOutputStream()) {
          handleRequest(outputStream, inputStream);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      try {
        acceptSocket.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private void handleRequest(OutputStream outputStream, InputStream inputStream)
        throws IOException, InterruptedException {
      try (BufferedReader bufferedReader = new BufferedReader(
          new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        String request = bufferedReader.readLine();

        String[] requestStrArray = request.split(" ", 2);
        String method = requestStrArray[0];
        String url = requestStrArray[1];

        String[] urlSplit = url.split("/");
        String category = urlSplit[1];
        if (category.equals("bike")) {
          if (method.equals(POST_METHOD)) {
            String username = urlSplit[urlSplit.length - 1];
            receiveBorrower(username);
          } else if (method.equals(GET_METHOD)) {
            checkBikeStatus(outputStream);
            Thread.sleep(3 * HOUR_AS_MILLIS);
          }
        } else if (category.equals("charger")) {
          if (urlSplit[2].equals("status")) {
            receivePlugEvent(urlSplit[3].equals("plugIn"));
          } else if (urlSplit[2].equals("energyKnots")) {
            receiveEnergyKnots(urlSplit[3]);
          }
        }
      }
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

    private void checkBikeStatus(OutputStream outputStream) throws IOException {
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
      bikeStatus.append("\n");
      borrowerReadWriteLock.readLock().unlock();
      outputStream.write(bikeStatus.toString().getBytes(StandardCharsets.UTF_8));
      outputStream.flush();
    }

    public void receiveBorrower(String username) {
      borrowerReadWriteLock.writeLock().lock();
      System.out.printf("已经上报%s\n", username);
      borrower = username;
      borrowerReadWriteLock.writeLock().unlock();
    }
  }
}
