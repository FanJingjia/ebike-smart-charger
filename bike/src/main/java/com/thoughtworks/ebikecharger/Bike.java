package com.thoughtworks.ebikecharger;

public class Bike {

  private String id;

  // status分为闲置和使用
  private String status;

  // 使用者名称=使用者名称/空字符串
  private String username;

  public Bike(String id) {
    this.id = id;
  }

  public void plugIn() {
    setStatus("plugIn");
  }

  public void plugOut(String username) {
    setStatus("plugOut");
    setUsername(username);
  }

  public String getId() {
    return id;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setUsername(String username) {
    this.username = username;
  }
}
