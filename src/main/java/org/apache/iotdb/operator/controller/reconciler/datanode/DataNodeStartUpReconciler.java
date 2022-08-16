package org.apache.iotdb.operator.controller.reconciler.datanode;

import org.apache.iotdb.operator.common.CommonConstant;
import org.apache.iotdb.operator.common.EnvKey;
import org.apache.iotdb.operator.config.ConfigNodeConfig;
import org.apache.iotdb.operator.config.DataNodeConfig;
import org.apache.iotdb.operator.controller.reconciler.StartUpReconciler;
import org.apache.iotdb.operator.crd.DataNodeSpec;
import org.apache.iotdb.operator.crd.Kind;
import org.apache.iotdb.operator.event.BaseEvent;
import org.apache.iotdb.operator.event.DataNodeEvent;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataNodeStartUpReconciler extends StartUpReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataNodeStartUpReconciler.class);
  private final DataNodeConfig dataNodeConfig = DataNodeConfig.getInstance();

  private final DataNodeSpec dataNodeSpec;

  public DataNodeStartUpReconciler(BaseEvent event) {
    super(
        ((DataNodeEvent) event).getResource().getSpec(),
        ((DataNodeEvent) event).getResource().getMetadata(),
        event.getKind(),
        event.getEventId());
    this.dataNodeSpec = ((DataNodeEvent) event).getResource().getSpec();
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
    LOGGER.info("========== iotdb-datanode.properties : ============ \n {}", dataNodeProperties);
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
    LOGGER.info("========== iotdb-datanode.properties : ============ \n {}", restConfig);
    configFiles.put(CommonConstant.DATA_NODE_REST_PROPERTY_FILE_NAME, restConfig);

    // read init script into ConfigMap
    String scriptContent =
        IOUtils.toString(
            getClass()
                .getResourceAsStream(
                    File.separator + CommonConstant.DATA_NODE_INIT_SCRIPT_FILE_NAME),
            Charset.defaultCharset());
    LOGGER.info("========== datanode-init.sh : ============ : \n {}", scriptContent);
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

      defaultConfigMap.put(CommonConstant.CONFIG_NODE_TARGET_CONFIG_NODE, targetConfigNode);
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
            .withName("data-region-consensus")
            .withContainerPort(dataNodeConfig.getDataRegionConsensusPort())
            .build();

    ContainerPort schemaRegionConsensusPort =
        new ContainerPortBuilder()
            .withName("schema-region-consensus")
            .withContainerPort(dataNodeConfig.getSchemaRegionConsensusPort())
            .build();

    ContainerPort mppDataExchangePort =
        new ContainerPortBuilder()
            .withName("mpp-data-exchange")
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
          .withFailureThreshold(60)
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
        .withFailureThreshold(60)
        .build();
  }

  @Override
  protected void createServices() {

    String serviceType = dataNodeSpec.getService().getType();

    // port for coordinator's communication between cluster nodes.
    int internalPort = dataNodeConfig.getInternalPort();
    ServicePort internalServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(internalPort)
            .withPort(internalPort)
            .withName("internal")
            .build();

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

    // port for outputting metrics data
    int metricPort = dataNodeConfig.getMetricPort();
    ServicePort metricServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(metricPort)
            .withPort(metricPort)
            .withName("metric")
            .build();

    // port for consensus's communication for data region between cluster nodes.
    int dataRegionConsensusPort = dataNodeConfig.getDataRegionConsensusPort();
    ServicePort dataRegionConsensusServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(dataRegionConsensusPort)
            .withPort(dataRegionConsensusPort)
            .withName("data-region-consensus")
            .build();

    // port for consensus's communication for schema region between cluster nodes.
    int schemaRegionConsensusPort = dataNodeConfig.getSchemaRegionConsensusPort();
    ServicePort schemaRegionConsensusServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(schemaRegionConsensusPort)
            .withPort(schemaRegionConsensusPort)
            .withName("schema-region-consensus")
            .build();

    // por for data exchange in mpp framework
    int mppDataExchangePort = dataNodeConfig.getMppDataExchangePort();
    ServicePort mppDataExchangeServicePort =
        new ServicePortBuilder()
            .withNewTargetPort(mppDataExchangePort)
            .withPort(mppDataExchangePort)
            .withName("mpp-data-exchange")
            .build();

    if (serviceType.equals(CommonConstant.SERVICE_TYPE_NODE_PORT)) {
      rpcServicePort.setNodePort(dataNodeConfig.getRpcNodePort());
      restServicePort.setNodePort(dataNodeConfig.getRestNodePort());
    }

    // for consensus among data nodes
    Service internalService =
        new ServiceBuilder()
            .withNewMetadata()
            .withName(subResourceName)
            .withNamespace(metadata.getNamespace())
            .withLabels(getLabels())
            .endMetadata()
            .withNewSpec()
            .withSelector(getLabels())
            .withPorts(
                internalServicePort,
                dataRegionConsensusServicePort,
                schemaRegionConsensusServicePort,
                mppDataExchangeServicePort,
                metricServicePort)
            .withClusterIP("None")
            .endSpec()
            .build();

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

    // set service type to NodePort if needed
    if (serviceType.equals(CommonConstant.SERVICE_TYPE_NODE_PORT)) {
      externalService.getSpec().setType(CommonConstant.SERVICE_TYPE_NODE_PORT);
    }

    // set external traffic policy
    if (dataNodeSpec
        .getService()
        .getExternalTrafficPolicy()
        .equals(CommonConstant.SERVICE_EXTERNAL_TRAFFIC_POLICY_LOCAL)) {
      externalService
          .getSpec()
          .setExternalTrafficPolicy(CommonConstant.SERVICE_EXTERNAL_TRAFFIC_POLICY_LOCAL);
    }

    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .resource(internalService)
        .create();
    kubernetesClient
        .services()
        .inNamespace(metadata.getNamespace())
        .resource(externalService)
        .create();
  }
}
