package org.apache.iotdb.operator.crd;

public class DataNodeSpec extends CommonSpec {

  private IoTDBConfigNodeConfig ioTDBConfigNodeConfig;

  private Service service;

  public IoTDBConfigNodeConfig getIoTDBConfigNodeConfig() {
    return ioTDBConfigNodeConfig;
  }

  public void setIoTDBConfigNodeConfig(IoTDBConfigNodeConfig ioTDBConfigNodeConfig) {
    this.ioTDBConfigNodeConfig = ioTDBConfigNodeConfig;
  }

  public Service getService() {
    return service;
  }

  public void setService(Service service) {
    this.service = service;
  }
}
