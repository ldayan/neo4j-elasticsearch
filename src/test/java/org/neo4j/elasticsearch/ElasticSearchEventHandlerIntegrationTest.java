package org.neo4j.elasticsearch;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Get;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.util.TestLogger;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.neo4j.helpers.collection.MapUtil.map;
import static org.neo4j.helpers.collection.MapUtil.stringMap;

public class ElasticSearchEventHandlerIntegrationTest {

    public static final String LABEL = "MyLabel";
    public static final String INDEX = "my_index";
    private GraphDatabaseService db;
    private JestClient client;

    @Before
    public void setUp() throws Exception {
        JestClientFactory factory = new JestClientFactory();
        factory.setHttpClientConfig(new HttpClientConfig
                .Builder("http://localhost:9200")
                .build());
        client = factory.getObject();
        db = new TestGraphDatabaseFactory()
                .newImpermanentDatabaseBuilder()
                .setConfig(config())
                .newGraphDatabase();
    }

    private Map<String, String> config() {
        return stringMap(
                "elasticsearch.host_name", "http://localhost:9200",
                "elasticsearch.node_selection", LABEL,
                "elasticsearch.index_name", INDEX);
    }

    @After
    public void tearDown() throws Exception {
        client.shutdownClient();
        db.shutdown();
    }

    @Test
    public void testAfterCommit() throws Exception {
        Transaction tx = db.beginTx();
        org.neo4j.graphdb.Node node = db.createNode(DynamicLabel.label(LABEL));
        String id = String.valueOf(node.getId());
        node.setProperty("foo", "foobar");
        tx.success();
        tx.close();

        JestResult response = client.execute(new Get.Builder(INDEX, id).build());

        assertEquals(true, response.isSucceeded());
        assertEquals(INDEX, response.getValue("_index"));
        assertEquals(id, response.getValue("_id"));
        assertEquals(LABEL, response.getValue("_type"));


        Map source = response.getSourceAsObject(Map.class);
        assertEquals(asList(LABEL), source.get("labels"));
        assertEquals(id, source.get("id"));
        assertEquals("foobar", source.get("foo"));
    }
}
