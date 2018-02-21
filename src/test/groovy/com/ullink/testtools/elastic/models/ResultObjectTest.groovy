package com.ullink.testtools.elastic.models

import com.ullink.testtools.elastic.TestExportTask
import spock.lang.Specification

class ResultObjectTest extends Specification {

    def 'should create Test Object'() {
        expect:
        new Result() instanceof Result
    }

    def 'sha1 hash'() {
        expect:
        TestExportTask.sha1Hashed('ABCDEF') == '970093678b182127f60bb51b8af2c94d539eca3a'
    }

}
