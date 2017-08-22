package com.ullink.testtools.elastic

import org.elasticsearch.action.bulk.BulkProcessor
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.xcontent.XContentType
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class ElasticSearchProcessorTest {
    private BulkProcessor processor
    private TransportClient client

    @Before
    void setup() {
        ElasticSearchProcessor elasticSearchClient = new ElasticSearchProcessor()
        client = elasticSearchClient.buildTransportClient(elasticSearchClient.parameters)
        def bulkProcessorListener = elasticSearchClient.buildBulkProcessorListener()
        processor = elasticSearchClient.buildBulkRequest(client, bulkProcessorListener)
    }

    @Test
    @Ignore
    void sendJson() {
        String source = "{\"name\":\"subtract\",\"description\":\"com.ullink.ultest.junit.integration.test.elastic.MathTest\",\"status\":\"SUCCESS\",\"executionTime\":0,\"timeStarted\":1501753772044,\"timeFinished\":1501753772045,\"automated\":false,\"systemProperties\":{\"architecture\":\"amd64\",\"osName\":\"Windows 10\",\"javaVersion\":\"1.8.0_131\"},\"projectProperties\":{\"businessLine\":\"connectivity\",\"version\":\"value: 1.1.02\",\"projectName\":\"value: ul-test-integration\",\"buildType\":\"SNAPSHOT\"}}"
        try {
            processor.add(new IndexRequest("movies", "movie", "ajfsgdfbstdccccfy").source(source, XContentType.JSON))
            processor.close()
        }
        catch (Exception e) {
            e.printStackTrace()
        }
    }

}