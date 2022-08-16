package org.apache.iotdb.operator.crd;

public class DataNodeSpec extends CommonSpec {

  private IoTDBDataNodeConfig iotdbConfig;

  private Service service;

  private String mode;

  private String confignodeName;

  public IoTDBDataNodeConfig getIotdbConfig() {
    return iotdbConfig;
  }

  public void setIotdbConfig(IoTDBDataNodeConfig iotdbConfig) {
    this.iotdbConfig = iotdbConfig;
  }

  public String getConfignodeName() {
    return confignodeName;
  }

  public void setConfignodeName(String confignodeName) {
    this.confignodeName = confignodeName;
  }

  public Service getService() {
    return service;
  }

  public void setService(Service service) {
    this.service = service;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }
}
