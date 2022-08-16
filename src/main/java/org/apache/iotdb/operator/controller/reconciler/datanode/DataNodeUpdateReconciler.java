package org.apache.iotdb.operator.controller.reconciler.datanode;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.config.DataNodeConfig;
import org.apache.iotdb.operator.controller.reconciler.UpdateReconciler;
import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.DataNodeSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.DataNodeEvent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class DataNodeUpdateReconciler extends UpdateReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataNodeUpdateReconciler.class);

  private final DataNodeConfig dataNodeConfig = DataNodeConfig.getInstance();

  private final CommonStatus oldStatus;
  private final CommonStatus newStatus;

  public DataNodeUpdateReconciler(BaseEvent event) {
    super(
        ((DataNodeEvent) event).getResource().getMetadata(),
        Kind.DATA_NODE,
        ((DataNodeEvent) event).getResource().getSpec());
    oldStatus = ((DataNodeEvent) event).getOldResource().getStatus();
    newStatus = ((DataNodeEvent) event).getResource().getStatus();
    if (((DataNodeSpec) newSpec).getMode().equals(CommonConstant.DATA_NODE_MODE_STANDALONE)) {
      newSpec.setReplicas(1);
    }
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.DATA_NODE_UPDATE;
  }

  @Override
  protected Object getNewStatus() {
    return newStatus;
  }

  @Override
  protected Object getOldStatus() {
    return oldStatus;
  }

  @Override
  protected void internalUpdateConfigMap(ConfigMap configMap) throws IOException {
    String dataNodePropertyFileContent =
        configMap.getData().get(CommonConstant.DATA_NODE_PROPERTY_FILE_NAME);

    Properties properties = new Properties();
    properties.load(new StringReader(dataNodePropertyFileContent));

    Map<String, Object> newProperties =
        ((DataNodeSpec) newSpec).getIotdbConfig().getDataNodeProperties();
    newProperties.forEach(
        (k, v) -> {
          if (properties.containsKey(k)) {
            properties.replace(k, v);
          } else {
            properties.setProperty(k, (String) v);
          }
        });

    Iterator<Entry<Object, Object>> it = properties.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Object, Object> entry = it.next();
      String key = (String) entry.getKey();
      if (dataNodeConfig.getDefaultProperties().contains(key)) {
        continue;
      }
      if (!newProperties.containsKey(key)) {
        it.remove();
      }
    }
    StringWriter stringWriter = new StringWriter();
    properties.store(stringWriter, CommonConstant.GENERATE_BY_OPERATOR);
    String newDataNodePropertyFileContent = stringWriter.toString();
    LOGGER.info(
        "=========== new datanode-properties: ============ \n {}", newDataNodePropertyFileContent);
    configMap
        .getData()
        .put(CommonConstant.DATA_NODE_PROPERTY_FILE_NAME, newDataNodePropertyFileContent);
  }

  @Override
  protected boolean needUpdateConfigMap(ConfigMap configMap) throws IOException {
    String dataNodePropertyFileContent =
        configMap.getData().get(CommonConstant.DATA_NODE_PROPERTY_FILE_NAME);
    LOGGER.info("old datanode-properties: \n {}", dataNodePropertyFileContent);

    Properties properties = new Properties();
    properties.load(new StringReader(dataNodePropertyFileContent));

    boolean needUpdate = false;
    Map<String, Object> newProperties =
        ((DataNodeSpec) newSpec).getIotdbConfig().getDataNodeProperties();

    if (newProperties.size() != properties.size() - dataNodeConfig.getDefaultProperties().size()) {
      needUpdate = true;
    } else {
      for (Entry<Object, Object> entry : properties.entrySet()) {
        Object k = entry.getKey();
        Object v = entry.getValue();
        String key = (String) k;
        if (dataNodeConfig.getDefaultProperties().contains(key)) {
          continue;
        }
        if (!v.equals(newProperties.get(key))) {
          needUpdate = true;
          break;
        }
      }
    }

    if (needUpdate) {
      LOGGER.info(
          "configmap changed, old: {} \n new (without default properties) : {}",
          properties,
          newProperties);
    }

    return needUpdate;
  }
}
