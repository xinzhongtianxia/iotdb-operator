package org.apache.iotdb.operator.controller.reconciler.datanode;

import org.apache.iotdb.operator.controller.reconciler.UpdateReconciler;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.DataNodeEvent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;

import java.io.IOException;

public class DataNodeUpdateReconciler extends UpdateReconciler {
  public DataNodeUpdateReconciler(BaseEvent event) {
    super(((DataNodeEvent) event).getResource().getMetadata(), Kind.DATA_NODE);
  }

  @Override
  public ReconcilerType getType() {
    return null;
  }

  @Override
  protected Object getNewStatus() {
    return null;
  }

  @Override
  protected Object getOldStatus() {
    return null;
  }

  @Override
  protected void internalUpdateStatefulSet(StatefulSet statefulSet) {}

  @Override
  protected void internalUpdateConfigMap(ConfigMap configMap) throws IOException {}

  @Override
  protected boolean needUpdateStatefulSet(boolean isConfigMapUpdated, StatefulSet statefulSet) {
    return false;
  }

  @Override
  protected boolean needUpdateConfigMap(ConfigMap configMap) throws IOException {
    return false;
  }
}
