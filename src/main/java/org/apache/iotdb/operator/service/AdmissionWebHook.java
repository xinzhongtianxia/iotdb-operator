/*
 *     Licensed to the Apache Software Foundation (ASF) under one
 *     or more contributor license agreements.  See the NOTICE file
 *     distributed with this work for additional information
 *     regarding copyright ownership.  The ASF licenses this file
 *     to you under the Apache License, Version 2.0 (the
 *     "License"); you may not use this file except in compliance
 *     with the License.  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing,
 *     software distributed under the License is distributed on an
 *     "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *     KIND, either express or implied.  See the License for the
 *     specific language governing permissions and limitations
 *     under the License.
 */

package org.apache.iotdb.operator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionResponseBuilder;
import io.fabric8.kubernetes.api.model.admission.v1.AdmissionReview;
import io.netty.handler.ssl.SslContextBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Random;

/** A demo for admission web hook */
public class AdmissionWebHook {
  private static final Logger logger = LoggerFactory.getLogger(AdmissionWebHook.class);

  private static AdmissionWebHook INSTANCE = new AdmissionWebHook();

  public static AdmissionWebHook getInstance() {
    return INSTANCE;
  }

  private AdmissionWebHook() {}

  public void start() throws IOException {
    InputStream cert =
        getClass().getResourceAsStream(File.separator + "conf" + File.separator + "server.pem");
    InputStream key =
        getClass().getResourceAsStream(File.separator + "conf" + File.separator + "server-key.pem");

    Security.addProvider(new BouncyCastleProvider());
    DisposableServer server =
        HttpServer.create()
            .secure(spec -> spec.sslContext(SslContextBuilder.forServer(cert, key)))
            .route(routes -> routes.post("/admission", this::handleAdmission))
            .port(8077)
            .bindNow();
    logger.info("http server started, listen on 8077");
    server.onDispose().block();
  }

  private Publisher<Void> handleAdmission(HttpServerRequest request, HttpServerResponse response) {
    ObjectMapper objectMapper = new ObjectMapper();
    return response.sendString(
        request
            .receive()
            .aggregate()
            .asString()
            .flatMap(
                review -> {
                  AdmissionReview resp = new AdmissionReview();
                  String uid = "";
                  try {
                    logger.info(review);
                    AdmissionReview ar = objectMapper.readValue(review, AdmissionReview.class);
                    uid = ar.getRequest().getUid();
                    boolean allowed = new Random().nextBoolean();
                    resp.setResponse(
                        new AdmissionResponseBuilder().withUid(uid).withAllowed(allowed).build());
                  } catch (JsonProcessingException e) {
                    logger.error(e.getMessage(), e);
                    resp.setResponse(
                        new AdmissionResponseBuilder().withUid(uid).withAllowed(true).build());
                  }
                  try {
                    return Mono.just(objectMapper.writeValueAsString(resp));
                  } catch (JsonProcessingException e) {
                    return Mono.just("OK");
                  }
                }));
  }

  public static void main(String[] args) throws IOException {
    getInstance().start();
  }
}
