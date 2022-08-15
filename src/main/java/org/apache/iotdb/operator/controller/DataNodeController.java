package org.apache.iotdb.operator.controller;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.config.IoTDBOperatorConfig;
import org.apache.iotdb.operator.controller.reconciler.DefaultReconciler;
import org.apache.iotdb.operator.controller.reconciler.IReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeDeleteReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeStartUpReconciler;
import org.apache.iotdb.operator.controller.reconciler.datanode.DataNodeUpdateReconciler;
import org.apache.iotdb.operator.crd.DataNode;
import org.apache.iotdb.operator.event.DataNodeEvent;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class DataNodeController implements IController {
  private static final Logger logger = LoggerFactory.getLogger(DataNodeController.class);

  private final BlockingQueue<DataNodeEvent> dataNodeEvents = new LinkedBlockingQueue<>();

  private final ExecutorService dataNodeExecutor = Executors.newSingleThreadExecutor();

  private void receiveDataNodeEvent(DataNodeEvent event) {
    logger.debug("received event :\n {}", event);
    dataNodeEvents.add(event);
  }

  public void reconcileDataNode(DataNodeEvent event) throws IOException {
    IReconciler reconciler = getReconciler(event);
    logger.info("{} begin to reconcile, eventId = {}", reconciler.getType(), event.getEventId());
    reconciler.reconcile();
    logger.info("{} ended reconcile, eventId = {}", reconciler.getType(), event.getEventId());
  }

  private IReconciler getReconciler(DataNodeEvent event) {
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
  public void startDispatch() {
    dataNodeExecutor.execute(
        () -> {
          logger.info("start dispatching DataNode events...");
          while (!Thread.interrupted()) {
            DataNodeEvent event = null;
            try {
              event = dataNodeEvents.take();
              reconcileDataNode(event);
            } catch (InterruptedException e) {
              logger.warn("thread has been interrupted!", e);
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              assert event != null;
              logger.error("event handle exception, eventId = {}", event.getEventId(), e);
            }
          }
        });
  }

  @Override
  public void startWatch() {
    MixedOperation<DataNode, KubernetesResourceList<DataNode>, Resource<DataNode>> resources =
        kubernetesClient.resources(DataNode.class);
    String scope = IoTDBOperatorConfig.getInstance().getScope();
    if (scope.equals(CommonConstant.SCOPE_NAMESPACE)) {
      String namespace = IoTDBOperatorConfig.getInstance().getNamespace();
      resources.inNamespace(namespace);
    }
    resources.inform(
        new ResourceEventHandler<DataNode>() {
          @Override
          public void onAdd(DataNode obj) {
            // TODO handle synthetic add
            DataNodeEvent event = new DataNodeEvent(Action.ADDED, obj);
            receiveDataNodeEvent(event);
          }

          @Override
          public void onUpdate(DataNode oldObj, DataNode newObj) {
            DataNodeEvent event = new DataNodeEvent(Action.MODIFIED, newObj, oldObj);
            receiveDataNodeEvent(event);
          }

          @Override
          public void onDelete(DataNode obj, boolean deletedFinalStateUnknown) {
            DataNodeEvent event = new DataNodeEvent(Action.DELETED, obj);
            receiveDataNodeEvent(event);
          }
        });
  }
}
