/*************************************************************************
 * ULLINK CONFIDENTIAL INFORMATION
 * _______________________________
 *
 * All Rights Reserved.
 *
 * NOTICE: This file and its content are the property of Ullink. The
 * information included has been classified as Confidential and may
 * not be copied, modified, distributed, or otherwise disseminated, in
 * whole or part, without the express written permission of Ullink.
 ************************************************************************/
package com.ullink.testtools.elastic.models

import spock.lang.Specification

class ResultObjectTest extends Specification {

    def 'should create Test Object'() {
        expect:
        new Result() instanceof Result
    }

}
