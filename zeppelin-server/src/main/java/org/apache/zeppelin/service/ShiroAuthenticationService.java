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
package org.apache.zeppelin.service;

import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.sql.DataSource;

import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.realm.ldap.DefaultLdapRealm;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.JdbcUtils;
import org.apache.shiro.util.ThreadContext;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.realm.ActiveDirectoryGroupRealm;
import org.apache.zeppelin.realm.LdapRealm;
import org.apache.zeppelin.realm.jwt.KnoxJwtRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AuthenticationService which use Apache Shiro.
 */
public class ShiroAuthenticationService implements AuthenticationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShiroAuthenticationService.class);

  private static final String INI_REALM = "org.apache.shiro.realm.text.IniRealm";
  private static final String LDAP_REALM = "org.apache.zeppelin.realm.LdapRealm";
  private static final String LDAP_GROUP_REALM = "org.apache.zeppelin.realm.LdapGroupRealm";
  private static final String ACTIVE_DIRECTORY_GROUP_REALM = "org.apache.zeppelin.realm.ActiveDirectoryGroupRealm";
  private static final String JDBC_REALM = "org.apache.shiro.realm.jdbc.JdbcRealm";

  private static final Pattern VALID_SQL_NAME_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

  private static boolean isValidSqlIdentifier(String name) {
    return name != null && VALID_SQL_NAME_IDENTIFIER_PATTERN.matcher(name).matches();
  }

  private final ZeppelinConfiguration zConf;

  @Inject
  public ShiroAuthenticationService(ZeppelinConfiguration zConf) throws Exception {
    LOGGER.info("ShiroAuthenticationService is initialized");
    this.zConf = zConf;
    if (zConf.getShiroPath().length() > 0) {
      try {
        Collection<Realm> realms =
            ((DefaultSecurityManager) org.apache.shiro.SecurityUtils.getSecurityManager())
                .getRealms();
        if (realms.size() > 1) {
          boolean isIniRealmEnabled = false;
          for (Realm realm : realms) {
            if (realm instanceof IniRealm && ((IniRealm) realm).getIni().get("users") != null) {
              isIniRealmEnabled = true;
              break;
            }
          }
          if (isIniRealmEnabled) {
            throw new Exception(
                "IniRealm/password based auth mechanisms should be exclusive. "
                    + "Consider removing [users] block from shiro.ini");
          }
        }
      } catch (UnavailableSecurityManagerException e) {
        LOGGER.error("Failed to initialise shiro configuration", e);
      }
    }
  }

  /**
   * Return the authenticated user if any otherwise returns "anonymous".
   *
   * @return shiro principal
   */
  @Override
  public String getPrincipal() {
    Subject subject = org.apache.shiro.SecurityUtils.getSubject();

    String principal;
    if (subject.isAuthenticated()) {
      principal = extractPrincipal(subject);
      if (zConf.isUsernameForceLowerCase()) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Converting principal name {} to lower case: {}", principal, principal.toLowerCase());
        }
        principal = principal.toLowerCase();
      }
    } else {
      // TODO(jl): Could be better to occur error?
      principal = "anonymous";
    }
    return principal;
  }

  private String extractPrincipal(Subject subject) {
    String principal;
    Object principalObject = subject.getPrincipal();
    if (principalObject instanceof Principal) {
      principal = ((Principal) principalObject).getName();
    } else {
      principal = String.valueOf(principalObject);
    }
    return principal;
  }

  @Override
  public Collection<Realm> getRealmsList() {
    String key = ThreadContext.SECURITY_MANAGER_KEY;
    DefaultSecurityManager defaultSecurityManager = (DefaultSecurityManager) ThreadContext.get(key);
    return defaultSecurityManager.getRealms();
  }

  /** Checked if shiro enabled or not. */
  @Override
  public boolean isAuthenticated() {
    return org.apache.shiro.SecurityUtils.getSubject().isAuthenticated();
  }

  /**
   * Get candidated users based on searchText
   *
   * @param searchText
   * @param numUsersToFetch
   * @return
   */
  @Override
  public List<String> getMatchedUsers(String searchText, int numUsersToFetch) {
    List<String> usersList = new ArrayList<>();
    try {
      Collection<Realm> realmsList = getRealmsList();
      if (realmsList != null) {
        for (Realm realm : realmsList) {
          String realClassName = realm.getClass().getName();
          LOGGER.debug("RealmClass.getName: {}", realClassName);
          if (INI_REALM.equals(realClassName)) {
            usersList.addAll(getUserList((IniRealm) realm));
          } else if (LDAP_GROUP_REALM.equals(realClassName)) {
            usersList.addAll(getUserList((DefaultLdapRealm) realm, searchText, numUsersToFetch));
          } else if (LDAP_REALM.equals(realClassName)) {
            usersList.addAll(getUserList((LdapRealm) realm, searchText, numUsersToFetch));
          } else if (ACTIVE_DIRECTORY_GROUP_REALM.equals(realClassName)) {
            usersList.addAll(getUserList((ActiveDirectoryGroupRealm) realm, searchText, numUsersToFetch));
          } else if (JDBC_REALM.equals(realClassName)) {
            usersList.addAll(getUserList((JdbcRealm) realm, searchText, numUsersToFetch));
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Exception in retrieving Users from realms ", e);
    }
    return usersList;
  }

  /**
   * Get matched roles.
   *
   * @return
   */
  @Override
  public List<String> getMatchedRoles() {
    List<String> rolesList = new ArrayList<>();
    try {
      Collection<Realm> realmsList = getRealmsList();
      if (realmsList != null) {
        for (Realm realm : realmsList) {
          String name = realm.getClass().getName();
          LOGGER.debug("RealmClass.getName: {}", name);
          if (INI_REALM.equals(name)) {
            rolesList.addAll(getRolesList((IniRealm) realm));
          } else if (LDAP_REALM.equals(name)) {
            rolesList.addAll(getRolesList((LdapRealm) realm));
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Exception in retrieving Users from realms ", e);
    }
    return rolesList;
  }

  /**
   * Return the roles associated with the authenticated user if any otherwise returns empty set.
   * TODO(prasadwagle) Find correct way to get user roles (see SHIRO-492)
   *
   * @return shiro roles
   */
  @Override
  public Set<String> getAssociatedRoles() {
    Subject subject = org.apache.shiro.SecurityUtils.getSubject();
    Set<String> roles = new HashSet<>();
    Map<String, String> allRoles = null;

    if (subject.isAuthenticated()) {
      Collection<Realm> realmsList = getRealmsList();
      for (Realm realm : realmsList) {
        String name = realm.getClass().getName();
        if (INI_REALM.equals(name)) {
          allRoles = ((IniRealm) realm).getIni().get("roles");
          break;
        } else if (LDAP_REALM.equals(name)) {
          try {
            AuthorizationInfo auth =
                ((LdapRealm) realm)
                    .queryForAuthorizationInfo(
                        new SimplePrincipalCollection(subject.getPrincipal(), realm.getName()),
                        ((LdapRealm) realm).getContextFactory());
            if (auth != null) {
              roles = new HashSet<>(auth.getRoles());
            }
          } catch (NamingException e) {
            LOGGER.error("Can't fetch roles", e);
          }
          break;
        } else if (ACTIVE_DIRECTORY_GROUP_REALM.equals(name)) {
          allRoles = ((ActiveDirectoryGroupRealm) realm).getListRoles();
          break;
        } else if (realm instanceof KnoxJwtRealm) {
          roles = ((KnoxJwtRealm) realm).mapGroupPrincipals(getPrincipal());
          break;
        }
      }
      if (allRoles != null) {
        for (Map.Entry<String, String> pair : allRoles.entrySet()) {
          if (subject.hasRole(pair.getKey())) {
            roles.add(pair.getKey());
          }
        }
      }
    }
    return roles;
  }

  /** Function to extract users from shiro.ini. */
  private List<String> getUserList(IniRealm r) {
    List<String> userList = new ArrayList<>();
    Map<String, String> getIniUser = r.getIni().get(IniRealm.USERS_SECTION_NAME);
    if (getIniUser != null) {
      for (Map.Entry<String, String> pair : getIniUser.entrySet()) {
        userList.add(pair.getKey().trim());
      }
    }
    return userList;
  }

  /**
   * * Get user roles from shiro.ini.
   *
   * @param r
   * @return
   */
  private List<String> getRolesList(IniRealm r) {
    List<String> roleList = new ArrayList<>();
    Map<String, String> getIniRoles = r.getIni().get(IniRealm.ROLES_SECTION_NAME);
    if (getIniRoles != null) {
      for (Map.Entry<String, String> pair : getIniRoles.entrySet()) {
        roleList.add(pair.getKey().trim());
      }
    }
    return roleList;
  }

  /** Function to extract users from LDAP. */
  private List<String> getUserList(DefaultLdapRealm r, String searchText, int numUsersToFetch) {
    List<String> userList = new ArrayList<>();
    String userDnTemplate = r.getUserDnTemplate();
    String[] userDn = userDnTemplate.split(",", 2);
    String userDnPrefix = userDn[0].split("=")[0];
    String userDnSuffix = userDn[1];
    JndiLdapContextFactory cf = (JndiLdapContextFactory) r.getContextFactory();
    try {
      LdapContext ctx = cf.getSystemLdapContext();
      SearchControls constraints = new SearchControls();
      constraints.setCountLimit(numUsersToFetch);
      constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
      String[] attrIDs = {userDnPrefix};
      constraints.setReturningAttributes(attrIDs);
      NamingEnumeration<SearchResult> result =
          ctx.search(userDnSuffix, "(" + userDnPrefix + "=*" + searchText + "*)", constraints);
      while (result.hasMore()) {
        Attributes attrs = result.next().getAttributes();
        if (attrs.get(userDnPrefix) != null) {
          String currentUser = attrs.get(userDnPrefix).toString();
          userList.add(currentUser.split(":")[1].trim());
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error retrieving User list from Ldap Realm", e);
    }
    LOGGER.info("UserList: {}", userList);
    return userList;
  }

  /** Function to extract users from Zeppelin LdapRealm. */
  private List<String> getUserList(LdapRealm r, String searchText, int numUsersToFetch) {
    List<String> userList = new ArrayList<>();
    LOGGER.debug("SearchText: {}", searchText);
    String userAttribute = r.getUserSearchAttributeName();
    String userSearchRealm = r.getUserSearchBase();
    String userObjectClass = r.getUserObjectClass();
    JndiLdapContextFactory cf = (JndiLdapContextFactory) r.getContextFactory();
    try {
      LdapContext ctx = cf.getSystemLdapContext();
      SearchControls constraints = new SearchControls();
      constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
      constraints.setCountLimit(numUsersToFetch);
      String[] attrIDs = {userAttribute};
      constraints.setReturningAttributes(attrIDs);
      NamingEnumeration<SearchResult> result =
          ctx.search(
              userSearchRealm,
              "(&(objectclass="
                  + userObjectClass
                  + ")("
                  + userAttribute
                  + "=*"
                  + searchText
                  + "*))",
              constraints);
      while (result.hasMore()) {
        Attributes attrs = result.next().getAttributes();
        if (attrs.get(userAttribute) != null) {
          String currentUser;
          if (r.getUserLowerCase()) {
            LOGGER.debug("userLowerCase true");
            currentUser = ((String) attrs.get(userAttribute).get()).toLowerCase();
          } else {
            LOGGER.debug("userLowerCase false");
            currentUser = (String) attrs.get(userAttribute).get();
          }
          LOGGER.debug("CurrentUser: {}", currentUser);
          userList.add(currentUser.trim());
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error retrieving User list from Ldap Realm", e);
    }
    return userList;
  }

  /**
   * * Get user roles from shiro.ini for Zeppelin LdapRealm.
   *
   * @param r
   * @return
   */
  private List<String> getRolesList(LdapRealm r) {
    List<String> roleList = new ArrayList<>();
    Map<String, String> roles = r.getListRoles();
    if (roles != null) {
      for (Map.Entry<String, String> pair : roles.entrySet()) {
        LOGGER.debug("RoleKeyValue: {} = {}", pair.getKey(), pair.getValue());
        roleList.add(pair.getKey());
      }
    }
    return roleList;
  }

  private List<String> getUserList(
      ActiveDirectoryGroupRealm r, String searchText, int numUsersToFetch) {
    List<String> userList = new ArrayList<>();
    try {
      LdapContext ctx = r.getLdapContextFactory().getSystemLdapContext();
      userList = r.searchForUserName(searchText, ctx, numUsersToFetch);
    } catch (Exception e) {
      LOGGER.error("Error retrieving User list from ActiveDirectory Realm", e);
    }
    return userList;
  }

  /** Function to extract users from JDBCs. */
  private List<String> getUserList(JdbcRealm obj, String searchText, int numUsersToFetch) {
    List<String> userlist = new ArrayList<>();
    Connection con = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    DataSource dataSource;
    String authQuery;
    String[] retval;
    String tablename = "";
    String username = "";
    String userquery;
    try {
      dataSource = (DataSource) FieldUtils.readField(obj, "dataSource", true);
      authQuery = (String) FieldUtils.readField(obj, "authenticationQuery", true);
      LOGGER.debug("authenticationQuery={}", authQuery);
      String authQueryLowerCase = authQuery.toLowerCase();
      retval = authQueryLowerCase.split("from", 2);
      if (retval.length >= 2) {
        retval = retval[1].split("with|where", 2);
        tablename = retval[0].strip();
        retval = retval[1].split("where", 2);
        if (retval.length >= 2) {
          retval = retval[1].split("=", 2);
        } else {
          retval = retval[0].split("=", 2);
        }
        username = retval[0].strip();
      }

      if (StringUtils.isBlank(username) || StringUtils.isBlank(tablename)) {
        return userlist;
      }
      if (!isValidSqlIdentifier(username)) {
        throw new IllegalArgumentException(
          "Invalid column name in authenticationQuery to build userlist query: "
            + authQuery + ", allowed pattern: " + VALID_SQL_NAME_IDENTIFIER_PATTERN
            + ", name identifier: [" + username + "]");
      }
      if (!isValidSqlIdentifier(tablename)) {
        throw new IllegalArgumentException(
          "Invalid table name in authenticationQuery to build userlist query: "
            + authQuery + ", allowed pattern: " + VALID_SQL_NAME_IDENTIFIER_PATTERN
            + ", name identifier: [" + tablename + "]");
      }

      userquery = String.format("SELECT %s FROM %s WHERE %s LIKE ?", username, tablename, username);
      LOGGER.info("Built query for user list. userquery={}", userquery);
    } catch (IllegalAccessException e) {
      LOGGER.error("Error while accessing dataSource for JDBC Realm", e);
      return new ArrayList<>();
    }

    try {
      con = dataSource.getConnection();
      ps = con.prepareStatement(userquery);
      ps.setString(1, "%" + searchText + "%");
      rs = ps.executeQuery();
      while (rs.next() && userlist.size() < numUsersToFetch) {
        userlist.add(rs.getString(1));
      }
    } catch (Exception e) {
      LOGGER.error("Error retrieving User list from JDBC Realm", e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(con);
    }
    return userlist;
  }
}
