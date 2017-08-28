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
        String source = """
            {"classname":"com.ullink.ultest.junit.integration.test.elastic.MathTest","failureType":null,"executionTime":1.0,"failureMessage":null,"timestamp":"2017-08-18T03:41:19","name":"subtract","resultType":"SUCCESS"}
         """
        try {
            processor.add(new IndexRequest("testresults-com-ullink-ultest-junit-integration-test-elastic-mathtest-2017-08-19", "testcase", "subtract_2017-08-18T03:41:19").source(source, XContentType.JSON))
            processor.close()
        }
        catch (Exception e) {
            e.printStackTrace()
        }
    }

}