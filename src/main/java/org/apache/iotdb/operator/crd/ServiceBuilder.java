package org.apache.iotdb.operator.crd;

public class ServiceBuilder {
  private Service service;

  public ServiceBuilder() {
    service = new Service();
  }

  public ServiceBuilder withType(String type) {
    service.setType(type);
    return this;
  }

  public ServiceBuilder withExternalTrafficPolicy(String externalTrafficPolicy) {
    service.setExternalTrafficPolicy(externalTrafficPolicy);
    return this;
  }

  public Service build() {
    return service;
  }
}
