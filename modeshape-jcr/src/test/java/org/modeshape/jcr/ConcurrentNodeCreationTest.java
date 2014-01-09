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
package org.modeshape.jcr;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.modeshape.common.junit.SkipLongRunning;
import org.modeshape.common.util.FileUtil;
import org.modeshape.jcr.ModeShapeEngine.State;
import org.modeshape.jcr.api.Binary;

public class ConcurrentNodeCreationTest
        extends AbstractTransactionalTest {

    private static final int THREADS_COUNT = 5;
    private static final int COUNT_PER_THREAD = 2;
    private static final String FOLDER_NAME="SampleFolder";

    private RepositoryConfiguration config;
    private ModeShapeEngine engine;
    private JcrRepository repository;
    private Set<Callable<Void>> callables;


    @Before
    public void beforeEach() throws Exception {

        FileUtil.delete("target/concurrent");
        config = RepositoryConfiguration.read(
                "concurrent/repo-config.json");
        engine = new ModeShapeEngine();
        startEngineAndDeployRepository();
        initializeFolder(FOLDER_NAME);
        callables = new HashSet<Callable<Void>>();
        for (int j = 0; j < THREADS_COUNT; j++) {
            callables.add(new ItemCreator(j, COUNT_PER_THREAD));
        }

    }

    @After
    public void afterEach() throws Exception {
       try {
                engine.shutdown(true).get();
            } finally {
                repository = null;
                engine = null;
                config = null;
            }

    }

    @SkipLongRunning
    @Test
    public void shouldCreateItemsSingleThread() throws Exception {
        ExecutorService executorServiceSingle = Executors.newSingleThreadExecutor();
        runAndCheck(executorServiceSingle);

    }

    @SkipLongRunning
    @Test
    public void shouldCreateItemsConcurrently() throws Exception {
        ExecutorService executorServiceMulti = Executors.newFixedThreadPool(THREADS_COUNT);
        runAndCheck(executorServiceMulti);
    }

    private void runAndCheck(
            ExecutorService executorService) throws Exception {

        try {
            List<Future<Void>> threadResults = executorService.invokeAll(callables);
            for (Future<?> future : threadResults) {
                future.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            executorService.shutdown();
        }
        JcrSession session = repository.login();
        Long actual = session.getRootNode().getNode(FOLDER_NAME).getNodes().getSize();
        Long expected = Long.valueOf(THREADS_COUNT*COUNT_PER_THREAD);
        assertThat("Folder has appropriated children count", actual, is(expected));
        session.logout();
    }

    protected void startEngineAndDeployRepository()
            throws Exception {
        engine.start();
        assertThat(engine.getState(), is(State.RUNNING));
        repository = engine.deploy(config);
    }

    protected void initializeFolder(String name) throws Exception {
        final Session session = repository.login();
        session.getRootNode().addNode(name, "acme:group");
        session.save();
        session.logout();
    }


    private final class ItemCreator implements Callable<Void> {

        private final int count;
        private final int threadNum;



        protected ItemCreator(int threadNum, int count) {
            this.threadNum = threadNum;
            this.count = count;
        }

        @Override
        public Void call() throws Exception {
            for (int i = 0; i < count; i++) {
                Node file = null;
                JcrSession jcrSession = null;
                try {
                    jcrSession = repository.login();
                    Node folder = jcrSession.getRootNode().getNode(FOLDER_NAME);
                    file = createItem(jcrSession, folder, i, "acme:item");
                    jcrSession.save();
//                    Thread.sleep(500);
//                    System.out.println(file);
                }finally {
                    if (jcrSession != null) {
                        jcrSession.logout();
                    }
                }
            }
            return null;
        }

        private Node createItem(
                JcrSession jcrSession,
                Node parentFolder,
                int num,
                String typeId) throws Exception{
            Node fileNode =
                    parentFolder
                            .addNode(
                                    "item-" + threadNum + "-" + num,
                                    typeId);

           Node contentNode = fileNode
                    .addNode(Node.JCR_CONTENT, NodeType.NT_RESOURCE);

            Binary binary = jcrSession.getValueFactory()
                    .createBinary("Sample content".getBytes());
            contentNode.setProperty("jcr:data", binary);
            return fileNode;
        }


    }


}
