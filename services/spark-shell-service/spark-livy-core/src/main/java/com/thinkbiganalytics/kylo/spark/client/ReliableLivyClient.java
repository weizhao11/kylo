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

import com.thinkbiganalytics.kylo.spark.livy.SparkLivyProcessManager;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.annotation.Resource;
import javax.ws.rs.WebApplicationException;

/**
 * A simple decorator that will attempt to restart failed queries on get/post statements
 */
public class ReliableLivyClient implements LivyClient {

    private static final XLogger logger = XLoggerFactory.getXLogger(ReliableLivyClient.class);

    @Resource
    private SparkLivyProcessManager processManager;

    private LivyClient livyClient;

    public ReliableLivyClient(LivyClient livyClient) {
        this.livyClient = livyClient;
    }


    @Override
    public Statement postStatement(JerseyRestClient client, SparkShellProcess sparkShellProcess, StatementsPost sp) {
        // What happens if session is missing?
        try {
            return livyClient.postStatement(client, sparkShellProcess, sp);
        } catch( WebApplicationException we ) {
            if( we.getResponse().getStatus() == 404 ) {
                // session is gone, or not able to perform work..
                final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                processManager.start(auth.getName());

                return livyClient.postStatement(client, sparkShellProcess, sp);
            }

            throw we;
        }
    }


    @Override
    public Statement getStatement(JerseyRestClient client, SparkShellProcess sparkShellProcess, Integer statementId) {
        try {
            return livyClient.getStatement(client, sparkShellProcess, statementId);
        } catch( WebApplicationException we ) {
            if( we.getResponse().getStatus() == 404 ) {
                // session is gone, or not able to perform work..
                final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                processManager.start(auth.getName());

                return livyClient.getStatement(client, sparkShellProcess, statementId);
            }

            throw we;
        }
    }


    @Override
    public SessionsGetResponse getSessions(JerseyRestClient client) {
        return livyClient.getSessions(client);
    }


    @Override
    public Session postSessions(JerseyRestClient client, SessionsPost sessionsPost) {
        return livyClient.postSessions(client, sessionsPost);
    }


    @Override
    public Session getSession(JerseyRestClient client, SparkShellProcess sessionId) {
        return livyClient.getSession(client, sessionId);
    }
}
