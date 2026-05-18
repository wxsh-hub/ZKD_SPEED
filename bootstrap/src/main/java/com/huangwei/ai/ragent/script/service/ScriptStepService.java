/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.huangwei.ai.ragent.script.service;

import com.huangwei.ai.ragent.script.dao.entity.ScriptStepDO;

import java.util.List;

public interface ScriptStepService {

    ScriptStepDO create(Long projectId, Long screenshotId, String operationType, String paramsJson);

    ScriptStepDO update(Long id, Long screenshotId, String operationType, String paramsJson);

    void delete(Long id);

    List<ScriptStepDO> listByProject(Long projectId);

    void reorder(List<Long> orderedIds);

    ScriptStepDO extractTemplate(Long screenshotId, Long projectId, int x1, int y1, int x2, int y2, int timeoutS);
}
