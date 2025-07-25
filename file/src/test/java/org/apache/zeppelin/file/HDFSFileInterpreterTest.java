/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.completer.CompletionType;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.junit.jupiter.api.Test;

/**
 * Tests Interpreter by running pre-determined commands against mock file system.
 */
class HDFSFileInterpreterTest {
  @Test
  void testMaxLength() {
    HDFSFileInterpreter t = new MockHDFSFileInterpreter(new Properties());
    t.open();
    InterpreterResult result = t.interpret("ls -l /", null);
    String lineSeparator = "\n";
    int fileStatusLength = MockFileSystem.FILE_STATUSES.split(lineSeparator).length;
    assertEquals(result.message().get(0).getData().split(lineSeparator).length, fileStatusLength);
    t.close();

    Properties properties = new Properties();
    final int maxLength = fileStatusLength - 2;
    properties.setProperty("hdfs.maxlength", String.valueOf(maxLength));
    HDFSFileInterpreter t1 = new MockHDFSFileInterpreter(properties);
    t1.open();
    InterpreterResult result1 = t1.interpret("ls -l /", null);
    assertEquals(result1.message().get(0).getData().split(lineSeparator).length, maxLength);
    t1.close();
  }

  @Test
  void test() {
    HDFSFileInterpreter t = new MockHDFSFileInterpreter(new Properties());
    t.open();

    // We have info for /, /user, /tmp, /mr-history/done

    // Ensure
    // 1. ls -l works
    // 2. paths (. and ..) are correctly handled
    // 3. flags and arguments to commands are correctly handled
    InterpreterResult result1 = t.interpret("ls -l /", null);
    assertEquals(InterpreterResult.Type.TEXT, result1.message().get(0).getType());

    InterpreterResult result2 = t.interpret("ls -l /./user/..", null);
    assertEquals(InterpreterResult.Type.TEXT, result2.message().get(0).getType());

    assertEquals(result1.message().get(0).getData(), result2.message().get(0).getData());

    // Ensure you can do cd and after that the ls uses current directory correctly
    InterpreterResult result3 = t.interpret("cd user", null);
    assertEquals(InterpreterResult.Type.TEXT, result3.message().get(0).getType());
    assertEquals("OK", result3.message().get(0).getData());

    InterpreterResult result4 = t.interpret("ls", null);
    assertEquals(InterpreterResult.Type.TEXT, result4.message().get(0).getType());

    InterpreterResult result5 = t.interpret("ls /user", null);
    assertEquals(InterpreterResult.Type.TEXT, result5.message().get(0).getType());

    assertEquals(result4.message().get(0).getData(), result5.message().get(0).getData());

    // Ensure pwd works correctly
    InterpreterResult result6 = t.interpret("pwd", null);
    assertEquals(InterpreterResult.Type.TEXT, result6.message().get(0).getType());
    assertEquals("/user", result6.message().get(0).getData());

    // Move a couple of levels and check we're in the right place
    InterpreterResult result7 = t.interpret("cd ../mr-history/done", null);
    assertEquals(InterpreterResult.Type.TEXT, result7.message().get(0).getType());
    assertEquals("OK", result7.message().get(0).getData());

    InterpreterResult result8 = t.interpret("ls -l ", null);
    assertEquals(InterpreterResult.Type.TEXT, result8.message().get(0).getType());

    InterpreterResult result9 = t.interpret("ls -l /mr-history/done", null);
    assertEquals(InterpreterResult.Type.TEXT, result9.message().get(0).getType());

    assertEquals(result8.message().get(0).getData(), result9.message().get(0).getData());

    InterpreterResult result10 = t.interpret("cd ../..", null);
    assertEquals(InterpreterResult.Type.TEXT, result10.message().get(0).getType());
    assertEquals("OK", result7.message().get(0).getData());

    InterpreterResult result11 = t.interpret("ls -l ", null);
    assertEquals(InterpreterResult.Type.TEXT, result11.message().get(0).getType());

    // we should be back to first result after all this navigation
    assertEquals(result1.message().get(0).getData(), result11.message().get(0).getData());

    // auto completion test
    List<InterpreterCompletion> expectedResultOne = Arrays.asList(
            new InterpreterCompletion("ls", "ls", CompletionType.command.name()));
    List<InterpreterCompletion> expectedResultTwo = Arrays.asList(
            new InterpreterCompletion("pwd", "pwd", CompletionType.command.name()));
    List<InterpreterCompletion> resultOne = t.completion("l", 0, null);
    List<InterpreterCompletion> resultTwo = t.completion("p", 0, null);

    assertEquals(expectedResultOne, resultOne);
    assertEquals(expectedResultTwo, resultTwo);

    t.close();
  }

  @Test
  void testCommandIsNull() {
    HDFSFileInterpreter t = new MockHDFSFileInterpreter(new Properties());
    t.open();

    InterpreterResult result = t.interpret(null, null);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals("No command", result.message().get(0).getData());

    t.close();
  }

  @Test
  void testUnknownCommand() {
    HDFSFileInterpreter t = new MockHDFSFileInterpreter(new Properties());
    t.open();

    InterpreterResult result = t.interpret("unknown", null);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals("Unknown command", result.message().get(0).getData());

    t.close();
  }

  @Test
  void testNoSuchDirectory() {
    HDFSFileInterpreter t = new MockHDFSFileInterpreter(new Properties());
    t.open();

    InterpreterResult result = t.interpret("cd /tmp/ida8c06540_date040315", null);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals("/tmp/ida8c06540_date040315: No such directory",
            result.message().get(0).getData());

    t.close();
  }

  @Test
  void testNoSuchFile() {
    HDFSFileInterpreter t = new MockHDFSFileInterpreter(new Properties());
    t.open();

    InterpreterResult result = t.interpret("ls -l /does/not/exist", null);
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType());
    assertEquals("No such File or directory", result.message().get(0).getData());

    t.close();
  }

}

/**
 * Store command results from curl against a real file system.
 */
class MockFileSystem {
  HashMap<String, String> mfs = new HashMap<>();
  static final String FILE_STATUSES =
          "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":4947954640," +
                  "\"group\":\"hadoop\",\"length\":0,\"modificationTime\":1438548219672," +
                  "\"owner\":\"yarn\",\"pathSuffix\":\"app-logs\",\"permission\":\"777\"," +
                  "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
                  "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16395," +
                  "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438548030045," +
                  "\"owner\":\"hdfs\",\"pathSuffix\":\"hdp\",\"permission\":\"755\"," +
                  "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
                  "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16390," +
                  "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438547985336," +
                  "\"owner\":\"mapred\",\"pathSuffix\":\"mapred\",\"permission\":\"755\"," +
                  "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
                  "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":2,\"fileId\":16392," +
                  "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438547985346," +
                  "\"owner\":\"hdfs\",\"pathSuffix\":\"mr-history\",\"permission\":\"755\"," +
                  "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
                  "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16400," +
                  "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438548089725," +
                  "\"owner\":\"hdfs\",\"pathSuffix\":\"system\",\"permission\":\"755\"," +
                  "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
                  "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16386," +
                  "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438548150089," +
                  "\"owner\":\"hdfs\",\"pathSuffix\":\"tmp\",\"permission\":\"777\"," +
                  "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"},\n" +
                  "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16387," +
                  "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438547921792," +
                  "\"owner\":\"hdfs\",\"pathSuffix\":\"user\",\"permission\":\"755\"," +
                  "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}\n";

  void addListStatusData() {
    mfs.put("/?op=LISTSTATUS",
        "{\"FileStatuses\":{\"FileStatus\":[\n" + FILE_STATUSES +
            "]}}"
    );
    mfs.put("/user?op=LISTSTATUS", "{\"FileStatuses\":{\"FileStatus\":[\n" +
           "        {\"accessTime\":0,\"blockSize\":0,\"childrenNum\":4,\"fileId\":16388," +
               "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1441253161263," +
               "\"owner\":\"ambari-qa\",\"pathSuffix\":\"ambari-qa\",\"permission\":\"770\"," +
               "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}\n" +
           "        ]}}"
    );
    mfs.put("/tmp?op=LISTSTATUS",
        "{\"FileStatuses\":{\"FileStatus\":[\n" +
            "        {\"accessTime\":1441253097489,\"blockSize\":2147483648,\"childrenNum\":0," +
                "\"fileId\":16400,\"group\":\"hdfs\",\"length\":1645," +
                "\"modificationTime\":1441253097517,\"owner\":\"hdfs\"," +
                "\"pathSuffix\":\"ida8c06540_date040315\",\"permission\":\"755\"," +
                "\"replication\":3,\"storagePolicy\":0,\"type\":\"FILE\"}\n" +
            "        ]}}"
    );
    mfs.put("/mr-history/done?op=LISTSTATUS",
        "{\"FileStatuses\":{\"FileStatus\":[\n" +
        "{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16433," +
                "\"group\":\"hadoop\",\"length\":0,\"modificationTime\":1441253197481," +
                "\"owner\":\"mapred\",\"pathSuffix\":\"2015\",\"permission\":\"770\"," +
                "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}\n" +
        "]}}"
    );
  }

  void addGetFileStatusData() {
    mfs.put("/?op=GETFILESTATUS",
        "{\"FileStatus\":{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":7,\"fileId\":16385," +
                "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1438548089725," +
                "\"owner\":\"hdfs\",\"pathSuffix\":\"\",\"permission\":\"755\"," +
                "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}}");
    mfs.put("/user?op=GETFILESTATUS",
        "{\"FileStatus\":{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16387," +
                "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1441253043188," +
                "\"owner\":\"hdfs\",\"pathSuffix\":\"\",\"permission\":\"755\"," +
                "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}}");
    mfs.put("/tmp?op=GETFILESTATUS",
        "{\"FileStatus\":{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16386," +
                "\"group\":\"hdfs\",\"length\":0,\"modificationTime\":1441253097489," +
                "\"owner\":\"hdfs\",\"pathSuffix\":\"\",\"permission\":\"777\"," +
                "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}}");
    mfs.put("/mr-history/done?op=GETFILESTATUS",
        "{\"FileStatus\":{\"accessTime\":0,\"blockSize\":0,\"childrenNum\":1,\"fileId\":16393," +
                "\"group\":\"hadoop\",\"length\":0,\"modificationTime\":1441253197480," +
                "\"owner\":\"mapred\",\"pathSuffix\":\"\",\"permission\":\"777\"," +
                "\"replication\":0,\"storagePolicy\":0,\"type\":\"DIRECTORY\"}}");
  }

  public void addMockData(HDFSCommand.Op op) {
    if (op.op.equals("LISTSTATUS")) {
      addListStatusData();
    } else if (op.op.equals("GETFILESTATUS")) {
      addGetFileStatusData();
    }
    // do nothing
  }

  public String get(String key) {
    return mfs.get(key);
  }
}

/**
 * Run commands against mock file system that simulates webhdfs responses.
 */
class MockHDFSCommand extends HDFSCommand {
  MockFileSystem fs = null;

  MockHDFSCommand(String url, String user, Logger logger, int maxLength) {
    super(url, user, logger, maxLength);
    fs = new MockFileSystem();
    fs.addMockData(getFileStatus);
    fs.addMockData(listStatus);
  }

  MockHDFSCommand(String url, String user, Logger logger) {
    this(url, user, logger, 1000);
  }

  @Override
  public String runCommand(Op op, String path, Arg[] args) throws Exception {
    String error = checkArgs(op, path, args);
    assertNull(error);

    String c = path + "?op=" + op.op;

    if (args != null) {
      for (Arg a : args) {
        c += "&" + a.key + "=" + a.value;
      }
    }
    return fs.get(c);
  }
}

/**
 * Mock Interpreter - uses Mock HDFS command.
 */
class MockHDFSFileInterpreter extends HDFSFileInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(MockHDFSFileInterpreter.class);

  @Override
  public void prepare() {
    // Run commands against mock File System instead of WebHDFS
    int i = Integer.parseInt(getProperty(HDFS_MAXLENGTH) == null ? "1000"
            : getProperty(HDFS_MAXLENGTH));
    cmd = new MockHDFSCommand("", "", LOGGER, i);
    gson = new Gson();
  }

  MockHDFSFileInterpreter(Properties property) {
    super(property);
  }
}
