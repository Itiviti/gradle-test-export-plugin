package com.ullink.testtools.elastic

import com.ullink.testtools.elastic.models.Result
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.elasticsearch.action.bulk.BulkProcessor
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.transport.TransportClient
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult

@Slf4j
class TestExportTask extends Exec {

    @Input
    @Optional
    String port

    @Input
    @Optional
    String clusterName

    @Input
    @Optional
    String host

    @Input
    @Optional
    def properties

    @Input
    @Optional
    def type = "testcase"

    @Internal
    def resultProperties
    @Internal
    BulkProcessor processor
    @Internal
    TransportClient client

    def overrideDefaultProperties(Properties properties) {
        if (getHost() != null) {
            log.error "setting host " + getHost()
            properties.setProperty('host', getHost())
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
        if (properties instanceof Closure) {
            resultProperties = properties.call()
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
                String typeFinal
                if (type instanceof String)
                    typeFinal = type
                if (type instanceof Closure)
                    typeFinal = type.call()
                String id = it.getName() + "_" + it.timestamp
                IndexRequest indexObj = new IndexRequest(index, typeFinal, id)
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