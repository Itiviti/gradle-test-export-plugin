package com.ullink.testtools.elastic.models

import spock.lang.Specification

class ResultObjectTest extends Specification {

    def 'should create Test Object'() {
        expect:
        new Result() instanceof Result
    }

}
