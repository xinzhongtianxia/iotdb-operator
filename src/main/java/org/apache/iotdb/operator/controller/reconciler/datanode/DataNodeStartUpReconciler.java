package org.apache.iotdb.operator.controller.reconciler.datanode;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.common.EnvKey;
import org.apache.iotdb.operator.config.ConfigNodeConfig;
import org.apache.iotdb.operator.config.DataNodeConfig;
import org.apache.iotdb.operator.controller.reconciler.StartUpReconciler;
import org.apache.iotdb.operator.crd.DataNodeSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.CustomResourceEvent;
import org.apache.iotdb.operator.util.OutputEventUtils;
import org.apache.iotdb.operator.util.ReconcilerUtils;

import org.apache.commons.io.IOUtils;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataNodeStartUpReconciler extends StartUpReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataNodeStartUpReconciler.class);
  private final DataNodeConfig dataNodeConfig = DataNodeConfig.getInstance();

  private final DataNodeSpec dataNodeSpec;

  public DataNodeStartUpReconciler(CustomResourceEvent event) {
    super(
        event.getResource().getSpec(),
        event.getResource().getMetadata(),
        event.getKind(),
        event.getEventId());
    this.dataNodeSpec = (DataNodeSpec) event.getResource().getSpec();
    if (dataNodeSpec.getMode().equals(CommonConstant.DATA_NODE_MODE_STANDALONE)) {
      dataNodeSpec.setReplicas(1);
    }
  }

  @Override
  public ReconcilerType getType() {
    return ReconcilerType.DATA_NODE_STARTUP;
  }

  @Override
  protected boolean checkInitConditions() {
    if (dataNodeSpec.getMode().equals(CommonConstant.DATA_NODE_MODE_STANDALONE)) {
      return true;
    }
    // todo check if confignode is running
    return false;
  }

  @Override
  protected Map<String, String> createConfigFiles() throws IOException {
    Map<String, String> configFiles = new HashMap<>();
    DataNodeSpec dataNodeSpec = (DataNodeSpec) commonSpec;

    // construct iotdb-datanode.properties
    Map<String, Object> properties = new HashMap<>();
    if (dataNodeSpec.getIotdbConfig() != null) {
      Map<String, Object> dataNodeProperties =
          dataNodeSpec.getIotdbConfig().getDataNodeProperties();
      properties.putAll(dataNodeProperties);
    }

    Map<String, Object> dataNodeDefaultConfigs =
        constructDefaultDataNodeConfigs(subResourceName, metadata.getNamespace());
    properties.putAll(dataNodeDefaultConfigs);

    StringBuilder sb = new StringBuilder(CommonConstant.GENERATE_BY_OPERATOR);
    properties.forEach(
        (k, v) -> {
          sb.append(CommonConstant.LINES_SEPARATOR)
              .append(k)
              .append("=")
              .append(v)
              .append(CommonConstant.LINES_SEPARATOR);
        });

    String dataNodeProperties = sb.toString();
    LOGGER.debug("========== iotdb-datanode.properties : ============ \n {}", dataNodeProperties);
    configFiles.put(CommonConstant.DATA_NODE_PROPERTY_FILE_NAME, dataNodeProperties);

    // construct iotdb-rest.properties
    StringBuilder restConfigSb = new StringBuilder(CommonConstant.GENERATE_BY_OPERATOR);
    restConfigSb
        .append(CommonConstant.LINES_SEPARATOR)
        .append("enable_rest_service=")
        .append(true)
        .append(CommonConstant.LINES_SEPARATOR)
        .append("rest_service_port=")
        .append(dataNodeConfig.getRestPort());
    String restConfig = restConfigSb.toString();
    LOGGER.debug("========== iotdb-rest.properties : ============ \n {}", restConfig);
    configFiles.put(CommonConstant.DATA_NODE_REST_PROPERTY_FILE_NAME, restConfig);

    // read init script into ConfigMap
    String scriptContent =
        IOUtils.toString(
            getClass()
                .getResourceAsStream(
                    File.separator
                        + "conf"
                        + File.separator
                        + CommonConstant.DATA_NODE_INIT_SCRIPT_FILE_NAME),
            Charset.defaultCharset());
    LOGGER.debug("========== datanode-init.sh : ============ : \n {}", scriptContent);
    configFiles.put(CommonConstant.DATA_NODE_INIT_SCRIPT_FILE_NAME, scriptContent);
    return configFiles;
  }

  private Map<String, Object> constructDefaultDataNodeConfigs(String name, String namespace) {
    Map<String, Object> defaultConfigMap = new HashMap<>();

    // set confignode address when running in cluster mode
    if (dataNodeSpec.getMode().equals(CommonConstant.DATA_NODE_MODE_CLUSTER)) {
      String configNodeName = ((DataNodeSpec) commonSpec).getConfignodeName();
      String targetConfigNode =
          configNodeName
              + "-"
              + Kind.CONFIG_NODE.toString().toLowerCase()
              + CommonConstant.SERVICE_SUFFIX_EXTERNAL
              + "."
              + namespace
              + ".svc.cluster.local:"
              + ConfigNodeConfig.getInstance().getInternalPort();

      defaultConfigMap.put(CommonConstant.DATA_NODE_TARGET_CONFIG_NODE, targetConfigNode);
    }

    // set addresses and ports
    defaultConfigMap.put(
        CommonConstant.DATA_NODE_INTERNAL_ADDRESS, dataNodeConfig.getInternalAddress());
    defaultConfigMap.put(CommonConstant.DATA_NODE_RPC_ADDRESS, dataNodeConfig.getRpcAddress());

    defaultConfigMap.put(CommonConstant.DATA_NODE_INTERNAL_PORT, dataNodeConfig.getInternalPort());
    defaultConfigMap.put(CommonConstant.DATA_NODE_RPC_PORT, dataNodeConfig.getRpcPort());
    defaultConfigMap.put(
        CommonConstant.DATA_NODE_MPP_DATA_EXCHANGE_PORT, dataNodeConfig.getMppDataExchangePort());
    defaultConfigMap.put(
        CommonConstant.DATA_NODE_DATA_REGION_CONSENSUS_PORT,
        dataNodeConfig.getDataRegionConsensusPort());
    defaultConfigMap.put(
        CommonConstant.DATA_NODE_SCHEMA_REGION_CONSENSUS_PORT,
        dataNodeConfig.getSchemaRegionConsensusPort());

    return defaultConfigMap;
  }

  @Override
  protected Map<String, String> getLabels() {
    Map<String, String> labels = dataNodeConfig.getAdditionalLabels();
    labels.put(CommonConstant.LABEL_KEY_APP_NAME, subResourceName);
    return labels;
  }

  @Override
  protected List<EnvVar> getEnvs() {
    List<EnvVar> envVars = ReconcilerUtils.computeJVMMemory(commonSpec.getLimits());
    envVars.add(
        new EnvVarBuilder()
            .withName(EnvKey.IOTDB_DATA_NODE_MODE.name())
            .withValue(dataNodeSpec.getMode())
            .build());
    LOGGER.info("envs : {}", envVars);
    return envVars;
  }

  @Override
  protected String getStartArgs() {
    return dataNodeConfig.getStartArgs();
  }

  @Override
  protected List<ContainerPort> createContainerPort() {
    ContainerPort consensusPort =
        new ContainerPortBuilder()
            .withName("internal")
            .withContainerPort(dataNodeConfig.getInternalPort())
            .build();

    ContainerPort rpcPort =
        new ContainerPortBuilder()
            .withName("rpc")
            .withContainerPort(dataNodeConfig.getRpcPort())
            .build();

    ContainerPort restPort =
        new ContainerPortBuilder()
            .withName("rest")
            .withContainerPort(dataNodeConfig.getRestPort())
            .build();

    ContainerPort metricPort =
        new ContainerPortBuilder()
            .withName("metric")
            .withContainerPort(dataNodeConfig.getMetricPort())
            .build();

    ContainerPort dataRegionConsensusPort =
        new ContainerPortBuilder()
            .withName("data-region")
            .withContainerPort(dataNodeConfig.getDataRegionConsensusPort())
            .build();

    ContainerPort schemaRegionConsensusPort =
        new ContainerPortBuilder()
            .withName("schema-region")
            .withContainerPort(dataNodeConfig.getSchemaRegionConsensusPort())
            .build();

    ContainerPort mppDataExchangePort =
        new ContainerPortBuilder()
            .withName("mpp-exchange")
            .withContainerPort(dataNodeConfig.getMppDataExchangePort())
            .build();

    return Arrays.asList(
        consensusPort,
        rpcPort,
        restPort,
        metricPort,
        dataRegionConsensusPort,
        schemaRegionConsensusPort,
        mppDataExchangePort);
  }

  @Override
  protected Probe createStartupProbe() {
    if (dataNodeSpec.getMode().equals(CommonConstant.DATA_NODE_MODE_STANDALONE)) {
      return new ProbeBuilder()
          .withNewTcpSocket()
          .withNewPort(dataNodeConfig.getRpcPort())
          .endTcpSocket()
          .withPeriodSeconds(3)
          .withFailureThreshold(60)
          .build();
    }

    // todo create probe for cluster mode
    return null;
  }

  @Override
  protected Probe createReadinessProbe() {
    if (dataNodeSpec.getMode().equals(CommonConstant.DATA_NODE_MODE_STANDALONE)) {
      return new ProbeBuilder()
          .withNewHttpGet()
          .withPath("/ping")
          .withNewPort(dataNodeConfig.getRestPort())
          .endHttpGet()
          .withPeriodSeconds(3)
          .withFailureThreshold(3)
          .build();
    }

    // todo create probe for cluster mode
    return null;
  }

  @Override
  protected Probe createLivenessProbe() {
    return new ProbeBuilder()
        .withNewTcpSocket()
        .withNewPort(dataNodeConfig.getRpcPort())
        .endTcpSocket()
        .withPeriodSeconds(3)
        .withFailureThreshold(10)
        .build();
  }

  @Override
  public Map<String, Service> createServices() {

    // port for outputting metrics data
    int metricPort = dataNodeConfig.getMetricPort();
    ServicePort metricServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(metricPort)
            .withPort(metricPort)
            .withName("metric")
            .build();

    List<ServicePort> internalServicePorts = new ArrayList<>();
    internalServicePorts.add(metricServicePort);

    if (dataNodeSpec.getMode().equals(CommonConstant.DATA_NODE_MODE_CLUSTER)) {
      // port for coordinator's communication between cluster nodes.
      int internalPort = dataNodeConfig.getInternalPort();
      ServicePort internalServicePort =
          new ServicePortBuilder()
              .withNewTargetPort(internalPort)
              .withPort(internalPort)
              .withName("internal")
              .build();

      // port for consensus's communication for data region between cluster nodes.
      int dataRegionConsensusPort = dataNodeConfig.getDataRegionConsensusPort();
      ServicePort dataRegionConsensusServicePort =
          new ServicePortBuilder()
              .withNewTargetPort(dataRegionConsensusPort)
              .withPort(dataRegionConsensusPort)
              .withName("data-region")
              .build();

      // port for consensus's communication for schema region between cluster nodes.
      int schemaRegionConsensusPort = dataNodeConfig.getSchemaRegionConsensusPort();
      ServicePort schemaRegionConsensusServicePort =
          new ServicePortBuilder()
              .withNewTargetPort(schemaRegionConsensusPort)
              .withPort(schemaRegionConsensusPort)
              .withName("schema-region")
              .build();

      // por for data exchange in mpp framework
      int mppDataExchangePort = dataNodeConfig.getMppDataExchangePort();
      ServicePort mppDataExchangeServicePort =
          new ServicePortBuilder()
              .withNewTargetPort(mppDataExchangePort)
              .withPort(mppDataExchangePort)
              .withName("mpp-exchange")
              .build();

      internalServicePorts.add(internalServicePort);
      internalServicePorts.add(mppDataExchangeServicePort);
      internalServicePorts.add(dataRegionConsensusServicePort);
      internalServicePorts.add(schemaRegionConsensusServicePort);
    }

    // for internal services
    Service internalService =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(subResourceName)
            .withNamespace(metadata.getNamespace())
            .withLabels(getLabels())
            .endMetadata()
            .withNewSpec()
            .withSelector(getLabels())
            .withPorts(internalServicePorts)
            .withClusterIP("None")
            .endSpec()
            .build();
    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .resource(internalService)
        .createOrReplace();

    String serviceType = dataNodeSpec.getService().getType();

    // port for external data insert and query
    int rpcPort = dataNodeConfig.getRpcPort();
    ServicePort rpcServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(rpcPort)
            .withPort(rpcPort)
            .withName("rpc")
            .build();

    // port for rest api
    int restPort = dataNodeConfig.getRestPort();
    ServicePort restServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(restPort)
            .withPort(restPort)
            .withName("rest")
            .build();

    if (serviceType.equals(CommonConstant.SERVICE_TYPE_NODE_PORT)
        || serviceType.equals(CommonConstant.SERVICE_TYPE_LOAD_BALANCER)) {
      rpcServicePort.setNodePort(dataNodeConfig.getRpcNodePort());
      restServicePort.setNodePort(dataNodeConfig.getRestNodePort());
    }

    // for rpc from datanode and metrics from prometheus
    Service externalService =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(subResourceName + CommonConstant.SERVICE_SUFFIX_EXTERNAL)
            .withNamespace(metadata.getNamespace())
            .withLabels(getLabels())
            .endMetadata()
            .withNewSpec()
            .withSelector(getLabels())
            .withPorts(rpcServicePort, restServicePort)
            .endSpec()
            .build();

    // set external traffic policy
    if (dataNodeSpec
        .getService()
        .getExternalTrafficPolicy()
        .equals(CommonConstant.SERVICE_EXTERNAL_TRAFFIC_POLICY_LOCAL)) {
      externalService
          .getSpec()
          .setExternalTrafficPolicy(CommonConstant.SERVICE_EXTERNAL_TRAFFIC_POLICY_LOCAL);
    }

    // set service type to NodePort if needed
    if (serviceType.equals(CommonConstant.SERVICE_TYPE_NODE_PORT)
        || serviceType.equals(CommonConstant.SERVICE_TYPE_LOAD_BALANCER)) {
      externalService.getSpec().setType(serviceType);

      // if there is already a service with the specific node port, delete it first
      Service service =
          kubernetesClient
              .services()
              .inNamespace(metadata.getNamespace())
              .resource(externalService)
              .get();
      if (service != null) {
        LOGGER.warn(
            "there is already a service running, may be caused by last failed deploy, delete it now! service = {}",
            service);
        kubernetesClient
            .services()
            .inNamespace(metadata.getNamespace())
            .resource(externalService)
            .delete();
      }
    }

    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .resource(externalService)
        .createOrReplace();

    OutputEventUtils.sendEvent(
        kind,
        OutputEventUtils.EVENT_TYPE_NORMAL,
        "CreateService",
        metadata,
        "Successfully created Service "
            + subResourceName
            + " and "
            + subResourceName
            + CommonConstant.SERVICE_SUFFIX_EXTERNAL,
        "Created",
        Kind.SERVICE.getName());
    Map<String, Service> serviceMap = new HashMap<>(2);
    serviceMap.put(internalService.getMetadata().getName(), internalService);
    serviceMap.put(externalService.getMetadata().getName(), externalService);
    return serviceMap;
  }
}
