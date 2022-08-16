package org.apache.iotdb.operator.controller.reconciler.datanode;

import org.apache.iotdb.operator.controller.reconciler.DeleteReconciler;
import org.apache.iotdb.operator.crd.DataNode;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.DataNodeEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataNodeDeleteReconciler extends DeleteReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataNodeDeleteReconciler.class);

  public DataNodeDeleteReconciler(BaseEvent baseEvent) {
    super(
        ((DataNodeEvent) baseEvent).getResource().getSpec(),
        ((DataNodeEvent) baseEvent).getResource().getMetadata(),
        Kind.DATA_NODE);
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.DATA_NODE_DELETE;
  }

  @Override
  protected void deleteCustomResource() {
    kubernetesClient
        .resources(DataNode.class)
        .inNamespace(metadata.getNamespace())
        .withName(metadata.getName())
        .delete();
    LOGGER.info("datanode deleted : {}", metadata.getName());
  }
}
