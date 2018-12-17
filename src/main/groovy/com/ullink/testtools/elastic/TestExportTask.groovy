package com.ullink.testtools.elastic

import com.ullink.testtools.elastic.models.Result
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.elasticsearch.action.bulk.BulkProcessor
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.xcontent.XContentType
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import static java.util.Collections.singletonList

@Slf4j
class TestExportTask extends ConventionTask {

    @Input
    @Optional
    String port = "9300"

    @Input
    @Optional
    String clusterName = "elasticsearch"

    @Input
    @Optional
    String host = "127.0.0.1"

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

    @TaskAction
    void exec() {
        ElasticSearchProcessor elasticSearchProcessor = new ElasticSearchProcessor()
        Properties parameters = new Properties()
        parameters.setProperty('host', host)
        parameters.setProperty('port', port)
        parameters.setProperty('clusterName', clusterName)

        def bulkProcessorListener = elasticSearchProcessor.buildBulkProcessorListener(project.logger)
        client = elasticSearchProcessor.buildTransportClient(parameters)
        processor = elasticSearchProcessor.buildBulkRequest(client, bulkProcessorListener)

        def index = indexPrefix + buildTime.format(DateTimeFormatter.ofPattern(indexTimestampPattern))
        index = index.replace('.', '-')

        if (targetDirectory == null) {
            targetDirectory = []
            project.tasks.withType(Test.class).forEach {
                def xmlReport = it.reports.getJunitXml()
                if (xmlReport.destination.exists()) {
                    project.logger.debug("Adding ${xmlReport.destination} to processing as an output of a test task")
                    targetDirectory << xmlReport.getDestination()
                } else {
                    project.logger.debug("Ignoring ${xmlReport.destination} as it does not exist")
                }
            }
        }
        if (targetDirectory instanceof GString) {
            targetDirectory = targetDirectory.toString()
        }
        if (targetDirectory instanceof String) {
            targetDirectory = singletonList(new File(targetDirectory))
        }
        project.logger.info("Found ${targetDirectory.size()} directories to process")
        targetDirectory.each {
            project.logger.info("Processing directory ${it}")
            def files = []
            it.eachFileRecurse(FileType.FILES) {
                if (it.getName().endsWith('.xml')) {
                    project.logger.debug("Found a test file ${it}")
                    files << it
                }
            }
            def list = parseTestFiles(files)
            project.logger.info("Found ${list.size()} test case results to export into ${index}")
            list.each {
                def output = JsonOutput.toJson(it)

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

        project.logger.info("Executing bulk export to Elasticsearch")
        if (!processor.awaitClose(10, TimeUnit.MINUTES)) {
            project.logger.error("Export to Elasticsearch timed out after 10 minutes")
        } else {
            project.logger.info("Executed bulk export to Elasticsearch successfully")
        }
    }

    List<Result> parseTestFiles(List<File> files) {
        def list = []
        files.each { file ->
            def xmlDoc = new XmlSlurper().parse(file)

            def count = 0
            xmlDoc.children().each {
                if (it.name() == "testcase") {
                    Result result = parseTestCase(it)
                    result.timestamp = buildTime.toString()

                    if (enrichment) {
                        result = enrichment.call(result, it)
                    }

                    list << result
                    count += 1
                }
            }
            project.logger.debug("Found ${count} test cases in ${file}")
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
