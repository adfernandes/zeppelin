/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.zeppelin.interpreter;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;


class InterpreterSettingManagerTest extends AbstractInterpreterTest {

  private String note1Id;
  private String note2Id;
  private String note3Id;
  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();

    note1Id = notebook.createNote("/note_1", AuthenticationInfo.ANONYMOUS);
    note2Id = notebook.createNote("/note_2", AuthenticationInfo.ANONYMOUS);
    note3Id = notebook.createNote("/note_3", AuthenticationInfo.ANONYMOUS);
  }

  @Test
  void testInitInterpreterSettingManager() throws IOException, RepositoryException {
    assertEquals(6, interpreterSettingManager.get().size());
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    assertEquals("test", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    assertEquals(8, interpreterSetting.getInterpreterInfos().size());
    // 3 other builtin properties:
    //   * zeppelin.interpreter.output.limit
    //   * zeppelin.interpreter.localRepo
    //   * zeppelin.interpreter.max.poolsize
    assertEquals(6, interpreterSetting.getJavaProperties().size());
    assertEquals("value_1", interpreterSetting.getJavaProperties().getProperty("property_1"));
    assertEquals("new_value_2", interpreterSetting.getJavaProperties().getProperty("property_2"));
    assertEquals("value_3", interpreterSetting.getJavaProperties().getProperty("property_3"));
    assertEquals("shared", interpreterSetting.getOption().perNote);
    assertEquals("shared", interpreterSetting.getOption().perUser);
    assertEquals(0, interpreterSetting.getDependencies().size());
    assertNotNull(interpreterSetting.getAngularObjectRegistryListener());
    assertNotNull(interpreterSetting.getRemoteInterpreterProcessListener());
    assertNotNull(interpreterSetting.getAppEventListener());
    assertNotNull(interpreterSetting.getDependencyResolver());
    assertNotNull(interpreterSetting.getInterpreterSettingManager());

    List<RemoteRepository> repositories = interpreterSettingManager.getRepositories();
    assertEquals(2, repositories.size());
    // After loading from file, central repository is replaced and moved to the end
    assertEquals("local", repositories.get(0).getId());
    assertEquals("central", repositories.get(1).getId());

    // Load it again
    InterpreterSettingManager interpreterSettingManager2 = new InterpreterSettingManager(zConf,
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class),
        mock(ApplicationEventListener.class), storage, pluginManager);
    assertEquals(6, interpreterSettingManager2.get().size());
    interpreterSetting = interpreterSettingManager2.getByName("test");
    assertEquals("test", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    assertEquals(8, interpreterSetting.getInterpreterInfos().size());
    assertEquals(6, interpreterSetting.getJavaProperties().size());
    assertEquals("value_1", interpreterSetting.getJavaProperties().getProperty("property_1"));
    assertEquals("new_value_2", interpreterSetting.getJavaProperties().getProperty("property_2"));
    assertEquals("value_3", interpreterSetting.getJavaProperties().getProperty("property_3"));
    assertEquals("shared", interpreterSetting.getOption().perNote);
    assertEquals("shared", interpreterSetting.getOption().perUser);
    assertEquals(0, interpreterSetting.getDependencies().size());

    repositories = interpreterSettingManager2.getRepositories();
    assertEquals(2, repositories.size());
    // After loading from file, central repository is replaced and moved to the end
    assertEquals("local", repositories.get(0).getId());
    assertEquals("central", repositories.get(1).getId());

  }

  @Test
  void testCreateUpdateRemoveSetting() throws IOException, InterpreterException {
    // create new interpreter setting
    InterpreterOption option = new InterpreterOption();
    option.setPerNote("scoped");
    option.setPerUser("scoped");
    Map<String, InterpreterProperty> properties = new HashMap<>();
    properties.put("property_4", new InterpreterProperty("property_4","value_4"));

    try {
      interpreterSettingManager.createNewSetting("test2", "test", new ArrayList<Dependency>(), option, properties);
      fail("Should fail due to interpreter already existed");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("already existed"));
    }

    interpreterSettingManager.createNewSetting("test3", "test", new ArrayList<Dependency>(), option, properties);
    assertEquals(7, interpreterSettingManager.get().size());
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test3");
    assertEquals("test3", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    // 3 other builtin properties:
    //   * zeppelin.interpeter.output.limit
    //   * zeppelin.interpreter.localRepo
    //   * zeppelin.interpreter.max.poolsize
    assertEquals(4, interpreterSetting.getJavaProperties().size());
    assertEquals("value_4", interpreterSetting.getJavaProperties().getProperty("property_4"));
    assertEquals("scoped", interpreterSetting.getOption().perNote);
    assertEquals("scoped", interpreterSetting.getOption().perUser);
    assertEquals(0, interpreterSetting.getDependencies().size());
    assertNotNull(interpreterSetting.getAngularObjectRegistryListener());
    assertNotNull(interpreterSetting.getRemoteInterpreterProcessListener());
    assertNotNull(interpreterSetting.getAppEventListener());
    assertNotNull(interpreterSetting.getDependencyResolver());
    assertNotNull(interpreterSetting.getInterpreterSettingManager());

    // load it again, it should be saved in interpreter-setting.json. So we can restore it properly
    InterpreterSettingManager interpreterSettingManager2 = new InterpreterSettingManager(zConf,
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class),
        mock(ApplicationEventListener.class), storage, pluginManager);
    assertEquals(7, interpreterSettingManager2.get().size());
    interpreterSetting = interpreterSettingManager2.getByName("test3");
    assertEquals("test3", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    assertEquals(4, interpreterSetting.getJavaProperties().size());
    assertEquals("value_4", interpreterSetting.getJavaProperties().getProperty("property_4"));
    assertEquals("scoped", interpreterSetting.getOption().perNote);
    assertEquals("scoped", interpreterSetting.getOption().perUser);
    assertEquals(0, interpreterSetting.getDependencies().size());

    // update interpreter setting
    InterpreterOption newOption = new InterpreterOption();
    newOption.setPerNote("scoped");
    newOption.setPerUser("isolated");
    Map<String, InterpreterProperty> newProperties = new HashMap<>(properties);
    newProperties.put("property_4", new InterpreterProperty("property_4", "new_value_4"));
    List<Dependency> newDependencies = new ArrayList<>();
    newDependencies.add(new Dependency("com.databricks:spark-avro_2.11:3.1.0"));
    interpreterSettingManager.setPropertyAndRestart(interpreterSetting.getId(), newOption, newProperties, newDependencies);
    interpreterSetting = interpreterSettingManager.get(interpreterSetting.getId());
    assertEquals("test3", interpreterSetting.getName());
    assertEquals("test", interpreterSetting.getGroup());
    assertEquals(4, interpreterSetting.getJavaProperties().size());
    assertEquals("new_value_4", interpreterSetting.getJavaProperties().getProperty("property_4"));
    assertEquals("scoped", interpreterSetting.getOption().perNote);
    assertEquals("isolated", interpreterSetting.getOption().perUser);
    assertEquals(1, interpreterSetting.getDependencies().size());
    assertNotNull(interpreterSetting.getAngularObjectRegistryListener());
    assertNotNull(interpreterSetting.getRemoteInterpreterProcessListener());
    assertNotNull(interpreterSetting.getAppEventListener());
    assertNotNull(interpreterSetting.getDependencyResolver());
    assertNotNull(interpreterSetting.getInterpreterSettingManager());

    // restart in note page
    // create 3 sessions as it is scoped mode
    interpreterSetting.getOption().setPerUser("scoped");
    interpreterSetting.getDefaultInterpreter("user1", note1Id);
    interpreterSetting.getDefaultInterpreter("user2", note2Id);
    interpreterSetting.getDefaultInterpreter("user3", note3Id);
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup("user1", note1Id);
    assertEquals(3, interpreterGroup.getSessionNum());
    // only close user1's session
    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", note1Id);
    assertEquals(2, interpreterGroup.getSessionNum());

    // remove interpreter setting
    interpreterSettingManager.remove(interpreterSetting.getId());
    assertEquals(6, interpreterSettingManager.get().size());

    // load it again
    InterpreterSettingManager interpreterSettingManager3 = new InterpreterSettingManager(zConf,
        mock(AngularObjectRegistryListener.class), mock(RemoteInterpreterProcessListener.class),
        mock(ApplicationEventListener.class), storage, pluginManager);
    assertEquals(6, interpreterSettingManager3.get().size());

  }

  @Test
  void testGetEditor() {
    // get editor setting from interpreter-setting.json
    Map<String, Object> editor = interpreterSettingManager.getEditorSetting("%test.echo", note1Id);
    assertEquals("java", editor.get("language"));

    editor = interpreterSettingManager.getEditorSetting("%mock1", note1Id);
    assertEquals("python", editor.get("language"));
  }

  @Test
  void testRestartShared() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("shared");
    interpreterSetting.getOption().setPerNote("shared");

    interpreterSetting.getOrCreateSession("user1", note1Id);
    interpreterSetting.getOrCreateInterpreterGroup("user2", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", note1Id);
    assertEquals(0, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testRestartPerUserIsolated() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("isolated");
    interpreterSetting.getOption().setPerNote("shared");

    interpreterSetting.getOrCreateSession("user1", note1Id);
    interpreterSetting.getOrCreateSession("user2", note2Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testRestartPerNoteIsolated() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("shared");
    interpreterSetting.getOption().setPerNote("isolated");

    interpreterSetting.getOrCreateSession("user1", note1Id);
    interpreterSetting.getOrCreateSession("user2", note2Id);
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().size());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
  }

  @Test
  void testRestartPerUserScoped() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("scoped");
    interpreterSetting.getOption().setPerNote("shared");

    interpreterSetting.getOrCreateSession("user1", note1Id);
    interpreterSetting.getOrCreateSession("user2", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
  }

  @Test
  void testRestartPerNoteScoped() throws InterpreterException {
    InterpreterSetting interpreterSetting = interpreterSettingManager.getByName("test");
    interpreterSetting.getOption().setPerUser("shared");
    interpreterSetting.getOption().setPerNote("scoped");

    interpreterSetting.getOrCreateSession("user1", note1Id);
    interpreterSetting.getOrCreateSession("user2", note2Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(2, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());

    interpreterSettingManager.restart(interpreterSetting.getId(), "user1", note1Id);
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().size());
    assertEquals(1, interpreterSetting.getAllInterpreterGroups().get(0).getSessionNum());
  }

  @Test
  void testInterpreterInclude() throws Exception {
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_INCLUDES.getVarName(), "mock1");
      setUp();

      assertEquals(1, interpreterSettingManager.get().size());
      assertEquals("mock1", interpreterSettingManager.get().get(0).getGroup());
    } finally {
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_INCLUDES.getVarName());
    }
  }

  @Test
  void testInterpreterExclude() throws Exception {
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_EXCLUDES.getVarName(),
              "test,config_test,mock_resource_pool");
      setUp();

      assertEquals(2, interpreterSettingManager.get().size());
      assertNotNull(interpreterSettingManager.getByName("mock1"));
      assertNotNull(interpreterSettingManager.getByName("mock2"));
    } finally {
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_EXCLUDES.getVarName());
    }
  }

  @Test
  void testInterpreterIncludeExcludeTogether() throws Exception {
    try {
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_INCLUDES.getVarName(),
              "test,");
      System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_EXCLUDES.getVarName(),
              "config_test,mock_resource_pool");

      try {
        setUp();
        fail("Should not able to create InterpreterSettingManager");
      } catch (Exception e) {
        e.printStackTrace();
        assertEquals("zeppelin.interpreter.include and zeppelin.interpreter.exclude can not be specified together, only one can be set.",
                e.getMessage());
      }
    } finally {
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_INCLUDES.getVarName());
      System.clearProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_EXCLUDES.getVarName());
    }
  }
}
