package com.ullink.testtools.elastic

import com.ullink.testtools.elastic.models.Result
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.apache.http.HttpHost
import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.elasticsearch.client.RestClient
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestResult

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import static java.util.Collections.singletonList

@Slf4j
class TestExportTask extends DefaultTask {

    @Input
    int port = 9200

    @Input
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

    @TaskAction
    void exec() {
        def client = RestClient.builder(new HttpHost(host, port, 'http'))
                .setFailureListener(new RestClient.FailureListener() {
                    @Override
                    void onFailure(HttpHost host) {
                        logger.error("Failed to upload the test reports.")
                    }
                })
                .setMaxRetryTimeoutMillis(600000)
                .build()

        def index = indexPrefix + buildTime.format(DateTimeFormatter.ofPattern(indexTimestampPattern))
        index = index.replace('.', '-')

        if (targetDirectory == null) {
            targetDirectory = []
            project.tasks.withType(Test.class).forEach {
                def xmlReport = it.reports.getJunitXml()
                if (xmlReport.destination.exists() && !targetDirectory.contains(xmlReport.destination)) {
                    logger.debug("Adding ${xmlReport.destination} to processing as an output of a test task")
                    targetDirectory << xmlReport.destination
                } else {
                    logger.debug("Ignoring ${xmlReport.destination} as it does not exist")
                }
            }
        }
        if (targetDirectory instanceof GString) {
            targetDirectory = targetDirectory.toString()
        }
        if (targetDirectory instanceof String) {
            targetDirectory = singletonList(new File(targetDirectory))
        }
        logger.info("Found ${targetDirectory.size()} directories to process")
        targetDirectory.each {
            logger.info("Processing directory ${it}")
            def files = []
            it.eachFileRecurse(FileType.FILES) {
                if (it.getName().endsWith('.xml')) {
                    logger.debug("Found a test file ${it}")
                    files << it
                }
            }
            def list = parseTestFiles(files)
            logger.info("Found ${list.size()} test case results to export into ${index}")

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

            def indexJson = "{\"index\":{\"_index\": \"$index\", \"_type\": \"$typeFinal\"}}\n"
            list.collate(100).withIndex().each { batchLists, batchIndex ->
                StringBuilder jsonBuilder = new StringBuilder()
                batchLists.each { result -> jsonBuilder.append(indexJson)
                        .append(JsonOutput.toJson(result))
                        .append('\n')
                }

                NStringEntity entity = new NStringEntity(jsonBuilder.toString(), ContentType.APPLICATION_JSON)
                def response = client.performRequest('POST', "/_bulk", ['pretty': 'true'], entity)
                logger.info("Batch ${batchIndex}: " + response.toString())
            }
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
                    failureText = node.text()
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
