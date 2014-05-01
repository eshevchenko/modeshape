/*
 * ModeShape (http://www.modeshape.org)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of
 * individual contributors.
 *
 * ModeShape is free software. Unless otherwise indicated, all code in ModeShape
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * ModeShape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.modeshape.jcr.benchmark;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkHistoryChart;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import com.carrotsearch.junitbenchmarks.annotation.LabelType;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.modeshape.common.util.FileUtil;
import org.modeshape.jcr.ClusteringHelper;
import org.modeshape.jcr.JcrRepository;
import org.modeshape.jcr.JcrSession;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.modeshape.jcr.NoSuchRepositoryException;
import org.modeshape.jcr.RepositoryConfiguration;
import org.modeshape.jcr.TestingUtil;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Execute test in cluster environment.
 *
 *
 * @author evgeniy.shevchenko
 * @version 1.0 4/14/14
 */
public abstract class AbstractBenchmarkTest {
    protected static int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors();




    /**
     * Count of cluster nodes.
     */
    protected static int CLUSTER_NODES_COUNT = 4;

    /**
     * Array of cluster repositories.
     */
    protected JcrRepository[] repositories;

    protected String methodName;
    /**
     *  A benchmark rule to  execution time of test action.
     */
    @Rule
    public TestRule rule = RuleChain.outerRule(
            new TestWatcher() {
                /**
                 * Start all repositories and run the test action.
                 * @param base
                 * @param description
                 * @return
                 */
                @Override
                public Statement apply(final Statement base, final Description description) {
                   return new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            // Start repositories before test will be executed to exclude
                            // "cluster startup time" from measure.
                            startRepositories(description);
                            base.evaluate();
                        }
                    };
                }


                /**
                 * Start all cluster nodes.
                 * @throws Exception
                 */
                private void startRepositories(final Description description)
                        throws Exception {
                    methodName = description.getMethodName();
                    System.setProperty("cluster.testname", methodName);
                    repositories = new JcrRepository[CLUSTER_NODES_COUNT];
                    for (int i = 0; i < CLUSTER_NODES_COUNT; i++) {
                        System.setProperty(
                                "jgroups.tcp.port",
                                new Integer(17900 + i).toString());
                        System.setProperty(
                                "cluster.item.number",
                                new Integer(i).toString());

                        repositories[i] =
                                TestingUtil
                                        .startRepositoryWithConfig(
                                                String.format(
                                                        "cluster/%s/repo.json", methodName));
                    }
                }

            }).around(new BenchmarkRule());

    /**
     * Bind Jgroups, init system variables.
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        ClusteringHelper.bindJGroupsToLocalAddress();
    }

    /**
     * Unbind Jgroups.
     * @throws Exception
     */
    @AfterClass
    public static void afterClass() throws Exception {
        ClusteringHelper.removeJGroupsBindings();
    }

    /**
     * Stop engines.
     * @throws Exception
     */
    @After
    public void after() throws Exception {
        Thread.sleep(3000);
        validate();
        TestingUtil.killRepositories(repositories);
    }

    /**
     * Execute {@see MoveNodeTask} tasks.
     * @throws Exception
     */
    protected void executeTest() throws Exception{
        executeTest(DEFAULT_POOL_SIZE);
    }
    /**
     * Execute {@see MoveNodeTask} tasks.
     * @throws Exception
     */
    protected void executeTest(final int poolSize)
                throws Exception {
        CompletionService<String> completionService =
                new ExecutorCompletionService(
                        Executors.newFixedThreadPool(poolSize));
        List<Callable<String>> tasks = generateTasks();
        for (Callable task : tasks) {
            completionService.submit(task);
        }
        for (int i = 0; i < tasks.size(); i++) {
            String taskResult =
                    completionService.take().get();
            processTaskResult(taskResult);
        }
    }




    protected abstract List<Callable<String>> generateTasks() throws RepositoryException;
    protected abstract void validate() throws Exception;
    protected abstract void processTaskResult(String result) throws Exception;
}
