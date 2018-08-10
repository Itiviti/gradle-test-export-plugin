package com.ullink.testtools.elastic

import com.ullink.testtools.elastic.models.Result
import groovy.io.FileType
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
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

import java.nio.file.Paths
import java.security.MessageDigest
import java.text.SimpleDateFormat
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
    def enrichment

    @Input
    @Optional
    def type = "testcase"

    @Input
    @Optional
    String indexPrefix = "testresults-"

    @Input
    @Optional
    String indexTimestampPattern = "yyyy-MM"

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
        ElasticSearchProcessor elasticSearchProcessor = new ElasticSearchProcessor()
        Properties parameters = overrideDefaultProperties(elasticSearchProcessor.getParameters())

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
                output= new JsonSlurper().parseText(output)
                assert output instanceof Map
                output.remove("timestamp")

                def timestamp = LocalDateTime.parse(it.timestamp)
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

                String id = sha1Hashed(it.getClassname() + it.getName() + it.timestamp)
                IndexRequest indexObj = new IndexRequest(index, typeFinal, id)
                processor.add(indexObj.source(output, XContentType.JSON))
            }
        }

        def InputJSONFile = getEnrichment().featureJsonParser('getFeatureJsonFile')
        def InputJSON = getEnrichment().featureJsonParser('getFeatureJsonParseText')

        def indexFeature = "feature-" + indexPrefix + new SimpleDateFormat(indexTimestampPattern).format(InputJSONFile.lastModified())
        String typeFinal = "feature"

        for (object in InputJSON) {
            String id = sha1Hashed(object.toString())
            IndexRequest indexObjFeature = new IndexRequest(indexFeature, typeFinal, id)
            object.productName= getProperties().product.name
            processor.add(indexObjFeature.source(object, XContentType.JSON))
        }

        processor.close()
    }

    static def sha1Hashed(String value) {
        def messageDigest = MessageDigest.getInstance("SHA-1")
        String hexString = messageDigest.digest(value.getBytes()).collect { String.format('%02x', it) }.join()
        return hexString
    }

    List<Result> parseTestFiles(List<File> files) {
        def list = []
        files.each {
            def xmlDoc = new XmlSlurper().parse(it)
            def fileName = (xmlDoc.@name)
            String timestamp = xmlDoc.@timestamp
            def solutions = getEnrichment().testCaseJsonParser(fileName)

            def filePath = Paths.get("src", "test", "groovy", fileName.toString().replace(".", File.separator) + ".groovy")

            xmlDoc.children().each {
                if (it.name() == "testcase") {
                    Result result = parseTestCase(it,solutions)
                    result.timestamp = timestamp
                    result.filePath = filePath
                    list << result
                }
            }
        }
        list
    }

    def parseTestCase(def p, def solution) {
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
        result.feature = getEnrichment().resolveFeature(p, solution)
        result.steps =  getEnrichment().resolveSteps(p,solution)
        result.properties = resolveProperties(p)
        result.projectName= project.getName()

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