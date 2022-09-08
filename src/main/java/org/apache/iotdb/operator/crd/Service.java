package org.apache.iotdb.operator.crd;

public class Service {

  private String type = "ClusterIp";
  private String externalTrafficPolicy = "Cluster";

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getExternalTrafficPolicy() {
    return externalTrafficPolicy;
  }

  public void setExternalTrafficPolicy(String externalTrafficPolicy) {
    this.externalTrafficPolicy = externalTrafficPolicy;
  }

  @Override
  public String toString() {
    return "Service{"
        + "type='"
        + type
        + '\''
        + ", externalTrafficPolicy='"
        + externalTrafficPolicy
        + '\''
        + '}';
  }
}
