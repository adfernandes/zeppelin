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

package org.apache.zeppelin.graph.neo4j;

import org.apache.commons.lang3.StringUtils;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.driver.SessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;

/**
 * Neo4j connection manager for Zeppelin.
 */
public class Neo4jConnectionManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jConnectionManager.class);

  public static final String NEO4J_SERVER_URL = "neo4j.url";
  public static final String NEO4J_DATABASE = "neo4j.database";
  public static final String NEO4J_AUTH_TYPE = "neo4j.auth.type";
  public static final String NEO4J_AUTH_USER = "neo4j.auth.user";
  public static final String NEO4J_AUTH_PASSWORD = "neo4j.auth.password";
  public static final String NEO4J_MAX_CONCURRENCY = "neo4j.max.concurrency";

  private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\{\\w+\\}");
  private static final String REPLACE_CURLY_BRACKETS = "\\{|\\}";

  private static final Pattern $_PATTERN = Pattern.compile("\\$\\w+\\}");
  private static final String REPLACE_$ = "\\$";

  private Driver driver = null;

  private final String neo4jUrl;

  private final Config config;

  private final AuthToken authToken;

  private final String database;

  /**
   * Enum type for the AuthToken.
   */
  public enum Neo4jAuthType {NONE, BASIC}

  public Neo4jConnectionManager(Properties properties) {
    this.neo4jUrl = properties.getProperty(NEO4J_SERVER_URL);
    this.config = Config.defaultConfig();
    this.database = properties.getProperty(NEO4J_DATABASE);
    this.authToken = initAuth(properties);
  }

  private AuthToken initAuth(Properties properties) {
    String authType = properties.getProperty(NEO4J_AUTH_TYPE, "NONE").toUpperCase();

    Neo4jAuthType neo4jAuthType;
    try {
      neo4jAuthType = Neo4jAuthType.valueOf(authType);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported Neo4j authentication type: " + authType, e);
    }

    switch (neo4jAuthType) {
      case BASIC:
        String username = properties.getProperty(NEO4J_AUTH_USER);
        String password = properties.getProperty(NEO4J_AUTH_PASSWORD);
        return AuthTokens.basic(username, password);
      case NONE:
        LOGGER.debug("Creating NONE authentication");
        return AuthTokens.none();
      default:
        throw new RuntimeException("Unexpected Neo4j authentication type");
    }
  }

  private Driver getDriver() {
    if (driver == null) {
      driver = GraphDatabase.driver(this.neo4jUrl, this.authToken, this.config);
    }
    return driver;
  }

  public void open() {
    getDriver();
  }

  public void close() {
    getDriver().close();
  }

  private Session getSession() {
    return getDriver().session(StringUtils.isNotEmpty(database) ?
            SessionConfig.forDatabase(database) : SessionConfig.defaultConfig());
  }

  public List<Record> execute(String cypherQuery,
                              InterpreterContext interpreterContext) {
    Map<String, Object> params = new HashMap<>();
    if (interpreterContext != null) {
      ResourcePool resourcePool = interpreterContext.getResourcePool();
      Set<String> keys = extractParams(cypherQuery, PROPERTY_PATTERN, REPLACE_CURLY_BRACKETS);
      keys.addAll(extractParams(cypherQuery, $_PATTERN, REPLACE_$));
      for (String key : keys) {
        Resource resource = resourcePool.get(key);
        if (resource != null) {
          params.put(key, resource.get());
        }
      }
    }
    LOGGER.debug("Executing cypher query {} with params {}", cypherQuery, params);
    try (Session session = getSession()) {
      final Result result = params.isEmpty()
              ? session.run(cypherQuery) : session.run(cypherQuery, params);
      return result.list();
    }
  }

  public List<Record> execute(String cypherQuery) {
    return execute(cypherQuery, null);
  }

  private Set<String> extractParams(String cypherQuery, Pattern pattern, String replaceChar) {
    Matcher matcher = pattern.matcher(cypherQuery);
    Set<String> keys = new HashSet<>();
    while (matcher.find()) {
      keys.add(matcher.group().replaceAll(replaceChar, StringUtils.EMPTY));
    }
    return keys;
  }
}

