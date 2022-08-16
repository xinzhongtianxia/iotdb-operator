package org.apache.iotdb.operator.crd;

import java.util.Objects;

public class Service {

  private String type;
  private String externalTrafficPolicy;

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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Service service = (Service) o;
    return Objects.equals(type, service.type)
        && Objects.equals(externalTrafficPolicy, service.externalTrafficPolicy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, externalTrafficPolicy);
  }
}
