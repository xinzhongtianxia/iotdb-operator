package org.apache.iotdb.operator.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version(DataNode.VERSION)
@Group(DataNode.GROUP)
public class DataNode
    extends io.fabric8.kubernetes.client.CustomResource<DataNodeSpec, CommonStatus>
    implements Namespaced {
  public static final String GROUP = "iotdb.apache.org";
  public static final String VERSION = "v1";
}
