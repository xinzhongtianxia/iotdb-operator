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

package org.apache.iotdb.operator.controller;

import org.apache.iotdb.operator.common.BaseEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

public abstract class BaseController implements IController {

  private static final Logger LOGGER = LoggerFactory.getLogger(BaseController.class);

  private BlockingQueue<BaseEvent> eventQueue = new LinkedBlockingDeque<>();

  private ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override
  public void startDispatch() {
    executor.execute(
        () -> {
          while (!Thread.interrupted()) {
            BaseEvent event = null;
            try {
              event = eventQueue.take();
              reconcile(event);
            } catch (InterruptedException e) {
              LOGGER.warn("thread has been interrupted!", e);
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              LOGGER.error("event handle exception, event = {}", event, e);
            }
          }
        });
  }

  abstract void reconcile(BaseEvent event);

  @Override
  public void receiveEvent(BaseEvent baseEvent) {
    // todo filter outdated events by ResourceVersion
    eventQueue.add(baseEvent);
  }
}
