package com.ullink.testtools.elastic

import com.ullink.testtools.elastic.models.Result
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.elasticsearch.action.bulk.BulkProcessor
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.xcontent.XContentType
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import static java.util.Collections.singletonList

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
    def targetDirectory

    @Input
    @Optional
    Closure<Result> enrichment

    @Input
    @Optional
    def type = "testcase"

    @Input
    @Optional
    String indexPrefix = "testresults-"

    @Input
    @Optional
    String indexTimestampPattern = "yyyy-MM"

    @Input
    @Optional
    LocalDateTime buildTime = LocalDateTime.now()

    @Internal
    BulkProcessor processor
    @Internal
    TransportClient client

    static def overrideDefaultProperties(TestExportTask task, Properties properties) {
        if (task.host != null) {
            log.info "setting host ${task.host}" +
            properties.setProperty('host', task.host)
        }
        if (task.clusterName != null) {
            properties.setProperty('clusterName', task.clusterName)
        }
        if (task.port != null) {
            properties.setProperty('port', task.port)
        }

        return properties
    }

    @Override
    void exec() {
        ElasticSearchProcessor elasticSearchProcessor = new ElasticSearchProcessor()
        Properties parameters = overrideDefaultProperties(this, elasticSearchProcessor.getParameters())

        def bulkProcessorListener = elasticSearchProcessor.buildBulkProcessorListener()
        client = elasticSearchProcessor.buildTransportClient(parameters)
        processor = elasticSearchProcessor.buildBulkRequest(client, bulkProcessorListener)

        if (targetDirectory == null) {
            targetDirectory = []
            project.tasks.withType(Test.class).forEach {
                def xmlReport = it.reports.getJunitXml()
                targetDirectory << xmlReport.getDestination()
            }
        }
        if (targetDirectory instanceof GString) {
            targetDirectory = targetDirectory.toString()
        }
        if (targetDirectory instanceof String) {
            targetDirectory = singletonList(new File(targetDirectory))
        }
        targetDirectory.each {
            def files = []
            it.eachFileRecurse(FileType.FILES) {
                if (it.getName().endsWith('.xml'))
                    files << it
            }
            def list = parseTestFiles(files)
            list.each {
                def output = JsonOutput.toJson(it)
                def timestamp = buildTime
                String index = indexPrefix + timestamp.format(DateTimeFormatter.ofPattern(indexTimestampPattern))
                index = index.replace('.', '-')

                String typeFinal
                switch (type) {
                    case GString:
                        typeFinal = type.toString()
                        break
                    case String:
                        typeFinal = type
                        break
                    case Closure:
                        typeFinal = type.call()
                        break
                    default:
                        throw new IllegalArgumentException("'type' attribute of type ${type.getClass()} is not supported")
                }

                IndexRequest indexObj = new IndexRequest(index, typeFinal)
                processor.add(indexObj.source(output, XContentType.JSON))
            }
        }

        processor.close()
    }

    List<Result> parseTestFiles(List<File> files) {
        def list = []
        files.each { file ->
            def xmlDoc = new XmlSlurper().parse(file)

            xmlDoc.children().each {
                if (it.name() == "testcase") {
                    Result result = parseTestCase(it)
                    result.timestamp = buildTime.toString()

                    if (enrichment) {
                        result = enrichment.call(result, it)
                    }

                    list << result
                }
            }
        }
        list
    }

    def parseTestCase(def p) {
        String testName = p.@name
        Result result = new Result(name: testName)
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
        result.properties = resolveProperties(p)
        result.projectName = project.getName()

        result
    }

    def resolveProperties(testCase) {
        if (properties instanceof Closure) {
            return properties.call(testCase)
        }
        if (properties instanceof Map) {
            return properties
        }
        return null
    }

}
