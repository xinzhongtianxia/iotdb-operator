package org.apache.iotdb.operator.controller.reconciler.datanode;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.config.DataNodeConfig;
import org.apache.iotdb.operator.controller.reconciler.UpdateReconciler;
import org.apache.iotdb.operator.crd.DataNode;
import org.apache.iotdb.operator.crd.DataNodeSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.CustomResourceEvent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.apache.iotdb.operator.util.OutputEventUtils;
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

  public DataNodeUpdateReconciler(CustomResourceEvent event) {
    super(event.getResource().getMetadata(), Kind.DATA_NODE, event.getResource().getSpec());

    // todo there should be an admission-validation-web-hook to prevent changing immutable configs
    // replace below hard-code by web hook
    ((DataNodeSpec) newSpec).setMode(((DataNodeSpec) event.getOldResource().getSpec()).getMode());
    if (((DataNodeSpec) newSpec).getMode().equals(CommonConstant.DATA_NODE_MODE_STANDALONE)
        && newSpec.getReplicas() != 1) {
      LOGGER.warn(
          "replicas has been set to {}, but in STANDALONE mode, replicas should always be 1 !!! Now"
              + "we set it to 1",
          newSpec.getReplicas());
      newSpec.setReplicas(1);
    } else {
      int newReplicas = newSpec.getReplicas();
      if (newReplicas < 3) {
        int oldReplica = event.getOldResource().getSpec().getReplicas();
        if (newReplicas != oldReplica) {
          LOGGER.warn(
              "replicas has been set to {}, but in STANDALONE mode, replicas should always be "
                  + "bigger than or equals to 3 !!!  Now we set it to its current value {}",
              oldReplica,
              newReplicas);
          newSpec.setReplicas(oldReplica);
        }
      }
    }
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.DATA_NODE_UPDATE;
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
            properties.replace(k, String.valueOf(v));
          } else {
            properties.setProperty(k, String.valueOf(v));
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
    Properties properties = new Properties();
    properties.load(new StringReader(dataNodePropertyFileContent));

    boolean needUpdate = false;
    Map<String, Object> newProperties =
        ((DataNodeSpec) newSpec).getIotdbConfig().getDataNodeProperties();

    int oldPropertySize = properties.size() - dataNodeConfig.getDefaultProperties().size();
    if (((DataNodeSpec) newSpec).getMode().equals(CommonConstant.DATA_NODE_MODE_STANDALONE)) {
      oldPropertySize += 1;
    }
    if (newProperties.size() != oldPropertySize) {
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

  @Override
  protected void patchPartitionToAnnotations(int rollingUpdatePartition) {

    DataNode dataNode =
        kubernetesClient
            .resources(DataNode.class)
            .inNamespace(meta.getNamespace())
            .withName(meta.getName())
            .require();
    String currentPartition =
        dataNode
            .getMetadata()
            .getAnnotations()
            .getOrDefault(CommonConstant.ANNOTATION_KEY_PARTITION, "0");

    if (Integer.parseInt(currentPartition) != rollingUpdatePartition) {
      dataNode
          .getMetadata()
          .getAnnotations()
          .put(CommonConstant.ANNOTATION_KEY_PARTITION, String.valueOf(rollingUpdatePartition));
      kubernetesClient.resource(dataNode).inNamespace(meta.getNamespace()).replace();

      OutputEventUtils.sendEvent(
          Kind.DATA_NODE,
          OutputEventUtils.EVENT_TYPE_NORMAL,
          "Update Partition",
          meta,
          "update DataNode's partition to " + rollingUpdatePartition,
          "Updated",
          Kind.DATA_NODE.getName());
    }
  }
}
