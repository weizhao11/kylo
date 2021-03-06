package com.thinkbiganalytics.kylo.spark.client;

/*-
 * #%L
 * kylo-spark-livy-core
 * %%
 * Copyright (C) 2017 - 2018 ThinkBig Analytics, a Teradata Company
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.thinkbiganalytics.kylo.spark.client.model.LivyServer;
import com.thinkbiganalytics.kylo.spark.client.model.enums.LivyServerStatus;
import com.thinkbiganalytics.kylo.spark.model.Session;
import com.thinkbiganalytics.kylo.spark.model.SessionsGetResponse;
import com.thinkbiganalytics.kylo.spark.model.SessionsPost;
import com.thinkbiganalytics.kylo.spark.model.Statement;
import com.thinkbiganalytics.kylo.spark.model.StatementsPost;
import com.thinkbiganalytics.rest.JerseyRestClient;
import com.thinkbiganalytics.spark.shell.SparkShellProcess;

import org.apache.commons.lang3.Validate;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Map;

import javax.annotation.Resource;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * LivyClient's role is to talk to Livy and simplify the management of get/post/delete calls to Livy.  It is careful to ensure Livy is in a ready state by coordinating it's efforts with a heart beat
 * thread.  It also allows for a simple retry on failure mechanism, with a configurable retry policy, should any calls to Livy throw an unexpected error response.
 */
public class DefaultLivyClient implements LivyClient {

    private static final XLogger logger = XLoggerFactory.getXLogger(DefaultLivyClient.class);

    private LivyServer livyServer;

    @Resource
    private Map<SparkShellProcess, Integer /* sessionId */> clientSessionCache;


    public DefaultLivyClient(LivyServer livyServer) {
        this.livyServer = livyServer;
    }

    @Override
    public Statement postStatement(JerseyRestClient client, SparkShellProcess sparkShellProcess, StatementsPost sp) {
        Integer sessionId = clientSessionCache.get(sparkShellProcess);

        try {
            Statement statement = client.post(STATEMENTS_URL(sessionId), sp, Statement.class);
            livyServer.setLivyServerStatus(LivyServerStatus.alive);
            logger.debug("statement={}", statement);
            return statement;
        } catch (WebApplicationException wae) {
            if (wae.getResponse().getStatus() != 404 || wae.getResponse().getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                livyServer.setLivyServerStatus(LivyServerStatus.http_error);
            }
            throw wae;
        }
    }

    @Override
    public Statement getStatement(JerseyRestClient client, SparkShellProcess sparkShellProcess, Integer statementId) {
        Integer sessionId = clientSessionCache.get(sparkShellProcess);

        try {
            Statement statement = client.get(STATEMENT_URL(sessionId, statementId), Statement.class);
            livyServer.setLivyServerStatus(LivyServerStatus.alive);
            logger.debug("statement={}", statement);
            return statement;
        } catch (WebApplicationException wae) {
            if (wae.getResponse().getStatus() != 404 || wae.getResponse().getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                livyServer.setLivyServerStatus(LivyServerStatus.http_error);
            }
            throw wae;
        }
    }

    @Override
    public SessionsGetResponse getSessions(JerseyRestClient client) {
        try {
            SessionsGetResponse sessions = client.get(SESSIONS_URL, SessionsGetResponse.class);
            livyServer.setLivyServerStatus(LivyServerStatus.alive);
            logger.debug("sessions={}", sessions);
            return sessions;
        } catch (WebApplicationException wae) {
            if (wae.getResponse().getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                livyServer.setLivyServerStatus(LivyServerStatus.http_error);
            }
            throw wae;
        }
    }

    @Override
    public Session postSessions(JerseyRestClient client, SessionsPost sessionsPost) {
        try {
            Session session = client.post(SESSIONS_URL, sessionsPost, Session.class);
            livyServer.setLivyServerStatus(LivyServerStatus.alive);
            livyServer.setSessionIdHighWaterMark(session.getId());
            livyServer.setLivySessionState(session.getId(), session.getState());
            logger.debug("session={}", session);
            return session;
        } catch (WebApplicationException wae) {
            if (wae.getResponse().getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                livyServer.setLivyServerStatus(LivyServerStatus.http_error);
            }
            throw wae;
        }
    }

    @Override
    public Session getSession(JerseyRestClient client, SparkShellProcess sparkShellProcess) {
        Integer sessionId = clientSessionCache.get(sparkShellProcess);

        try {
            Session session = client.get(SESSION_URL(sessionId), Session.class);
            livyServer.setLivyServerStatus(LivyServerStatus.alive);
            livyServer.setLivySessionState(session.getId(), session.getState());
            logger.debug("session={}", session);
            return session;
        } catch (WebApplicationException wae) {
            // should catch 404, 500 etc.
            livyServer.recordSessionState(sessionId, wae.getResponse().getStatus(), null);
            throw wae;
        } catch (Exception e) {
            logger.error("Unexpected exception occurred:", e);
            throw e;
        }
    }

    private final static String SESSIONS_URL = "/sessions";

    private String SESSION_URL(Integer sessionId) {
        Validate.notNull(sessionId, "sessionId cannot be null");
        return String.format("/sessions/%s", sessionId);
    }

    private String STATEMENT_URL(Integer sessionId, Integer statementId) {
        Validate.notNull(sessionId, "sessionId cannot be null");
        Validate.notNull(statementId, "sessionId cannot be null");
        return String.format("/sessions/%s/statements/%s", sessionId, statementId);
    }

    private String STATEMENTS_URL(Integer sessionId) {
        Validate.notNull(sessionId, "sessionId cannot be null");
        return String.format("/sessions/%s/statements", sessionId);
    }

}
