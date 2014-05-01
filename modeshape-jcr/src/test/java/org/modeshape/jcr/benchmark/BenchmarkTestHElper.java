package org.modeshape.jcr.benchmark;

import junit.framework.Assert;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.modeshape.jcr.JcrRepository;
import org.modeshape.jcr.JcrSession;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Utility class with common methods.
 * @author evgeniy.shevchenko
 * @version 1.0 4/30/14
 */

public class BenchmarkTestHelper {
    public static final Random RANDOM = new Random();

    /**
     * Generate list of {@see Callable} tasks.
     * One task will login to random repo in cluster and create document
     * with body.
     * @return List of tasks
     */
    public static List<Callable<String>> generateCreateDocumentsTasks(
            final JcrRepository[] repositories,
            final int tasksCount,
            final String methodName) {
        final List<Callable<String>> tasks =
                new ArrayList<Callable<String>>(
                        tasksCount);
        for (int i = 0; i < tasksCount; i++) {
            final int index = RANDOM.nextInt(repositories.length);
            final CreateDocumentsBenchmarkTest.CreateDocumentTask task =
                    new CreateDocumentsBenchmarkTest.CreateDocumentTask(
                            repositories[index], methodName);
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * Generate list of {@see Callable} tasks.
     * One task will login to random repo in cluster and move document.
     * with body.
     *
     * @return List of tasks
     */
    public static List<Callable<String>> generateMoveNodesTasks(
            final JcrRepository[] repositories,
            final String sourcePath,
            final String destPath) throws RepositoryException {
        final List<Callable<String>> tasks =
                new ArrayList<Callable<String>>();
        final JcrRepository repository = repositories[
                RANDOM.nextInt(repositories.length)];
        final JcrSession session = repository.login();
        final Node sourceFolder = session.getNode(sourcePath);
        final NodeIterator sourceFolderIterator = sourceFolder.getNodes();
        while (sourceFolderIterator.hasNext()) {
            final Node node = sourceFolderIterator.nextNode();

            final MoveDocumentsBenchmarkTest.MoveNodeTask task =
                    new MoveDocumentsBenchmarkTest.MoveNodeTask(
                            repository,
                            node.getIdentifier(),
                            destPath);
            tasks.add(task);
        }

        return tasks;
    }

    public static void validateJcrContent(final Node node, final String expectedContent, final boolean isBas64Encoded) throws Exception {
        Node jcrContent = node.getNode("jcr:content");
        InputStream is = null;
        BufferedInputStream bis = null;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            is = jcrContent.getProperty("jcr:data").getBinary().getStream();
            bis = new BufferedInputStream(is);
            int result = bis.read();
            while (result != -1) {
                byte b = (byte) result;
                buf.write(b);
                result = bis.read();
            }
            String body = isBas64Encoded ?
                    new String(Base64.decodeBase64(buf.toByteArray())) :
                    buf.toString();
            assertTrue(
                    "Document contain appropriate body",
                    body.contains(expectedContent));
        } finally {

            if (bis != null) {
                bis.close();
            }
            if (is != null) {
                is.close();
            }
            if (buf != null) {
                buf.close();
            }
        }

    }

    public static void validateFolder(
            final Node node,
            final String destPath,
            final String expectedContent,
            final boolean isBase64Encoded) throws Exception {
        Assert.assertTrue("Node is folder", node.isNodeType("nt:folder"));
        assertNotNull(
                "The document with '%s' identifier was found!",
                node);
        assertTrue(
                "The document was moved to destination folder!",
                node.getPath().startsWith(destPath));
        NodeIterator iterator = node.getNodes();
        while (iterator.hasNext()) {
            Node item = iterator.nextNode();
            if (item.isNodeType("nt:folder")) {
                validateFolder(item, destPath, expectedContent, isBase64Encoded);
            } else {
                validateJcrContent(item, expectedContent, isBase64Encoded);
            }
        }
    }

    /**
     * Validate that all objects are available in repository
     * after replication.
     *
     * @throws Exception on error
     */
    public static void validateRepository(
            final JcrSession session,
            final Set<String> taskResults,
            final String destPath,
            final String expectedContent,
            final int expectedCount,
            final boolean isBase64Encoded) throws Exception {
        for (String id : taskResults) {
            Node node = session.getNodeByIdentifier(id);
            assertNotNull(
                    "The document with '%s' identifier was found!",
                    node);
            assertTrue(
                    "The document was moved to destination folder!",
                    node.getPath().startsWith(destPath));
            if (node.isNodeType("nt:file")) {
                validateJcrContent(node, expectedContent, isBase64Encoded);
            } else {
                validateFolder(node, destPath, expectedContent, isBase64Encoded);
            }
        }

        NodeIterator destNodeIterator =
                ((Node) session.getNode(destPath)).getNodes();
        while (destNodeIterator.hasNext()) {
            Node node = destNodeIterator.nextNode();
            Assert.assertNotNull("Node could be read", node);
        }

        assertThat(
                "There were created appropriate child nodes count",
                taskResults.size(),
                is(expectedCount));
    }
}
