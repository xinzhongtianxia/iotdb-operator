package org.apache.iotdb.operator.crd;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IoTDBDataNodeConfig {
  private final Map<String, Object> dataNodeProperties = new HashMap<>();

  public Map<String, Object> getDataNodeProperties() {
    return dataNodeProperties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IoTDBDataNodeConfig that = (IoTDBDataNodeConfig) o;
    return Objects.equals(dataNodeProperties, that.dataNodeProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataNodeProperties);
  }
}
