package org.modeshape.jcr.benchmark;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkHistoryChart;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import com.carrotsearch.junitbenchmarks.annotation.LabelType;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.modeshape.jcr.JcrRepository;
import org.modeshape.jcr.JcrSession;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Test to measure execution time of creation documents in same folder with
 * different configurations in cluster.
 *
 * This class will generate a graphical summary for all benchmarked
 * methods of the annotated class. Report will be available in
 * <a href="../modeshape-jcr/target/benchmark/create-documents-report.html">file</a>
 * This class will also generate a graphical summary of the historical and
 * current run of a given set of methods. Report will be available in
 * <a href="../modeshape-jcr/target/benchmark/create-documents-report-history.html">file</a>
 *
 * @author evgeniy.shevchenko
 * @version 1.0 4/29/14
 */
@BenchmarkMethodChart(filePrefix = "../modeshape-jcr/target/benchmark/create-documents-report")
@BenchmarkHistoryChart(labelWith = LabelType.CUSTOM_KEY, maxRuns = 20, filePrefix = "../modeshape-jcr/target/benchmark/create-documents-report-history")
@BenchmarkOptions(benchmarkRounds = 4, warmupRounds = 0)
public class CreateDocumentsBenchmarkTest extends AbstractBenchmarkTest {


    /**
     * Count of documents to create.
     */
    private static int TASKS_COUNT = 1000;

    private static final String BODY = "../modeshape-jcr/src/test/resources/cluster/%s/repo.json";
    /**
     * List of documents identifiers, which will be validated on all
     * cluster nodes.
     */
    private Set<String> taskResults =
            new HashSet<String>(TASKS_COUNT);

    @Before
    public void before() throws Exception {
        super.before();
        taskResults.clear();
    }

    /**
     * Test sync replication mode with tcp transport
     * Full configuration can be found by this
     * <a href="file:////src/test/resources/cluster/createDocumentsSyncTcp/repo.json">link</a>
     * @throws Exception on error
     */
    @Test
    @BenchmarkOptions(benchmarkRounds = 1, warmupRounds = 0)
    public void createDocumentsSyncTcp() throws Exception{
        executeTest();
    }


    /**
     * Generate list of {@see Callable} tasks.
     * Where one task will login to random repo in cluster and create document
     * with body.
     * The size of list will be equal {@link #TASKS_COUNT}.
     *
     * @return List of tasks
     */
    @Override
    protected List<Callable<String>> generateTasks() {
        return BenchmarkTestHelper
                .generateCreateDocumentsTasks(
                        repositories, TASKS_COUNT, getMethodName());
    }


    /**
     * Validate that all created objects are available in all repositories
     * after replication.
     * @throws Exception on error
     */
    @Override
    protected void validate() throws Exception {
        for (JcrRepository repository: repositories) {
            JcrSession session = null;
            try {
                session = repository.login();
                BenchmarkTestHelper
                        .validateRepository(
                                session,
                                taskResults,
                                "/",
                                "ClusteredRepository",
                                TASKS_COUNT,
                                false);
                System.out.println("Total items count " + ((Node)session.getRootNode()).getNodes().getSize());
            } finally {
                if (session != null) {
                    session.logout();
                }
            }
        }
    }

    /**
     * Process result of execution one {@see Callable} task.
     * @param result   Result of execution one {@see Callable} task.
     * @throws Exception on error.
     */
    @Override
    protected void processTaskResult(String result) throws Exception {
        taskResults.add(result);
    }

    /**
     * {@see Callable} task for creating documents in root folder.
     * @author evgeniy.shevchenko
     * @version 1.0 3/24/14
     */

     static class CreateDocumentTask implements Callable<String> {

        private JcrRepository repository;
        private String methodName;
        public CreateDocumentTask(
                final JcrRepository repository,
                final String methodName) {
            this.repository = repository;
            this.methodName = methodName;
        }

        /**
         * Create document in root folder.
         *
         * @return document identifier
         * @throws Exception if unable to compute a result
         */
        @Override
        public String call() throws Exception {
            JcrSession session = null;

            try {
                session = repository.login();
                Node root = session.getRootNode();

                Node file = root.addNode(
                        UUID.randomUUID().toString(), "nt:file");
                Node resource = file.addNode("jcr:content", "nt:resource");
                resource.setProperty(
                        "jcr:data",
                        session.getValueFactory()
                                .createBinary(
                                        new FileInputStream(
                                                String.format(BODY, methodName))));
                session.save();
                return file.getIdentifier();
            } finally {
                if (session != null) {
                    session.logout();
                }
            }

        }
    }


}
