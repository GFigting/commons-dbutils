/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.dbutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

/**
 * ResultSetIteratorTest
 */
class ResultSetIteratorTest extends BaseTestCase {

    @Test
    void testCreatesResultSetIteratorTakingThreeArgumentsAndCallsRemove() {

        final ResultSet resultSet = mock(ResultSet.class);
        final ResultSetIterator resultSetIterator = new ResultSetIterator(resultSet, null);
        resultSetIterator.remove();

    }

    @Test
    void testNext() {

        final Iterator<Object[]> iter = new ResultSetIterator(getResultSet());

        assertTrue(iter.hasNext());
        Object[] row = iter.next();
        assertEquals(COLUMN_COUNT, row.length);
        assertEquals("1", row[0]);
        assertEquals("2", row[1]);
        assertEquals("THREE", row[2]);

        assertTrue(iter.hasNext());
        row = iter.next();
        assertEquals(COLUMN_COUNT, row.length);

        assertEquals("4", row[0]);
        assertEquals("5", row[1]);
        assertEquals("SIX", row[2]);

        assertFalse(iter.hasNext());
        assertEquals(0, iter.next().length);
    }

    @Test
    void testRethrowThrowsRuntimeException() {

        final ResultSetIterator resultSetIterator = new ResultSetIterator((ResultSet) null);
        final Throwable throwable = new Throwable();
        final SQLException sqlException = new SQLException(throwable);

        try {
            resultSetIterator.rethrow(sqlException);
            fail("Expecting exception: RuntimeException");
        } catch (final RuntimeException e) {
            assertEquals(ResultSetIterator.class.getName(), e.getStackTrace()[0].getClassName());
        }

    }

}
