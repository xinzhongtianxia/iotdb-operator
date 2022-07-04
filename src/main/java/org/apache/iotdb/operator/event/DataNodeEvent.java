package org.apache.iotdb.operator.event;

import org.apache.iotdb.operator.crd.CommonStatus;
import org.apache.iotdb.operator.crd.DataNodeSpec;
import org.apache.iotdb.operator.crd.Kind;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher.Action;

public class DataNodeEvent extends BaseEvent {

  private final CustomResource<DataNodeSpec, CommonStatus> oldResource;

  private final CustomResource<DataNodeSpec, CommonStatus> resource;

  public DataNodeEvent(
      Action action,
      CustomResource<DataNodeSpec, CommonStatus> resource,
      CustomResource<DataNodeSpec, CommonStatus> oldResource) {
    super(action, Kind.DATA_NODE, resource.getMetadata().getResourceVersion());
    this.resource = resource;
    this.oldResource = oldResource;
  }

  public DataNodeEvent(Action action, CustomResource<DataNodeSpec, CommonStatus> resource) {
    this(action, resource, null);
  }

  public CustomResource<DataNodeSpec, CommonStatus> getOldResource() {
    return oldResource;
  }

  public CustomResource<DataNodeSpec, CommonStatus> getResource() {
    return resource;
  }
}
