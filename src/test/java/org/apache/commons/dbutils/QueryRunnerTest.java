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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.dbutils.handlers.ArrayHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@SuppressWarnings("boxing") // test code
@ExtendWith(MockitoExtension.class)
class QueryRunnerTest {
    //
    // Random tests
    //
    final class MyBean {
        private int a;
        private double b;
        private String c;

        public int getA() {
            return a;
        }

        public double getB() {
            return b;
        }

        public String getC() {
            return c;
        }

        public void setA(final int a) {
            this.a = a;
        }

        public void setB(final double b) {
            this.b = b;
        }

        public void setC(final String c) {
            this.c = c;
        }
    }

    private QueryRunner runner;

    private ArrayHandler handler;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private DataSource dataSource;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Connection conn;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private PreparedStatement prepStmt;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Statement stmt;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private CallableStatement call;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ParameterMetaData meta;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ResultSet results;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ResultSetMetaData resultsMeta;

    //
    // Batch test cases
    //

    // helper method for calling batch when an exception is expected
    private void callBatchWithException(final String sql, final Object[][] params) throws Exception {
        when(meta.getParameterCount()).thenReturn(2);
        boolean caught = false;

        try {
            runner.batch(sql, params);

            verify(prepStmt, times(1)).getParameterMetaData();
            verify(prepStmt, times(2)).addBatch();
            verify(prepStmt, times(1)).executeBatch();
            verify(prepStmt, times(1)).close();    // make sure the statement is closed
            verify(conn, times(1)).close();    // make sure the connection is closed
        } catch (final SQLException e) {
            System.out.println("[TEST] The following exception is expected:");
            System.out.println(e);
            caught = true;
        }

        if (!caught) {
            fail("Exception never thrown, but expected");
        }
    }

    // helper method for calling execute when an exception is expected
    private void callExecuteWithException(final Object... params) throws Exception {
        boolean caught = false;

        try {
            when(meta.getParameterCount()).thenReturn(2);
            runner.query("{call my_proc(?, ?)}", handler, params);

        } catch (final SQLException e) {
            caught = true;
        }

        if (!caught) {
            fail("Exception never thrown, but expected");
        }
    }

    // helper method for calling execute when an exception is expected
    private void callExecuteWithResultSetWithException(final Object... params) throws Exception {
        boolean caught = false;

        try {
            when(meta.getParameterCount()).thenReturn(2);
            runner.execute("{call my_proc(?, ?)}", handler, params);

        } catch (final SQLException e) {
            caught = true;
        }

        if (!caught) {
            fail("Exception never thrown, but expected");
        }
    }

    private void callGoodBatch(final Connection conn, final Object[][] params) throws Exception {
        when(meta.getParameterCount()).thenReturn(2);
        runner.batch(conn, "select * from blah where ? = ?", params);

        verify(prepStmt, times(1)).getParameterMetaData();
        verify(prepStmt, times(2)).addBatch();
        verify(prepStmt, times(1)).executeBatch();
        // make sure we closed the statement
        verify(prepStmt, times(1)).close();
        // make sure we do not close the connection, since QueryRunner.batch(Connection, String, Object[][]) does not close connections
        verify(conn, times(0)).close();
    }

    private void callGoodBatch(final Object[][] params) throws Exception {
        callGoodBatch(params, true);
    }

    private void callGoodBatch(final Object[][] params, final boolean pmdCheck) throws Exception {
        when(meta.getParameterCount()).thenReturn(2);
        runner.batch("select * from blah where ? = ?", params);

        verify(prepStmt, times(pmdCheck ? 1 : 0)).getParameterMetaData();
        verify(prepStmt, times(2)).addBatch();
        verify(prepStmt, times(1)).executeBatch();
        verify(prepStmt, times(1)).close();    // make sure we closed the statement
        verify(conn, times(1)).close();    // make sure we closed the connection
    }

    private void callGoodExecute() throws Exception {
        when(call.execute()).thenReturn(false);
        when(call.getUpdateCount()).thenReturn(3);

        when(meta.getParameterCount()).thenReturn(2);
        int result = runner.execute("{call my_proc(?, ?)}", "unit", "test");

        assertEquals(3, result);

        verify(call, times(1)).execute();
        verify(call, times(1)).close();    // make sure we closed the statement
        verify(conn, times(1)).close();    // make sure we do not close the connection

        // call the other variation of query
        when(meta.getParameterCount()).thenReturn(0);
        result = runner.execute("{call my_proc()}");

        assertEquals(3, result);

        verify(call, times(2)).execute();
        verify(call, times(2)).close();    // make sure we closed the statement
        verify(conn, times(2)).close();    // make sure we do not close the connection

        // Test single OUT parameter
        when(meta.getParameterCount()).thenReturn(1);
        when(call.getObject(1)).thenReturn(42);
        final OutParameter<Integer> intParam =
            new OutParameter<>(Types.INTEGER, Integer.class);
        result = runner.execute("{?= call my_proc()}", intParam);

        assertEquals(42, intParam.getValue().intValue());
        assertEquals(3, result);

        verify(call, times(3)).execute();
        verify(call, times(3)).close();    // make sure we closed the statement
        verify(conn, times(3)).close();    // make sure we do not close the connection

        // Test OUT parameters with IN parameters
        when(meta.getParameterCount()).thenReturn(3);
        when(call.getObject(1)).thenReturn(4242);
        intParam.setValue(null);
        result = runner.execute("{?= call my_proc(?, ?)}", intParam, "unit", "test");

        assertEquals(4242, intParam.getValue().intValue());
        assertEquals(3, result);

        verify(call, times(4)).execute();
        verify(call, times(4)).close();    // make sure we closed the statement
        verify(conn, times(4)).close();    // make sure we do not close the connection

        // Test INOUT parameters
        when(meta.getParameterCount()).thenReturn(3);
        when(call.getObject(1)).thenReturn(24);
        when(call.getObject(3)).thenReturn("out");
        intParam.setValue(null);
        final OutParameter<String> stringParam =
            new OutParameter<>(Types.VARCHAR, String.class, "in");
        result = runner.execute("{?= call my_proc(?, ?)}", intParam, "test", stringParam);

        assertEquals(24, intParam.getValue().intValue());
        assertEquals("out", stringParam.getValue());
        assertEquals(3, result);

        verify(call, times(5)).execute();
        verify(call, times(5)).close();    // make sure we closed the statement
        verify(conn, times(5)).close();    // make sure we do not close the connection
    }

    //
    // Execute tests
    //
    private void callGoodExecute(final Connection conn) throws Exception {
        when(call.execute()).thenReturn(false);
        when(call.getUpdateCount()).thenReturn(3);

        when(meta.getParameterCount()).thenReturn(2);
        int result = runner.execute(conn, "{call my_proc(?, ?)}", "unit", "test");

        assertEquals(3, result);

        verify(call, times(1)).execute();
        verify(call, times(1)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection

        // call the other variation of query
        when(meta.getParameterCount()).thenReturn(0);
        result = runner.execute(conn, "{call my_proc()}");

        assertEquals(3, result);

        verify(call, times(2)).execute();
        verify(call, times(2)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection

        // Test single OUT parameter
        when(meta.getParameterCount()).thenReturn(1);
        when(call.getObject(1)).thenReturn(42);
        final OutParameter<Integer> intParam =
            new OutParameter<>(Types.INTEGER, Integer.class);
        result = runner.execute(conn, "{?= call my_proc()}", intParam);

        assertEquals(42, intParam.getValue().intValue());
        assertEquals(3, result);

        verify(call, times(3)).execute();
        verify(call, times(3)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection

        // Test OUT parameters with IN parameters
        when(meta.getParameterCount()).thenReturn(3);
        when(call.getObject(1)).thenReturn(4242);
        intParam.setValue(null);
        result = runner.execute(conn, "{?= call my_proc(?, ?)}", intParam, "unit", "test");

        assertEquals(4242, intParam.getValue().intValue());
        assertEquals(3, result);

        verify(call, times(4)).execute();
        verify(call, times(4)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection

        // Test INOUT parameters
        when(meta.getParameterCount()).thenReturn(3);
        when(call.getObject(1)).thenReturn(24);
        when(call.getObject(3)).thenReturn("out");
        intParam.setValue(null);
        final OutParameter<String> stringParam =
            new OutParameter<>(Types.VARCHAR, String.class, "in");
        result = runner.execute(conn, "{?= call my_proc(?, ?)}", intParam, "test", stringParam);

        assertEquals(24, intParam.getValue().intValue());
        assertEquals("out", stringParam.getValue());
        assertEquals(3, result);

        verify(call, times(5)).execute();
        verify(call, times(5)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection
    }

    private void callGoodExecuteWithResultSet() throws Exception {
        when(call.execute()).thenReturn(true);

        when(meta.getParameterCount()).thenReturn(2);
        runner.execute("{call my_proc(?, ?)}", handler, "unit", "test");

        verify(call, times(1)).execute();
        verify(results, times(1)).close();
        verify(call, times(1)).close();    // make sure we closed the statement
        verify(conn, times(1)).close();    // make sure we do not close the connection

        // call the other variation of query
        when(meta.getParameterCount()).thenReturn(0);
        runner.execute("{call my_proc()}", handler);

        verify(call, times(2)).execute();
        verify(results, times(2)).close();
        verify(call, times(2)).close();    // make sure we closed the statement
        verify(conn, times(2)).close();    // make sure we do not close the connection

        // Test single OUT parameter
        when(meta.getParameterCount()).thenReturn(1);
        when(call.getObject(1)).thenReturn(42);
        final OutParameter<Integer> intParam =
            new OutParameter<>(Types.INTEGER, Integer.class);
        runner.execute("{?= call my_proc()}", handler, intParam);

        assertEquals(42, intParam.getValue().intValue());

        verify(call, times(3)).execute();
        verify(results, times(3)).close();
        verify(call, times(3)).close();    // make sure we closed the statement
        verify(conn, times(3)).close();    // make sure we do not close the connection

        // Test OUT parameters with IN parameters
        when(meta.getParameterCount()).thenReturn(3);
        when(call.getObject(1)).thenReturn(4242);
        intParam.setValue(null);
        runner.execute("{?= call my_proc(?, ?)}", handler, intParam, "unit", "test");

        assertEquals(4242, intParam.getValue().intValue());

        verify(call, times(4)).execute();
        verify(results, times(4)).close();
        verify(call, times(4)).close();    // make sure we closed the statement
        verify(conn, times(4)).close();    // make sure we do not close the connection

        // Test INOUT parameters
        when(meta.getParameterCount()).thenReturn(3);
        when(call.getObject(1)).thenReturn(24);
        when(call.getObject(3)).thenReturn("out");
        intParam.setValue(null);
        final OutParameter<String> stringParam =
            new OutParameter<>(Types.VARCHAR, String.class, "in");
        runner.execute("{?= call my_proc(?, ?)}", handler, intParam, "test", stringParam);

        assertEquals(24, intParam.getValue().intValue());
        assertEquals("out", stringParam.getValue());

        verify(call, times(5)).execute();
        verify(results, times(5)).close();
        verify(call, times(5)).close();    // make sure we closed the statement
        verify(conn, times(5)).close();    // make sure we do not close the connection
    }

    private void callGoodExecuteWithResultSet(final Connection conn) throws Exception {
        when(call.execute()).thenReturn(true);

        when(meta.getParameterCount()).thenReturn(2);
        runner.execute(conn, "{call my_proc(?, ?)}", handler, "unit", "test");

        verify(call, times(1)).execute();
        verify(results, times(1)).close();
        verify(call, times(1)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection

        // call the other variation of query
        when(meta.getParameterCount()).thenReturn(0);
        runner.execute(conn, "{call my_proc()}", handler);

        verify(call, times(2)).execute();
        verify(results, times(2)).close();
        verify(call, times(2)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection

        // Test single OUT parameter
        when(meta.getParameterCount()).thenReturn(1);
        when(call.getObject(1)).thenReturn(42);
        final OutParameter<Integer> intParam =
            new OutParameter<>(Types.INTEGER, Integer.class);
        runner.execute(conn, "{?= call my_proc()}", handler, intParam);

        assertEquals(42, intParam.getValue().intValue());

        verify(call, times(3)).execute();
        verify(results, times(3)).close();
        verify(call, times(3)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection

        // Test OUT parameters with IN parameters
        when(meta.getParameterCount()).thenReturn(3);
        when(call.getObject(1)).thenReturn(4242);
        intParam.setValue(null);
        runner.execute(conn, "{?= call my_proc(?, ?)}", handler, intParam, "unit", "test");

        assertEquals(4242, intParam.getValue().intValue());

        verify(call, times(4)).execute();
        verify(results, times(4)).close();
        verify(call, times(4)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection

        // Test INOUT parameters
        when(meta.getParameterCount()).thenReturn(3);
        when(call.getObject(1)).thenReturn(24);
        when(call.getObject(3)).thenReturn("out");
        intParam.setValue(null);
        final OutParameter<String> stringParam =
            new OutParameter<>(Types.VARCHAR, String.class, "in");
        runner.execute(conn, "{?= call my_proc(?, ?)}", handler, intParam, "test", stringParam);

        assertEquals(24, intParam.getValue().intValue());
        assertEquals("out", stringParam.getValue());

        verify(call, times(5)).execute();
        verify(results, times(5)).close();
        verify(call, times(5)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection
    }

    private void callGoodQuery() throws Exception {
        when(meta.getParameterCount()).thenReturn(2);
        String sql = "select * from blah where ? = ?";
        runner.query(sql, handler, "unit", "test");

        verify(prepStmt, times(1)).executeQuery();
        verify(results, times(1)).close();
        verify(prepStmt, times(1)).close();    // make sure we closed the statement
        verify(conn, times(1)).close();    // make sure we closed the connection

        // call the other variation of query
        sql = "select * from blah";
        runner.query(sql, handler);

        verify(stmt, times(1)).executeQuery(sql);
        verify(results, times(2)).close();
        verify(stmt, times(1)).close();    // make sure we closed the statement
        verify(conn, times(2)).close();    // make sure we closed the connection
    }

    //
    // Query test cases
    //
    private void callGoodQuery(final Connection conn) throws Exception {
        when(meta.getParameterCount()).thenReturn(2);
        String sql = "select * from blah where ? = ?";
        runner.query(conn, sql, handler, "unit", "test");

        verify(prepStmt, times(1)).executeQuery();
        verify(results, times(1)).close();
        verify(prepStmt, times(1)).close();    // make sure we closed the statement
        // make sure we do not close the connection, since QueryRunner.query(Connection, String, ResultSetHandler<T>, Object...) does not close connections
        verify(conn, times(0)).close();
        // call the other variation of query
        sql = "select * from blah";
        runner.query(conn, sql, handler);

        verify(stmt, times(1)).executeQuery(sql);
        verify(results, times(2)).close();
        verify(stmt, times(1)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection, see above
    }

    private void callGoodUpdate() throws Exception {
        when(meta.getParameterCount()).thenReturn(2);
        String sql = "update blah set ? = ?";
        runner.update(sql, "unit", "test");

        verify(prepStmt, times(1)).executeUpdate();
        verify(prepStmt, times(1)).close();    // make sure we closed the statement
        verify(conn, times(1)).close();    // make sure we closed the connection

        // call the other variation
        when(meta.getParameterCount()).thenReturn(0);
        sql = "update blah set unit = test";
        runner.update(sql);

        verify(stmt, times(1)).executeUpdate(sql);
        verify(stmt, times(1)).close();    // make sure we closed the statement
        verify(conn, times(2)).close();    // make sure we closed the connection

        // call the other variation
        when(meta.getParameterCount()).thenReturn(1);
        sql = "update blah set unit = ?";
        runner.update(sql, "test");

        verify(prepStmt, times(2)).executeUpdate();
        verify(prepStmt, times(2)).close();    // make sure we closed the statement
        verify(conn, times(3)).close();    // make sure we closed the connection
    }

    //
    // Update test cases
    //
    private void callGoodUpdate(final Connection conn) throws Exception {
        when(meta.getParameterCount()).thenReturn(2);
        runner.update(conn, "update blah set ? = ?", "unit", "test");

        verify(prepStmt, times(1)).executeUpdate();
        verify(prepStmt, times(1)).close();    // make sure we closed the statement
        // make sure we do not close the connection, since QueryRunner.update(Connection, String, Object...) does not close connections
        verify(conn, times(0)).close();
        // call the other variation
        when(meta.getParameterCount()).thenReturn(0);
        final String sql = "update blah set unit = test";
        runner.update(conn, sql);

        verify(stmt, times(1)).executeUpdate(sql);
        verify(stmt, times(1)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection, see above

        // call the other variation
        when(meta.getParameterCount()).thenReturn(1);
        runner.update(conn, "update blah set unit = ?", "test");

        verify(prepStmt, times(2)).executeUpdate();
        verify(prepStmt, times(2)).close();    // make sure we closed the statement
        verify(conn, times(0)).close();    // make sure we do not close the connection, see above
    }

    /** Helper method for calling batch when an exception is expected. */
    private void callQueryWithException(final Object... params) throws Exception {
        boolean caught = false;

        try {
            when(meta.getParameterCount()).thenReturn(2);
            final String sql = "select * from blah where ? = ?";
            runner.query(sql, handler, params);

            verify(prepStmt, never()).close(); // make sure the statement is still open
            verify(prepStmt, times(1)).executeQuery();
            verify(prepStmt, times(1)).close(); // make sure we closed the statement
            verify(results, times(1)).close();
            verify(conn, times(1)).close(); // make sure we closed the connection
        } catch (final SQLException e) {
            caught = true;
        }

        if (!caught) {
            fail("Exception never thrown, but expected");
        }
    }

    // helper method for calling batch when an exception is expected
    private void callUpdateWithException(final Object... params) throws Exception {
        boolean caught = false;

        try {
            when(meta.getParameterCount()).thenReturn(2);
            final String sql = "select * from blah where ? = ?";
            runner.update(sql, params);

            verify(prepStmt, times(1)).executeUpdate();
            verify(prepStmt, times(1)).close(); // make sure we closed the statement
            verify(conn, times(1)).close(); // make sure we closed the connection
        } catch (final SQLException e) {
            caught = true;
        }

        if (!caught) {
            fail("Exception never thrown, but expected");
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(conn);

        when(conn.prepareStatement(any(String.class))).thenReturn(prepStmt);
        when(prepStmt.getParameterMetaData()).thenReturn(meta);
        when(prepStmt.executeQuery()).thenReturn(results);

        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any(String.class))).thenReturn(results);

        when(conn.prepareCall(any(String.class))).thenReturn(call);
        when(call.getParameterMetaData()).thenReturn(meta);
        when(call.getResultSet()).thenReturn(results);
        when(call.getMoreResults()).thenReturn(false);

        when(results.next()).thenReturn(false);

         handler = new ArrayHandler();
         runner = new QueryRunner(dataSource);
    }

    @Test
    void testAddBatchExceptionOnAdd() throws Exception {
        final String[][] params = { { "unit", "unit" }, { "test", "test" } };

        doThrow(new SQLException()).when(prepStmt).addBatch();

        callBatchWithException("select * from blah where ? = ?", params);
    }

    @Test
    void testBadPrepareConnection() throws Exception {
        assertThrows(SQLException.class, () -> {
            runner = new QueryRunner();
            runner.update("update blah set unit = test");
        });
    }

    @Test
    void testExecuteBatchExceptionOnExec() throws Exception {
        final String[][] params = { { "unit", "unit" }, { "test", "test" } };

        doThrow(new SQLException()).when(prepStmt).executeBatch();

        callBatchWithException("select * from blah where ? = ?", params);
    }

    @Test
    void testExecuteException() throws Exception {
        callExecuteWithException(handler, "unit", "test");
    }

    @Test
    void testExecuteQueryException() throws Exception {
        callQueryWithException(handler, "unit", "test");
    }

    @Test
    void testExecuteUpdateException() throws Exception {
        doThrow(new SQLException()).when(prepStmt).executeUpdate();

        callUpdateWithException("unit", "test");
    }

    @Test
    void testExecuteWithMultipleResultSets() throws Exception {
        when(call.execute()).thenReturn(true);
        when(call.getMoreResults()).thenAnswer(new Answer<Boolean>() {
            private int count = 1;
            @Override
            public Boolean answer(final InvocationOnMock invocation) {
                return ++count <= 3;
            }
        });
        when(meta.getParameterCount()).thenReturn(0);
        final List<Object[]> objects = runner.execute("{call my_proc()}", handler);

        assertEquals(3, objects.size());
        verify(call, times(1)).execute();
        verify(results, times(3)).close();
        verify(call, times(1)).close();    // make sure we closed the statement
        verify(conn, times(1)).close();    // make sure we close the connection

    }

    @Test
    void testExecuteWithResultSetException() throws Exception {
        callExecuteWithResultSetWithException(handler, "unit", "test");
    }

    @Test
    void testFillStatementWithBean() throws Exception {
        final MyBean bean = new MyBean();
        when(meta.getParameterCount()).thenReturn(3);
        runner.fillStatementWithBean(prepStmt, bean, "a", "b", "c");
    }

    @Test
    void testFillStatementWithBeanNullNames() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            final MyBean bean = new MyBean();
            runner.fillStatementWithBean(prepStmt, bean, "a", "b", null);
        });
    }

    @Test
    void testGoodBatch() throws Exception {
        final String[][] params = { { "unit", "unit" }, { "test", "test" } };

        callGoodBatch(params);
    }

    @Test
    void testGoodBatchDefaultConstructor() throws Exception {
        runner = new QueryRunner();
        final String[][] params = { { "unit", "unit" }, { "test", "test" } };

        callGoodBatch(conn, params);
    }

    @Test
    void testGoodBatchInsert() throws Exception {
        results = mock(ResultSet.class);
        resultsMeta = mock(ResultSetMetaData.class);

        when(meta.getParameterCount()).thenReturn(2);
        when(conn.prepareStatement(any(String.class), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(prepStmt);
        when(prepStmt.getGeneratedKeys()).thenReturn(results);
        when(results.next()).thenReturn(true).thenReturn(true).thenReturn(false);

        final ResultSetHandler<List<Object>> handler = rs -> {
            final List<Object> objects = new ArrayList<>();
            while (rs.next()) {
                objects.add(new Object());
            }
            return objects;
        };

        final Object[][] params = new Object[2][2];
        params[0][0] = "Test";
        params[0][1] = "Blah";
        params[1][0] = "Test2";
        params[1][1] = "Blah2";

        final List<Object> generatedKeys = runner.insertBatch("INSERT INTO blah(col1, col2) VALUES(?,?)", handler, params);

        verify(prepStmt, times(2)).addBatch();
        verify(prepStmt, times(1)).executeBatch();
        verify(prepStmt, times(1)).close(); // make sure we closed the statement
        verify(conn, times(1)).close(); // make sure we closed the connection

        assertEquals(2, generatedKeys.size());
    }

    @Test
    void testGoodBatchPmdTrue() throws Exception {
        runner = new QueryRunner(dataSource, true);
        final String[][] params = { { "unit", "unit" }, { "test", "test" } };

        callGoodBatch(params, false);
    }

    @Test
    void testGoodExecute() throws Exception {
        callGoodExecute();
    }

    @Test
    void testGoodExecuteDefaultConstructor() throws Exception {
        runner = new QueryRunner();
        callGoodExecute(conn);
    }

    @Test
    void testGoodExecutePmdTrue() throws Exception {
        runner = new QueryRunner(true);
        callGoodExecute(conn);
    }

    @Test
    void testGoodExecuteWithResultSet() throws Exception {
        callGoodExecuteWithResultSet();
    }

    @Test
    void testGoodExecuteWithResultSetDefaultConstructor() throws Exception {
        runner = new QueryRunner();
        callGoodExecuteWithResultSet(conn);
    }

    @Test
    void testGoodExecuteWithResultSetPmdTrue() throws Exception {
        runner = new QueryRunner(true);
        callGoodExecuteWithResultSet(conn);
    }

    @Test
    void testGoodInsert() throws Exception {
        results = mock(ResultSet.class);

        when(meta.getParameterCount()).thenReturn(2);
        when(conn.prepareStatement(any(String.class), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(prepStmt);
        when(prepStmt.getGeneratedKeys()).thenReturn(results);
        when(results.next()).thenReturn(true).thenReturn(false);
        when(results.getObject(1)).thenReturn(1L);

        final Long generatedKey = runner.insert("INSERT INTO blah(col1, col2) VALUES(?,?)", new ScalarHandler<>(), "unit", "test");

        verify(prepStmt, times(1)).executeUpdate();
        verify(prepStmt, times(1)).close(); // make sure we closed the statement
        verify(conn, times(1)).close(); // make sure we closed the connection

        assertEquals(1L, generatedKey.longValue());
    }

    @Test
    void testGoodQuery() throws Exception {
        callGoodQuery();
    }

    @Test
    void testGoodQueryDefaultConstructor() throws Exception {
        runner = new QueryRunner();
        callGoodQuery(conn);
    }

    @Test
    void testGoodQueryPmdTrue() throws Exception {
        runner = new QueryRunner(true);
        callGoodQuery(conn);
    }

    @Test
    void testGoodUpdate() throws Exception {
        callGoodUpdate();
    }

    @Test
    void testGoodUpdateDefaultConstructor() throws Exception {
        runner = new QueryRunner();
        callGoodUpdate(conn);
    }

    @Test
    void testGoodUpdatePmdTrue() throws Exception {
        runner = new QueryRunner(true);
        callGoodUpdate(conn);
    }

    @Test
    void testNoParamsExecute() throws Exception {
        callGoodExecute();
    }

    @Test
    void testNoParamsExecuteWithResultSet() throws Exception {
        callExecuteWithResultSetWithException();
    }

    @Test
    void testNoParamsQuery() throws Exception {
        callGoodQuery();
    }

    @Test
    void testNoParamsUpdate() throws Exception {
        callGoodUpdate();
    }

    @Test
    void testNullConnectionBatch() throws Exception {
        assertThrows(SQLException.class, () -> {
            final String[][] params = {{"unit", "unit"}, {"test", "test"}};

            when(dataSource.getConnection()).thenReturn(null);

            runner.batch("select * from blah where ? = ?", params);
        });
    }

    @Test
    void testNullConnectionExecute() throws Exception {
        assertThrows(SQLException.class, () -> {
            when(dataSource.getConnection()).thenReturn(null);

            runner.execute("{call my_proc(?, ?)}", "unit", "test");
        });
    }

    @Test
    void testNullConnectionExecuteWithResultSet() throws Exception {
        assertThrows(SQLException.class, () -> {
            when(dataSource.getConnection()).thenReturn(null);

            runner.execute("{call my_proc(?, ?)}", handler, "unit", "test");
        });
    }

    @Test
    void testNullConnectionQuery() throws Exception {
        assertThrows(SQLException.class, () -> {
            when(dataSource.getConnection()).thenReturn(null);

            runner.query("select * from blah where ? = ?", handler, "unit", "test");
        });
    }

    @Test
    void testNullConnectionUpdate() throws Exception {
        assertThrows(SQLException.class, () -> {
            when(dataSource.getConnection()).thenReturn(null);

            runner.update("select * from blah where ? = ?", "unit", "test");
        });
    }

    @Test
    void testNullHandlerExecute() throws Exception {
        assertThrows(SQLException.class, () -> {
            when(meta.getParameterCount()).thenReturn(2);

            runner.execute("{call my_proc(?, ?)}");
        });
    }

    @Test
    void testNullHandlerExecuteWithResultSet() throws Exception {
        assertThrows(SQLException.class, () ->
            runner.execute("{call my_proc(?, ?)}", (ResultSetHandler) null));
    }

    @Test
    void testNullHandlerQuery() throws Exception {
        assertThrows(SQLException.class, () ->
            runner.query("select * from blah where ? = ?", null));
    }

    //
    // Execute with ResultSetHandler
    //

    @Test
    void testNullParamsArgBatch() throws Exception {
        assertThrows(SQLException.class, () ->
            runner.batch("select * from blah where ? = ?", null));
    }

    @Test
    void testNullParamsBatch() throws Exception {
        final String[][] params = { { null, "unit" }, { "test", null } };

        callGoodBatch(params);
    }

    @Test
    void testNullSqlBatch() throws Exception {
        assertThrows(SQLException.class, () -> {
            final String[][] params = {{"unit", "unit"}, {"test", "test"}};

            runner.batch(null, params);
        });
    }

    @Test
    void testNullSqlExecute() throws Exception {
        assertThrows(SQLException.class, () ->
            runner.execute(null));
    }

    @Test
    void testNullSqlExecuteWithResultSet() throws Exception {
        assertThrows(SQLException.class, () ->
            runner.execute(null, handler));
    }

    @Test
    void testNullSqlQuery() throws Exception {
        assertThrows(SQLException.class, () ->
            runner.query(null, handler));
    }

    @Test
    void testNullSqlUpdate() throws Exception {
        assertThrows(SQLException.class, () ->
            runner.update(null));
    }

    @Test
    void testStatementConfiguration() throws Exception {
        final StatementConfiguration stmtConfig = new StatementConfiguration(1, 2, 3, 4, 5);
        final QueryRunner queryRunner = new QueryRunner(stmtConfig);
        queryRunner.prepareStatement(conn, "select 1");

        verify(prepStmt).setFetchDirection(eq(1));
        verify(prepStmt).setFetchSize(eq(2));
        verify(prepStmt).setMaxFieldSize(eq(3));
        verify(prepStmt).setMaxRows(eq(4));
        verify(prepStmt).setQueryTimeout(eq(5));
    }

    @Test
    void testTooFewParamsBatch() throws Exception {
        final String[][] params = { { "unit" }, { "test" } };

        callBatchWithException("select * from blah where ? = ?", params);
    }

    @Test
    void testTooFewParamsExecute() throws Exception {
        callExecuteWithException("unit");
    }

    @Test
    void testTooFewParamsExecuteWithResultSet() throws Exception {
        callExecuteWithResultSetWithException("unit");
    }

    @Test
    void testTooFewParamsQuery() throws Exception {
        callQueryWithException("unit");
    }

    @Test
    void testTooFewParamsUpdate() throws Exception {
        callUpdateWithException("unit");
    }

    @Test
    void testTooManyParamsBatch() throws Exception {
        final String[][] params = { { "unit", "unit", "unit" }, { "test", "test", "test" } };

        callBatchWithException("select * from blah where ? = ?", params);
    }

    @Test
    void testTooManyParamsExecute() throws Exception {
        callExecuteWithException("unit", "test", "fail");
    }

    @Test
    void testTooManyParamsExecuteWithResultSet() throws Exception {
        callExecuteWithResultSetWithException("unit", "test", "fail");
    }

    @Test
    void testTooManyParamsQuery() throws Exception {
        callQueryWithException("unit", "test", "fail");
    }

    @Test
    void testTooManyParamsUpdate() throws Exception {
        callUpdateWithException("unit", "test", "fail");
    }
}
