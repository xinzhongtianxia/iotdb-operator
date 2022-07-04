package org.apache.iotdb.operator.controller.reconciler.datanode;

import org.apache.iotdb.operator.controller.reconciler.StartUpReconciler;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.DataNodeEvent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class DataNodeStartUpReconciler extends StartUpReconciler {
  private static final Logger logger = LoggerFactory.getLogger(DataNodeStartUpReconciler.class);

  public DataNodeStartUpReconciler(BaseEvent event) {
    super(
        ((DataNodeEvent) event).getResource().getSpec(),
        ((DataNodeEvent) event).getResource().getMetadata(),
        event.getKind(),
        event.getEventId());
  }

  @Override
  public ReconcilerType getType() {
    return null;
  }

  @Override
  protected Map<String, String> createConfigFiles() throws IOException {
    return null;
  }

  @Override
  protected Map<String, String> getLabels() {
    return null;
  }

  @Override
  protected StatefulSet createStatefulSet(ConfigMap configMap) {
    logger.info("create datanode statefulset");
    return null;
  }

  @Override
  protected void createServices() {
    logger.info("create datanode service");
  }
}
