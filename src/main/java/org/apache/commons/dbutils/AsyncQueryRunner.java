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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.sql.DataSource;

/**
 * Executes SQL queries with pluggable strategies for handling
 * {@code ResultSet}s.  This class is thread safe.
 *
 * @see ResultSetHandler
 * @since 1.4
 */
public class AsyncQueryRunner extends AbstractQueryRunner {

    /**
     * @deprecated No longer used by this class. Will be removed in a future version.
     * Class that encapsulates the continuation for batch calls.
     */
    @Deprecated
    protected class BatchCallableStatement implements Callable<int[]> {
        private final String sql;
        private final Object[][] params;
        private final Connection conn;
        private final boolean closeConn;
        private final PreparedStatement ps;

        /**
         * Creates a new BatchCallableStatement instance.
         *
         * @param sql The SQL statement to execute.
         * @param params An array of query replacement parameters.  Each row in
         *        this array is one set of batch replacement values.
         * @param conn The connection to use for the batch call.
         * @param closeConn True if the connection should be closed, false otherwise.
         * @param ps The {@link PreparedStatement} to be executed.
         */
        public BatchCallableStatement(final String sql, final Object[][] params, final Connection conn, final boolean closeConn, final PreparedStatement ps) {
            this.sql = sql;
            this.params = params.clone();
            this.conn = conn;
            this.closeConn = closeConn;
            this.ps = ps;
        }

        /**
         * The actual call to executeBatch.
         *
         * @return an array of update counts containing one element for each command in the batch.
         * @throws SQLException if a database access error occurs or one of the commands sent to the database fails.
         * @see PreparedStatement#executeBatch()
         */
        @Override
        public int[] call() throws SQLException {
            int[] ret = null;

            try {
                ret = ps.executeBatch();
            } catch (final SQLException e) {
                rethrow(e, sql, (Object[])params);
            } finally {
                close(ps);
                if (closeConn) {
                    close(conn);
                }
            }

            return ret;
        }
    }
    /**
     * Class that encapsulates the continuation for query calls.
     * @param <T> The type of the result from the call to handle.
     */
    protected class QueryCallableStatement<T> implements Callable<T> {
        private final String sql;
        private final Object[] params;
        private final Connection conn;
        private final boolean closeConn;
        private final PreparedStatement ps;
        private final ResultSetHandler<T> rsh;

        /**
         * Creates a new {@code QueryCallableStatement} instance.
         *
         * @param conn The connection to use for the batch call.
         * @param closeConn True if the connection should be closed, false otherwise.
         * @param ps The {@link PreparedStatement} to be executed.
         * @param rsh The handler that converts the results into an object.
         * @param sql The SQL statement to execute.
         * @param params An array of query replacement parameters.  Each row in
         *        this array is one set of batch replacement values.
         */
        public QueryCallableStatement(final Connection conn, final boolean closeConn, final PreparedStatement ps,
                final ResultSetHandler<T> rsh, final String sql, final Object... params) {
            this.sql = sql;
            this.params = params;
            this.conn = conn;
            this.closeConn = closeConn;
            this.ps = ps;
            this.rsh = rsh;
        }

        /**
         * The actual call to {@code handle()} method.
         *
         * @return an array of update counts containing one element for each command in the batch.
         * @throws SQLException if a database access error occurs.
         * @see ResultSetHandler#handle(ResultSet)
         */
        @Override
        public T call() throws SQLException {
            ResultSet resultSet = null;
            T ret = null;

            try {
                resultSet = wrap(ps.executeQuery());
                ret = rsh.handle(resultSet);
            } catch (final SQLException e) {
                rethrow(e, sql, params);
            } finally {
                try {
                    close(resultSet);
                } finally {
                    close(ps);
                    if (closeConn) {
                        close(conn);
                    }
                }
            }

            return ret;
        }

    }

    /**
     * Class that encapsulates the continuation for update calls.
     *
     * @deprecated No longer used by this class. Will be removed in a future version.
     */
    @Deprecated
    protected class UpdateCallableStatement implements Callable<Integer> {
        private final String sql;
        private final Object[] params;
        private final Connection conn;
        private final boolean closeConn;
        private final PreparedStatement ps;

        /**
         * Constructs a new instance.
         *
         * @param conn The connection to use for the batch call.
         * @param closeConn True if the connection should be closed, false otherwise.
         * @param ps The {@link PreparedStatement} to be executed.
         * @param sql The SQL statement to execute.
         * @param params An array of query replacement parameters.  Each row in
         *        this array is one set of batch replacement values.
         */
        public UpdateCallableStatement(final Connection conn, final boolean closeConn, final PreparedStatement ps, final String sql, final Object... params) {
            this.sql = sql;
            this.params = params;
            this.conn = conn;
            this.closeConn = closeConn;
            this.ps = ps;
        }

        /**
         * The actual call to {@code executeUpdate()} method.
         *
         * @return either (1) the row count for SQL Data Manipulation Language (DML) statements or
         *                (2) 0 for SQL statements that return nothing
         * @throws SQLException if a database access error occurs.
         * @see PreparedStatement#executeUpdate()
         */
        @Override
        public Integer call() throws SQLException {
            int rows = 0;

            try {
                rows = ps.executeUpdate();
            } catch (final SQLException e) {
                rethrow(e, sql, params);
            } finally {
                close(ps);
                if (closeConn) {
                    close(conn);
                }
            }

            return Integer.valueOf(rows);
        }

    }

    private final ExecutorService executorService;

    private final QueryRunner queryRunner;

    /**
     * @deprecated Use {@link #AsyncQueryRunner(ExecutorService, QueryRunner)} instead.
     * Constructor for AsyncQueryRunner that controls the use of {@code ParameterMetaData}.
     *
     * @param pmdKnownBroken Some drivers don't support {@link java.sql.ParameterMetaData#getParameterType(int)};
     * if {@code pmdKnownBroken} is set to true, we won't even try it; if false, we'll try it,
     * and if it breaks, we'll remember not to use it again.
     * @param executorService the {@code ExecutorService} instance used to run JDBC invocations concurrently.
     */
    @Deprecated
    public AsyncQueryRunner(final boolean pmdKnownBroken, final ExecutorService executorService) {
        this(null, pmdKnownBroken, executorService);
    }

    /**
     * @deprecated Use {@link #AsyncQueryRunner(ExecutorService, QueryRunner)} instead.
     * Constructor for AsyncQueryRunner that take a {@code DataSource} and controls the use of {@code ParameterMetaData}.
     * Methods that do not take a {@code Connection} parameter will retrieve connections from this
     * {@code DataSource}.
     *
     * @param ds The {@code DataSource} to retrieve connections from.
     * @param pmdKnownBroken Some drivers don't support {@link java.sql.ParameterMetaData#getParameterType(int)};
     * if {@code pmdKnownBroken} is set to true, we won't even try it; if false, we'll try it,
     * and if it breaks, we'll remember not to use it again.
     * @param executorService the {@code ExecutorService} instance used to run JDBC invocations concurrently.
     */
    @Deprecated
    public AsyncQueryRunner(final DataSource ds, final boolean pmdKnownBroken, final ExecutorService executorService) {
        super(ds, pmdKnownBroken);
        this.executorService = executorService;
        this.queryRunner = new QueryRunner(ds, pmdKnownBroken);
    }

    /**
     * @deprecated Use {@link #AsyncQueryRunner(ExecutorService, QueryRunner)} instead.
     * Constructor for AsyncQueryRunner that takes a {@code DataSource}.
     *
     * Methods that do not take a {@code Connection} parameter will retrieve connections from this
     * {@code DataSource}.
     *
     * @param ds The {@code DataSource} to retrieve connections from.
     * @param executorService the {@code ExecutorService} instance used to run JDBC invocations concurrently.
     */
    @Deprecated
    public AsyncQueryRunner(final DataSource ds, final ExecutorService executorService) {
        this(ds, false, executorService);
    }

    /**
     * Constructor for AsyncQueryRunner.
     *
     * @param executorService the {@code ExecutorService} instance used to run JDBC invocations concurrently.
     */
    public AsyncQueryRunner(final ExecutorService executorService) {
        this(null, false, executorService);
    }

    /**
     * Constructor for AsyncQueryRunner which uses a provided ExecutorService and underlying QueryRunner.
     *
     * @param executorService the {@code ExecutorService} instance used to run JDBC invocations concurrently.
     * @param queryRunner the {@code QueryRunner} instance to use for the queries.
     * @since 1.5
     */
    public AsyncQueryRunner(final ExecutorService executorService, final QueryRunner queryRunner) {
        this.executorService = executorService;
        this.queryRunner = queryRunner;
    }

    /**
     * Execute a batch of SQL INSERT, UPDATE, or DELETE queries.
     *
     * @param conn The {@code Connection} to use to run the query.  The caller is
     * responsible for closing this Connection.
     * @param sql The SQL to execute.
     * @param params An array of query replacement parameters.  Each row in
     * this array is one set of batch replacement values.
     * @return A {@code Future} which returns the number of rows updated per statement.
     * @throws SQLException if a database access error occurs
     */
    public Future<int[]> batch(final Connection conn, final String sql, final Object[][] params) throws SQLException {
        return executorService.submit(() -> queryRunner.batch(conn, sql, params));
    }

    /**
     * Execute a batch of SQL INSERT, UPDATE, or DELETE queries.  The
     * {@code Connection} is retrieved from the {@code DataSource}
     * set in the constructor.  This {@code Connection} must be in
     * auto-commit mode or the update will not be saved.
     *
     * @param sql The SQL to execute.
     * @param params An array of query replacement parameters.  Each row in
     * this array is one set of batch replacement values.
     * @return A {@code Future} which returns the number of rows updated per statement.
     * @throws SQLException if a database access error occurs
     */
    public Future<int[]> batch(final String sql, final Object[][] params) throws SQLException {
        return executorService.submit(() -> queryRunner.batch(sql, params));
    }

    /**
     * Executes {@link QueryRunner#insert(Connection, String, ResultSetHandler)} asynchronously.
     *
     * @param <T> Return type expected
     * @param conn {@link Connection} to use to execute the SQL statement
     * @param sql SQL insert statement to execute
     * @param rsh {@link ResultSetHandler} for handling the results
     * @return {@link Future} that executes a query runner insert
     * @see QueryRunner#insert(Connection, String, ResultSetHandler)
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> Future<T> insert(final Connection conn, final String sql, final ResultSetHandler<T> rsh) throws SQLException {
        return executorService.submit(() -> queryRunner.insert(conn, sql, rsh));
    }

    /**
     * Executes {@link QueryRunner#insert(Connection, String, ResultSetHandler, Object...)} asynchronously.
     *
     * @param <T> Return type expected
     * @param conn {@link Connection} to use to execute the SQL statement
     * @param sql SQL insert statement to execute
     * @param rsh {@link ResultSetHandler} for handling the results
     * @param params Parameter values for substitution in the SQL statement
     * @return {@link Future} that executes a query runner insert
     * @see QueryRunner#insert(Connection, String, ResultSetHandler, Object...)
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> Future<T> insert(final Connection conn, final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        return executorService.submit(() -> queryRunner.insert(conn, sql, rsh, params));
    }

    /**
     * Executes {@link QueryRunner#insert(String, ResultSetHandler)} asynchronously.
     *
     * @param <T> Return type expected
     * @param sql SQL insert statement to execute
     * @param rsh {@link ResultSetHandler} for handling the results
     * @return {@link Future} that executes a query runner insert
     * @see QueryRunner#insert(String, ResultSetHandler)
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> Future<T> insert(final String sql, final ResultSetHandler<T> rsh) throws SQLException {
        return executorService.submit(() -> queryRunner.insert(sql, rsh));
    }

    /**
     * Executes {@link QueryRunner#insert(String, ResultSetHandler, Object...)} asynchronously.
     *
     * @param <T> Return type expected
     * @param sql SQL insert statement to execute
     * @param rsh {@link ResultSetHandler} for handling the results
     * @param params Parameter values for substitution in the SQL statement
     * @return {@link Future} that executes a query runner insert
     * @see QueryRunner#insert(String, ResultSetHandler, Object...)
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> Future<T> insert(final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        return executorService.submit(() -> queryRunner.insert(sql, rsh, params));
    }

    /**
     * {@link QueryRunner#insertBatch(Connection, String, ResultSetHandler, Object[][])} asynchronously.
     *
     * @param <T> Return type expected
     * @param conn {@link Connection} to use to execute the SQL statement
     * @param sql SQL insert statement to execute
     * @param rsh {@link ResultSetHandler} for handling the results
     * @param params An array of query replacement parameters.  Each row in
     *        this array is one set of batch replacement values.
     * @return {@link Future} that executes a query runner batch insert
     * @see QueryRunner#insertBatch(Connection, String, ResultSetHandler, Object[][])
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> Future<T> insertBatch(final Connection conn, final String sql, final ResultSetHandler<T> rsh, final Object[][] params) throws SQLException {
        return executorService.submit(() -> queryRunner.insertBatch(conn, sql, rsh, params));
    }

    /**
     * {@link QueryRunner#insertBatch(String, ResultSetHandler, Object[][])} asynchronously.
     *
     * @param <T> Return type expected
     * @param sql SQL insert statement to execute
     * @param rsh {@link ResultSetHandler} for handling the results
     * @param params An array of query replacement parameters.  Each row in
     *        this array is one set of batch replacement values.
     * @return {@link Future} that executes a query runner batch insert
     * @see QueryRunner#insertBatch(String, ResultSetHandler, Object[][])
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> Future<T> insertBatch(final String sql, final ResultSetHandler<T> rsh, final Object[][] params) throws SQLException {
        return executorService.submit(() -> queryRunner.insertBatch(sql, rsh, params));
    }

    /**
     * Execute an SQL SELECT query without any replacement parameters.  The
     * caller is responsible for closing the connection.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to execute the query in.
     * @param sql The query to execute.
     * @param rsh The handler that converts the results into an object.
     * @return A {@code Future} which returns the result of the query call.
     * @throws SQLException if a database access error occurs
     */
    public <T> Future<T> query(final Connection conn, final String sql, final ResultSetHandler<T> rsh) throws SQLException {
        return executorService.submit(() -> queryRunner.query(conn, sql, rsh));
    }

    /**
     * Execute an SQL SELECT query with replacement parameters.  The
     * caller is responsible for closing the connection.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to execute the query in.
     * @param sql The query to execute.
     * @param rsh The handler that converts the results into an object.
     * @param params The replacement parameters.
     * @return A {@code Future} which returns the result of the query call.
     * @throws SQLException if a database access error occurs
     */
    public <T> Future<T> query(final Connection conn, final String sql, final ResultSetHandler<T> rsh, final Object... params)
            throws SQLException {
        return executorService.submit(() -> queryRunner.query(conn, sql, rsh, params));
    }

    /**
     * Executes the given SELECT SQL without any replacement parameters.
     * The {@code Connection} is retrieved from the
     * {@code DataSource} set in the constructor.
     * @param <T> The type of object that the handler returns
     * @param sql The SQL statement to execute.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet}.
     *
     * @return A {@code Future} which returns the result of the query call.
     * @throws SQLException if a database access error occurs
     */
    public <T> Future<T> query(final String sql, final ResultSetHandler<T> rsh) throws SQLException {
        return executorService.submit(() -> queryRunner.query(sql, rsh));
    }

    /**
     * Executes the given SELECT SQL query and returns a result object.
     * The {@code Connection} is retrieved from the
     * {@code DataSource} set in the constructor.
     * @param <T> The type of object that the handler returns
     * @param sql The SQL statement to execute.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet}.
     * @param params Initialize the PreparedStatement's IN parameters with
     * this array.
     * @return A {@code Future} which returns the result of the query call.
     * @throws SQLException if a database access error occurs
     */
    public <T> Future<T> query(final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        return executorService.submit(() -> queryRunner.query(sql, rsh, params));
    }

    /**
     * Execute an SQL INSERT, UPDATE, or DELETE query without replacement
     * parameters.
     *
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @return A {@code Future} which returns the number of rows updated.
     * @throws SQLException if a database access error occurs
     */
    public Future<Integer> update(final Connection conn, final String sql) throws SQLException {
        return executorService.submit(() -> Integer.valueOf(queryRunner.update(conn, sql)));
    }

    /**
     * Execute an SQL INSERT, UPDATE, or DELETE query with a single replacement
     * parameter.
     *
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param param The replacement parameter.
     * @return A {@code Future} which returns the number of rows updated.
     * @throws SQLException if a database access error occurs
     */
    public Future<Integer> update(final Connection conn, final String sql, final Object param) throws SQLException {
        return executorService.submit(() -> Integer.valueOf(queryRunner.update(conn, sql, param)));
    }

    /**
     * Execute an SQL INSERT, UPDATE, or DELETE query.
     *
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param params The query replacement parameters.
     * @return A {@code Future} which returns the number of rows updated.
     * @throws SQLException if a database access error occurs
     */
    public Future<Integer> update(final Connection conn, final String sql, final Object... params) throws SQLException {
        return executorService.submit(() -> Integer.valueOf(queryRunner.update(conn, sql, params)));
    }

    /**
     * Executes the given INSERT, UPDATE, or DELETE SQL statement without
     * any replacement parameters. The {@code Connection} is retrieved
     * from the {@code DataSource} set in the constructor.  This
     * {@code Connection} must be in auto-commit mode or the update will
     * not be saved.
     *
     * @param sql The SQL statement to execute.
     * @throws SQLException if a database access error occurs
     * @return A {@code Future} which returns the number of rows updated.
     */
    public Future<Integer> update(final String sql) throws SQLException {
        return executorService.submit(() -> Integer.valueOf(queryRunner.update(sql)));
    }

    /**
     * Executes the given INSERT, UPDATE, or DELETE SQL statement with
     * a single replacement parameter.  The {@code Connection} is
     * retrieved from the {@code DataSource} set in the constructor.
     * This {@code Connection} must be in auto-commit mode or the
     * update will not be saved.
     *
     * @param sql The SQL statement to execute.
     * @param param The replacement parameter.
     * @throws SQLException if a database access error occurs
     * @return A {@code Future} which returns the number of rows updated.
     */
    public Future<Integer> update(final String sql, final Object param) throws SQLException {
        return executorService.submit(() -> Integer.valueOf(queryRunner.update(sql, param)));
    }

    /**
     * Executes the given INSERT, UPDATE, or DELETE SQL statement.  The
     * {@code Connection} is retrieved from the {@code DataSource}
     * set in the constructor.  This {@code Connection} must be in
     * auto-commit mode or the update will not be saved.
     *
     * @param sql The SQL statement to execute.
     * @param params Initializes the PreparedStatement's IN (i.e. '?')
     * parameters.
     * @throws SQLException if a database access error occurs
     * @return A {@code Future} which returns the number of rows updated.
     */
    public Future<Integer> update(final String sql, final Object... params) throws SQLException {
        return executorService.submit(() -> Integer.valueOf(queryRunner.update(sql, params)));
    }

}
