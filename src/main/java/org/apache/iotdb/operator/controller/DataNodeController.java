package org.apache.iotdb.operator.controller;

import org.apache.iotdb.operator.controller.reconciler.DefaultReconciler;
import org.apache.iotdb.operator.controller.reconciler.IReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeDeleteReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeStartUpReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeUpdateReconciler;
import org.apache.iotdb.operator.crd.DataNode;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.CustomResourceEvent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watcher.Action;

public class DataNodeController extends AbstractCustomResourceController {

  public DataNodeController() {
    super(Kind.DATA_NODE);
  }

  @Override
  protected IReconciler getReconciler(CustomResourceEvent event) {
    Action action = event.getAction();
    switch (action) {
      case ADDED:
        return new DataNodeStartUpReconciler(event);
      case DELETED:
        return new DataNodeDeleteReconciler(event);
      case MODIFIED:
        return new DataNodeUpdateReconciler(event);
      default:
        return new DefaultReconciler(event);
    }
  }

  @Override
  protected Class<? extends HasMetadata> getResourceType() {
    return DataNode.class;
  }
}
