package com.ullink.testtools.elastic

import com.ullink.testtools.elastic.models.Result
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.elasticsearch.action.bulk.BulkProcessor
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.transport.TransportClient
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult

@Slf4j
class ElasticTask extends Exec {
    String port
    String clusterName
    String ipAddress
    def properties
    String type = "testcase"

    private resultProperties
    BulkProcessor processor
    TransportClient client

    def overrideDefaultProperties(Properties properties) {
        if (getIpAddress() != null) {
            log.error "setting ip address " + getIpAddress()
            properties.setProperty('ipAddress', getIpAddress())
        }
        if (getClusterName() != null) {
            properties.setProperty('clusterName', getClusterName())
        }
        if (getPort() != null) {
            properties.setProperty('port', getPort())
        }
        return properties
    }


    @Override
    void exec() {
        resultProperties
        if (properties instanceof Closure) {
            resultProperties = properties.call()
            println resultProperties
        }
        if (properties instanceof Map) {
            resultProperties = properties
        }

        ElasticSearchProcessor elasticSearchProcessor = new ElasticSearchProcessor()
        Properties parameters = overrideDefaultProperties(elasticSearchProcessor.getParameters())

        def bulkProcessorListener = elasticSearchProcessor.buildBulkProcessorListener()
        client = elasticSearchProcessor.buildTransportClient(parameters)
        processor = elasticSearchProcessor.buildBulkRequest(client, bulkProcessorListener)

        project.tasks.withType(Test.class).forEach {
            def xmlReport = it.reports.getJunitXml()
            File reportLocation = xmlReport.getDestination()
            def files = []
            reportLocation.eachFileRecurse(FileType.FILES) {
                if (it.getName().endsWith('.xml'))
                    files << it
            }
            def list = parseTestFiles(files)
            list.each {
                def output = JsonOutput.toJson(it)
                String index = "testresults-" + it.getClassname() + "-" + it.timestamp.find("([\\d-]+)")
                index = index.toLowerCase().replace('.', '-')
                String type = type
                String id = it.getName() + "_" + it.timestamp
                println output
                IndexRequest indexObj = new IndexRequest(index, type, id)
                processor.add(indexObj.source(output))
            }
        }
        processor.close()
    }

    def parseTestFiles(List<File> files) {
        def list = []
        files.each {
            def xmlDoc = new XmlSlurper().parse(it)
            String timestamp = xmlDoc.@timestamp

            xmlDoc.children().each {
                if (it.name() == "testcase") {
                    Result result = parseTestCase(it)
                    result.timestamp = timestamp
                    list << result
                }
            }
        }

        list
    }

    def parseTestCase(def p) {
        String testname = p.@name
        Result result = new Result(name: testname)
        def time = Float.parseFloat(p.@time.toString()) * 1000
        result.with {
            classname = p.@classname
            executionTime = time
            result.resultType = TestResult.ResultType.SUCCESS
        }
        p.children().each {
            if (it.name() == 'failure') {
                def node = it
                result.with {
                    failureMessage = node.@message
                    failureType = node.@type
                    resultType = TestResult.ResultType.FAILURE
                }
            }
            if (it.name() == 'skipped') {
                result.resultType = TestResult.ResultType.SKIPPED
            }
        }
        result.properties = resultProperties
        result
    }
}