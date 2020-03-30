/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.exception.NotValidException;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.DecisionDefinitionQuery;
import org.camunda.bpm.engine.repository.DecisionRequirementsDefinition;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class DecisionDefinitionQueryTest {

  protected static final String DMN_ONE_RESOURCE = "org/camunda/bpm/engine/test/repository/one.dmn";
  protected static final String DMN_TWO_RESOURCE = "org/camunda/bpm/engine/test/repository/two.dmn";
  protected static final String DMN_THREE_RESOURCE = "org/camunda/bpm/engine/test/api/repository/three_.dmn";

  protected static final String DRD_SCORE_RESOURCE = "org/camunda/bpm/engine/test/dmn/deployment/drdScore.dmn11.xml";
  protected static final String DRD_DISH_RESOURCE = "org/camunda/bpm/engine/test/dmn/deployment/drdDish.dmn11.xml";

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);
  protected ExpectedException exceptionRule = ExpectedException.none();

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule).around(exceptionRule);

  protected RepositoryService repositoryService;

  protected String decisionRequirementsDefinitionId;
  protected String firstDeploymentId;
  protected String secondDeploymentId;
  protected String thirdDeploymentId;

  @Before
  public void init() {
    repositoryService = engineRule.getRepositoryService();

    firstDeploymentId = testRule.deploy(DMN_ONE_RESOURCE, DMN_TWO_RESOURCE).getId();
    secondDeploymentId = testRule.deploy(DMN_ONE_RESOURCE).getId();
    thirdDeploymentId = testRule.deploy(DMN_THREE_RESOURCE).getId();
  }

  @Test
  public void decisionDefinitionProperties() {
    List<DecisionDefinition> decisionDefinitions = repositoryService
      .createDecisionDefinitionQuery()
      .orderByDecisionDefinitionName().asc()
      .orderByDecisionDefinitionVersion().asc()
      .orderByDecisionDefinitionCategory()
      .asc()
      .list();

    DecisionDefinition decisionDefinition = decisionDefinitions.get(0);
    assertThat(decisionDefinition.getKey()).isEqualTo("one");
    assertThat(decisionDefinition.getName()).isEqualTo("One");
    assertThat(decisionDefinition.getId()).startsWith("one:1");
    assertThat(decisionDefinition.getCategory()).isEqualTo("Examples");
    assertThat(decisionDefinition.getVersion()).isEqualTo(1);
    assertThat(decisionDefinition.getResourceName()).isEqualTo("org/camunda/bpm/engine/test/repository/one.dmn");
    assertThat(decisionDefinition.getDeploymentId()).isEqualTo(firstDeploymentId);

    decisionDefinition = decisionDefinitions.get(1);
    assertThat(decisionDefinition.getKey()).isEqualTo("one");
    assertThat(decisionDefinition.getName()).isEqualTo("One");
    assertThat(decisionDefinition.getId()).startsWith("one:2");
    assertThat(decisionDefinition.getCategory()).isEqualTo("Examples");
    assertThat(decisionDefinition.getVersion()).isEqualTo(2);
    assertThat(decisionDefinition.getResourceName()).isEqualTo("org/camunda/bpm/engine/test/repository/one.dmn");
    assertThat(decisionDefinition.getDeploymentId()).isEqualTo(secondDeploymentId);

    decisionDefinition = decisionDefinitions.get(2);
    assertThat(decisionDefinition.getKey()).isEqualTo("two");
    assertThat(decisionDefinition.getName()).isEqualTo("Two");
    assertThat(decisionDefinition.getId()).startsWith("two:1");
    assertThat(decisionDefinition.getCategory()).isEqualTo("Examples2");
    assertThat(decisionDefinition.getVersion()).isEqualTo(1);
    assertThat(decisionDefinition.getResourceName()).isEqualTo("org/camunda/bpm/engine/test/repository/two.dmn");
    assertThat(decisionDefinition.getDeploymentId()).isEqualTo(firstDeploymentId);
  }

  @Test
  public void queryByDecisionDefinitionIds() {
    // empty list
    assertThat(repositoryService.createDecisionDefinitionQuery().decisionDefinitionIdIn("a", "b").list()).isEmpty();

    // collect all ids
    List<DecisionDefinition> decisionDefinitions = repositoryService.createDecisionDefinitionQuery().list();
    List<String> ids = new ArrayList<String>();
    for (DecisionDefinition decisionDefinition : decisionDefinitions) {
      ids.add(decisionDefinition.getId());
    }

    decisionDefinitions = repositoryService.createDecisionDefinitionQuery()
      .decisionDefinitionIdIn(ids.toArray(new String[ids.size()]))
      .list();

    assertThat(decisionDefinitions).hasSize(ids.size());
    for (DecisionDefinition decisionDefinition : decisionDefinitions) {
      assertThat(ids).contains(decisionDefinition.getId()).withFailMessage("Expected to find decision definition " + decisionDefinition);
    }
  }

  @Test
  public void queryByDeploymentId() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.deploymentId(firstDeploymentId);

    verifyQueryResults(query, 2);
  }

  @Test
  public void queryByInvalidDeploymentId() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

   query
     .deploymentId("invalid");

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.deploymentId(null);
  }

  @Test
  public void queryByDeploymentTimeAfter() {
    List<Deployment> deployments = repositoryService.createDeploymentQuery().list();

    for (Deployment deployment : deployments) {
      List<DecisionDefinition> decisionDefinitions = repositoryService.createDecisionDefinitionQuery().deployedAfter(deployment.getDeploymentTime()).list();
      for (DecisionDefinition decisionDefinition : decisionDefinitions) {
        Deployment singleDeployment = repositoryService.createDeploymentQuery().deploymentId(decisionDefinition.getDeploymentId()).singleResult();
        // all results should have a later deployment time than the one used in the query
        assertThat(singleDeployment.getDeploymentTime()).isAfter(deployment.getDeploymentTime());
      }
    }
  }

  @Test
  public void queryByDeploymentTimeAt() {
    Deployment firstDeployment = repositoryService.createDeploymentQuery().deploymentId(firstDeploymentId).singleResult();
    Deployment secondDeployment = repositoryService.createDeploymentQuery().deploymentId(secondDeploymentId).singleResult();
    Deployment thirdDeployment = repositoryService.createDeploymentQuery().deploymentId(thirdDeploymentId).singleResult();

    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery().deployedAt(firstDeployment.getDeploymentTime());
    verifyQueryResults(query, 2);

    query = repositoryService.createDecisionDefinitionQuery().deployedAt(secondDeployment.getDeploymentTime());
    verifyQueryResults(query, 1);

    query = repositoryService.createDecisionDefinitionQuery().deployedAt(thirdDeployment.getDeploymentTime());
    verifyQueryResults(query, 1);
  }

  @Test
  public void queryByName() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionName("Two");

    verifyQueryResults(query, 1);

    query.decisionDefinitionName("One");

    verifyQueryResults(query, 2);
  }

  @Test
  public void queryByInvalidName() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionName("invalid");

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionName(null);
  }

  @Test
  public void queryByNameLike() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionNameLike("%w%");

    verifyQueryResults(query, 1);

    query.decisionDefinitionNameLike("%z\\_");

    verifyQueryResults(query, 1);
  }

  @Test
  public void queryByInvalidNameLike() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionNameLike("%invalid%");

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionNameLike(null);
  }

  @Test
  public void queryByResourceNameLike() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionResourceNameLike("%ree%");

    verifyQueryResults(query, 1);

    query.decisionDefinitionResourceNameLike("%ee\\_%");

    verifyQueryResults(query, 1);
  }

  @Test
  public void queryByInvalidNResourceNameLike() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionResourceNameLike("%invalid%");

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionNameLike(null);
  }

  @Test
  public void queryByKey() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    // decision one
    query.decisionDefinitionKey("one");

    verifyQueryResults(query, 2);

    // decision two
    query.decisionDefinitionKey("two");

    verifyQueryResults(query, 1);
  }

  @Test
  public void queryByInvalidKey() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionKey("invalid");

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionKey(null);
  }

  @Test
  public void queryByKeyLike() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionKeyLike("%o%");

    verifyQueryResults(query, 3);

    query.decisionDefinitionKeyLike("%z\\_");

    verifyQueryResults(query, 1);
  }

  @Test
  public void queryByInvalidKeyLike() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionKeyLike("%invalid%");

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionKeyLike(null);
  }

  @Test
  public void queryByCategory() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionCategory("Examples");

    verifyQueryResults(query, 2);
  }

  @Test
  public void queryByInvalidCategory() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionCategory("invalid");

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionCategory(null);
  }

  @Test
  public void queryByCategoryLike() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionCategoryLike("%Example%");

    verifyQueryResults(query, 3);

    query.decisionDefinitionCategoryLike("%amples2");

    verifyQueryResults(query, 1);

    query.decisionDefinitionCategoryLike("%z\\_");

    verifyQueryResults(query, 1);
  }

  @Test
  public void queryByInvalidCategoryLike() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionCategoryLike("invalid");

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionCategoryLike(null);
  }

  @Test
  public void queryByVersion() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionVersion(2);

    verifyQueryResults(query, 1);

    query.decisionDefinitionVersion(1);

    verifyQueryResults(query, 3);
  }

  @Test
  public void queryByInvalidVersion() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.decisionDefinitionVersion(3);

    verifyQueryResults(query, 0);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionVersion(-1);

    exceptionRule.expect(NotValidException.class);
    query.decisionDefinitionVersion(null);
  }

  @Test
  public void queryByLatest() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    query.latestVersion();

    verifyQueryResults(query, 3);

    query
      .decisionDefinitionKey("one")
      .latestVersion();

    verifyQueryResults(query, 1);

    query
      .decisionDefinitionKey("two")
      .latestVersion();

    verifyQueryResults(query, 1);
  }

  public void testInvalidUsageOfLatest() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    exceptionRule.expect(NotValidException.class);
    query
        .decisionDefinitionId("test")
        .latestVersion()
        .list();

    exceptionRule.expect(NotValidException.class);
    query
        .decisionDefinitionName("test")
        .latestVersion()
        .list();

    exceptionRule.expect(NotValidException.class);
    query
        .decisionDefinitionNameLike("test")
        .latestVersion()
        .list();

    exceptionRule.expect(NotValidException.class);
    query
        .decisionDefinitionVersion(1)
        .latestVersion()
        .list();

    exceptionRule.expect(NotValidException.class);
    query
        .deploymentId("test")
        .latestVersion()
        .list();
  }

  @Test
  public void queryByDecisionRequirementsDefinitionId() {
    testRule.deploy(DRD_DISH_RESOURCE, DRD_SCORE_RESOURCE);

    List<DecisionRequirementsDefinition> drds = repositoryService.createDecisionRequirementsDefinitionQuery()
        .orderByDecisionRequirementsDefinitionName().asc().list();

    String dishDrdId = drds.get(0).getId();
    String scoreDrdId = drds.get(1).getId();

    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    verifyQueryResults(query.decisionRequirementsDefinitionId("non existing"), 0);
    verifyQueryResults(query.decisionRequirementsDefinitionId(dishDrdId), 3);
    verifyQueryResults(query.decisionRequirementsDefinitionId(scoreDrdId), 2);
  }

  @Test
  public void queryByDecisionRequirementsDefinitionKey() {
    testRule.deploy(DRD_DISH_RESOURCE, DRD_SCORE_RESOURCE);

    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    verifyQueryResults(query.decisionRequirementsDefinitionKey("non existing"), 0);
    verifyQueryResults(query.decisionRequirementsDefinitionKey("dish"), 3);
    verifyQueryResults(query.decisionRequirementsDefinitionKey("score"), 2);
  }

  @Test
  public void queryByWithoutDecisionRequirementsDefinition() {
    testRule.deploy(DRD_DISH_RESOURCE, DRD_SCORE_RESOURCE);

    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    verifyQueryResults(query, 9);
    verifyQueryResults(query.withoutDecisionRequirementsDefinition(), 4);
  }

  @Test
  public void querySorting() {
    DecisionDefinitionQuery query = repositoryService.createDecisionDefinitionQuery();

    // asc
    query
      .orderByDecisionDefinitionId()
      .asc();
    verifyQueryResults(query, 4);

    query = repositoryService.createDecisionDefinitionQuery();

    query
      .orderByDeploymentId()
      .asc();
    verifyQueryResults(query, 4);

    query = repositoryService.createDecisionDefinitionQuery();

    query
      .orderByDecisionDefinitionKey()
      .asc();
    verifyQueryResults(query, 4);

    query = repositoryService.createDecisionDefinitionQuery();

    query
      .orderByDecisionDefinitionVersion()
      .asc();
    verifyQueryResults(query, 4);

    // desc

    query = repositoryService.createDecisionDefinitionQuery();

    query
      .orderByDecisionDefinitionId()
      .desc();
    verifyQueryResults(query, 4);

    query = repositoryService.createDecisionDefinitionQuery();

    query
      .orderByDeploymentId()
      .desc();
    verifyQueryResults(query, 4);

    query = repositoryService.createDecisionDefinitionQuery();

    query
      .orderByDecisionDefinitionKey()
      .desc();
    verifyQueryResults(query, 4);

    query = repositoryService.createDecisionDefinitionQuery();

    query
      .orderByDecisionDefinitionVersion()
      .desc();
    verifyQueryResults(query, 4);

    query = repositoryService.createDecisionDefinitionQuery();

    // Typical use decision
    query
      .orderByDecisionDefinitionKey()
      .asc()
      .orderByDecisionDefinitionVersion()
      .desc();

    List<DecisionDefinition> decisionDefinitions = query.list();
    assertThat(decisionDefinitions.size()).isEqualTo(4);

    assertThat(decisionDefinitions.get(0).getKey()).isEqualTo("one");
    assertThat(decisionDefinitions.get(0).getVersion()).isEqualTo(2);
    assertThat(decisionDefinitions.get(1).getKey()).isEqualTo("one");
    assertThat(decisionDefinitions.get(1).getVersion()).isEqualTo(1);
    assertThat(decisionDefinitions.get(2).getKey()).isEqualTo("two");
    assertThat(decisionDefinitions.get(2).getVersion()).isEqualTo(1);
  }


  protected void verifyQueryResults(DecisionDefinitionQuery query, int expectedCount) {
    assertThat(query.count()).isEqualTo(expectedCount);
    assertThat(query.list().size()).isEqualTo(expectedCount);
  }

  @org.camunda.bpm.engine.test.Deployment(resources = {
    "org/camunda/bpm/engine/test/api/repository/versionTag.dmn",
    "org/camunda/bpm/engine/test/api/repository/versionTagHigher.dmn" })
  @Test
  public void testQueryOrderByVersionTag() {
    List<DecisionDefinition> decisionDefinitionList = repositoryService
      .createDecisionDefinitionQuery()
      .versionTagLike("1%")
      .orderByVersionTag()
      .asc()
      .list();

    assertThat(decisionDefinitionList.get(1).getVersionTag()).isEqualTo("1.1.0");
  }

  @Test
  public void testQueryOrderByDecisionRequirementsDefinitionKey() {
    // given
    List<DecisionDefinition> scoreDefinitions = testRule.deploy(DRD_SCORE_RESOURCE).getDeployedDecisionDefinitions();
    List<String> scoreDefinitionIds = asIds(scoreDefinitions);

    List<DecisionDefinition> dishDefinitions = testRule.deploy(DRD_DISH_RESOURCE).getDeployedDecisionDefinitions();
    List<String> dishDefinitionIds = asIds(dishDefinitions);

    // when
    List<DecisionDefinition> decisionDefinitionList = repositoryService
      .createDecisionDefinitionQuery()
      .decisionDefinitionIdIn(merge(scoreDefinitionIds, dishDefinitionIds))
      .orderByDecisionRequirementsDefinitionKey()
      .asc()
      .list();

    // then
    List<DecisionDefinition> firstThreeResults = decisionDefinitionList.subList(0, 3);
    List<DecisionDefinition> lastTwoResults = decisionDefinitionList.subList(3, 5);

    assertThat(firstThreeResults).extracting("id").containsExactlyInAnyOrderElementsOf(dishDefinitionIds);
    assertThat(lastTwoResults).extracting("id").containsExactlyInAnyOrderElementsOf(scoreDefinitionIds);
  }

  @Test
  public void testQueryOrderByDeployTime() {
    List<DecisionDefinition> decisionDefinitions = repositoryService.createDecisionDefinitionQuery().orderByDeploymentTime().asc().list();
    Date lastDeployTime = null;
    for (DecisionDefinition decisionDefinition : decisionDefinitions) {
      Deployment deployment = repositoryService.createDeploymentQuery().deploymentId(decisionDefinition.getDeploymentId()).singleResult();
      if (lastDeployTime == null) {
        lastDeployTime = deployment.getDeploymentTime();
      } else {
        assertThat(lastDeployTime).isBeforeOrEqualsTo(deployment.getDeploymentTime());
        lastDeployTime = deployment.getDeploymentTime();
      }
    }
  }

  protected String[] merge(List<String> list1, List<String> list2) {
    int numElements = list1.size() + list2.size();
    List<String> copy = new ArrayList<>(numElements);
    copy.addAll(list1);
    copy.addAll(list2);

    return copy.toArray(new String[numElements]);
  }

  protected List<String> asIds(List<DecisionDefinition> decisions) {
    List<String> ids = new ArrayList<>();
    for (DecisionDefinition decision : decisions) {
      ids.add(decision.getId());
    }

    return ids;
  }

  @org.camunda.bpm.engine.test.Deployment(resources = {
    "org/camunda/bpm/engine/test/api/repository/versionTag.dmn",
    "org/camunda/bpm/engine/test/api/repository/versionTagHigher.dmn" })
  @Test
  public void testQueryByVersionTag() {
    DecisionDefinition decisionDefinition = repositoryService
      .createDecisionDefinitionQuery()
      .versionTag("1.0.0")
      .singleResult();

    assertThat(decisionDefinition.getKey()).isEqualTo("versionTag");
    assertThat(decisionDefinition.getVersionTag()).isEqualTo("1.0.0");
  }

  @org.camunda.bpm.engine.test.Deployment(resources = {
    "org/camunda/bpm/engine/test/api/repository/versionTag.dmn",
    "org/camunda/bpm/engine/test/api/repository/versionTagHigher.dmn" })
  @Test
  public void testQueryByVersionTagLike() {
    List<DecisionDefinition> decisionDefinitionList = repositoryService
    .createDecisionDefinitionQuery()
    .versionTagLike("1%")
    .list();

    assertThat(decisionDefinitionList).hasSize(2);
  }
}
