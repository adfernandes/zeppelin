---
layout: page
title: "Contributing to Apache Zeppelin (Code)"
description: "How can you contribute to Apache Zeppelin project? This document covers from setting up your develop environment to making a pull request on Github."
group: development/contribution
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Contributing to Apache Zeppelin ( Code )

<div id="toc"></div>

> **NOTE :** Apache Zeppelin is an [Apache2 License](http://www.apache.org/licenses/LICENSE-2.0.html) Software.
Any contributions to Zeppelin (Source code, Documents, Image, Website) means you agree with license all your contributions as Apache2 License.

## Setting up
Here are some tools you will need to build and test Zeppelin.

#### Software Configuration Management ( SCM )

Since Zeppelin uses Git for it's SCM system, you need git client installed in your development machine.

#### Integrated Development Environment ( IDE )

You are free to use whatever IDE you prefer, or your favorite command line editor.

#### Build Tools

To build the code, install

  * Java 11

## Getting the source code
First of all, you need Zeppelin source code. The official location of Zeppelin is [https://gitbox.apache.org/repos/asf/zeppelin.git](https://gitbox.apache.org/repos/asf/zeppelin.git).

### git access

Get the source code on your development machine using git.

```bash
git clone git://gitbox.apache.org/repos/asf/zeppelin.git zeppelin
```

You may also want to develop against a specific branch. For example, for branch-0.11.0

```bash
git clone -b branch-0.11.0 git://gitbox.apache.org/repos/asf/zeppelin.git zeppelin
```

Apache Zeppelin follows [Fork & Pull](https://github.com/sevntu-checkstyle/sevntu.checkstyle/wiki/Development-workflow-with-Git:-Fork,-Branching,-Commits,-and-Pull-Request) as a source control workflow.
If you want to not only build Zeppelin but also make any changes, then you need to fork [Zeppelin github mirror repository](https://github.com/apache/zeppelin) and make a pull request.

Before making a pull request, please take a look [Contribution Guidelines](http://zeppelin.apache.org/contribution/contributions.html).


### Build

```bash
./mvnw install
```

To skip test

```bash
./mvnw install -DskipTests
```

To build with specific spark / hadoop version

```bash
./mvnw install -Dspark.version=x.x.x -Dhadoop.version=x.x.x
```

For the further 

### Run Zeppelin server in development mode

#### Option 1 - Command Line

1. Copy the `conf/zeppelin-site.xml.template` to `zeppelin-server/src/main/resources/zeppelin-site.xml` and change the configurations in this file if required
2. Run the following command

```bash
cd zeppelin-server
HADOOP_HOME=YOUR_HADOOP_HOME JAVA_HOME=YOUR_JAVA_HOME \
./mvnw exec:java -Dexec.mainClass="org.apache.zeppelin.server.ZeppelinServer" -Dexec.args=""
```

#### Option 2 - Daemon Script

> **Note:** Make sure you first run 

```bash
./mvnw clean install -DskipTests
```

in your zeppelin root directory, otherwise your server build will fail to find the required dependencies in the local repo.

or use daemon script

```bash
bin/zeppelin-daemon start
```

Server will be run on [http://localhost:8080](http://localhost:8080).

#### Option 3 - IDE

1. Copy the `conf/zeppelin-site.xml.template` to `zeppelin-server/src/main/resources/zeppelin-site.xml` and change the configurations in this file if required
2. `ZeppelinServer.java` Main class


### Generating Thrift Code

Some portions of the Zeppelin code are generated by [Thrift](http://thrift.apache.org). For most Zeppelin changes, you don't need to worry about this. But if you modify any of the Thrift IDL files (e.g. zeppelin-interpreter/src/main/thrift/*.thrift), then you also need to regenerate these files and submit their updated version as part of your patch.

To regenerate the code, install **thrift-0.9.2** and then run the following command to generate thrift code.

```bash
cd <zeppelin_home>/zeppelin-interpreter/src/main/thrift
./genthrift.sh
```

### Run Selenium test

Zeppelin has [set of integration tests](https://github.com/apache/zeppelin/tree/master/zeppelin-integration/src/test/java/org/apache/zeppelin/integration) using Selenium. To run these test, first build and run Zeppelin and make sure Zeppelin is running on port 8080. Then you can run test using following command

```bash
TEST_SELENIUM=true ./mvnw test -Dtest=[TEST_NAME] -DfailIfNoTests=false \
-pl 'zeppelin-interpreter,zeppelin-zengine,zeppelin-server'
```

For example, to run [ParagraphActionIT](https://github.com/apache/zeppelin/blob/master/zeppelin-integration/src/test/java/org/apache/zeppelin/integration/ParagraphActionsIT.java),

```bash
TEST_SELENIUM=true ./mvnw test -Dtest=ParagraphActionsIT -DfailIfNoTests=false \
-pl 'zeppelin-interpreter,zeppelin-zengine,zeppelin-server'
```

You'll need Firefox web browser installed in your development environment.


## Where to Start
You can find issues for <a href="https://issues.apache.org/jira/browse/ZEPPELIN-981?jql=project%20%3D%20ZEPPELIN%20AND%20labels%20in%20(beginner%2C%20newbie)">beginner & newbie</a>

## Stay involved
Contributors should join the Zeppelin mailing lists.

* [dev@zeppelin.apache.org](http://mail-archives.apache.org/mod_mbox/zeppelin-dev/) is for people who want to contribute code to Zeppelin. [subscribe](mailto:dev-subscribe@zeppelin.apache.org?subject=send this email to subscribe), [unsubscribe](mailto:dev-unsubscribe@zeppelin.apache.org?subject=send this email to unsubscribe), [archives](http://mail-archives.apache.org/mod_mbox/zeppelin-dev/)

If you have any issues, create a ticket in [JIRA](https://issues.apache.org/jira/browse/ZEPPELIN).
