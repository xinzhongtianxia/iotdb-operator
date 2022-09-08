package org.apache.iotdb.operator.crd;

import org.apache.iotdb.operator.config.DataNodeConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IoTDBDataNodeConfig {
  private final Map<String, Object> dataNodeProperties = new HashMap<>();

  public Map<String, Object> getDataNodeProperties() {
    return dataNodeProperties;
  }

  public void setDataNodeProperties(String name, Object value) {
    // user need not to set some default configurations that should be set by IoTDB-Operator
    if (DataNodeConfig.getInstance().getDefaultProperties().contains(name)) {
      return;
    }
    dataNodeProperties.put(name, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IoTDBDataNodeConfig that = (IoTDBDataNodeConfig) o;
    return Objects.equals(dataNodeProperties, that.dataNodeProperties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataNodeProperties);
  }

  @Override
  public String toString() {
    return "IoTDBDataNodeConfig{" + "dataNodeProperties=" + dataNodeProperties + '}';
  }
}
