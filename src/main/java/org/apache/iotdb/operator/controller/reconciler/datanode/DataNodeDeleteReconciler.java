package org.apache.iotdb.operator.controller.reconciler.datanode;

import org.apache.iotdb.operator.controller.reconciler.DeleteReconciler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataNodeDeleteReconciler extends DeleteReconciler {
  private static final Logger logger = LoggerFactory.getLogger(DataNodeDeleteReconciler.class);

  @Override
  public ReconcilerType getType() {
    return null;
  }

  @Override
  public void reconcile() {
    logger.info("delete datanode");
  }
}
