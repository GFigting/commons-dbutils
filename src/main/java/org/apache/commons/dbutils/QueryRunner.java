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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import javax.sql.DataSource;

/**
 * Executes SQL queries with pluggable strategies for handling
 * {@code ResultSet}s.  This class is thread safe.
 *
 * @see ResultSetHandler
 */
public class QueryRunner extends AbstractQueryRunner {

    /**
     * Constructor for QueryRunner.
     */
    public QueryRunner() {
    }

    /**
     * Constructor for QueryRunner that controls the use of {@code ParameterMetaData}.
     *
     * @param pmdKnownBroken Some drivers don't support {@link java.sql.ParameterMetaData#getParameterType(int)};
     * if {@code pmdKnownBroken} is set to true, we won't even try it; if false, we'll try it,
     * and if it breaks, we'll remember not to use it again.
     */
    public QueryRunner(final boolean pmdKnownBroken) {
        super(pmdKnownBroken);
    }

    /**
     * Constructor for QueryRunner that takes a {@code DataSource} to use.
     *
     * Methods that do not take a {@code Connection} parameter will retrieve connections from this
     * {@code DataSource}.
     *
     * @param ds The {@code DataSource} to retrieve connections from.
     */
    public QueryRunner(final DataSource ds) {
        super(ds);
    }

    /**
     * Constructor for QueryRunner that takes a {@code DataSource} and controls the use of {@code ParameterMetaData}.
     * Methods that do not take a {@code Connection} parameter will retrieve connections from this
     * {@code DataSource}.
     *
     * @param ds The {@code DataSource} to retrieve connections from.
     * @param pmdKnownBroken Some drivers don't support {@link java.sql.ParameterMetaData#getParameterType(int)};
     * if {@code pmdKnownBroken} is set to true, we won't even try it; if false, we'll try it,
     * and if it breaks, we'll remember not to use it again.
     */
    public QueryRunner(final DataSource ds, final boolean pmdKnownBroken) {
        super(ds, pmdKnownBroken);
    }

    /**
     * Constructor for QueryRunner that takes a {@code DataSource}, a {@code StatementConfiguration}, and
     * controls the use of {@code ParameterMetaData}.  Methods that do not take a {@code Connection} parameter
     * will retrieve connections from this {@code DataSource}.
     *
     * @param ds The {@code DataSource} to retrieve connections from.
     * @param pmdKnownBroken Some drivers don't support {@link java.sql.ParameterMetaData#getParameterType(int)};
     * if {@code pmdKnownBroken} is set to true, we won't even try it; if false, we'll try it,
     * and if it breaks, we'll remember not to use it again.
     * @param stmtConfig The configuration to apply to statements when they are prepared.
     */
    public QueryRunner(final DataSource ds, final boolean pmdKnownBroken, final StatementConfiguration stmtConfig) {
        super(ds, pmdKnownBroken, stmtConfig);
    }

    /**
     * Constructor for QueryRunner that takes a {@code DataSource} to use and a {@code StatementConfiguration}.
     *
     * Methods that do not take a {@code Connection} parameter will retrieve connections from this
     * {@code DataSource}.
     *
     * @param ds The {@code DataSource} to retrieve connections from.
     * @param stmtConfig The configuration to apply to statements when they are prepared.
     */
    public QueryRunner(final DataSource ds, final StatementConfiguration stmtConfig) {
        super(ds, stmtConfig);
    }

    /**
     * Constructor for QueryRunner that takes a {@code StatementConfiguration} to configure statements when
     * preparing them.
     *
     * @param stmtConfig The configuration to apply to statements when they are prepared.
     */
    public QueryRunner(final StatementConfiguration stmtConfig) {
        super(stmtConfig);
    }

    /**
     * Execute a batch of SQL INSERT, UPDATE, or DELETE queries.
     *
     * @param conn The Connection to use to run the query.  The caller is
     * responsible for closing this Connection.
     * @param sql The SQL to execute.
     * @param params An array of query replacement parameters.  Each row in
     * this array is one set of batch replacement values.
     * @return The number of rows updated per statement.
     * @throws SQLException if a database access error occurs
     * @since 1.1
     */
    public int[] batch(final Connection conn, final String sql, final Object[][] params) throws SQLException {
        if (conn == null) {
            throw new SQLException("Null connection");
        }

        if (sql == null) {
            throw new SQLException("Null SQL statement");
        }

        if (params == null) {
            throw new SQLException("Null parameters. If parameters aren't need, pass an empty array.");
        }

        PreparedStatement stmt = null;
        ParameterMetaData pmd = null;
        int[] rows = null;
        try {
            stmt = this.prepareStatement(conn, sql);
            // When the batch size is large, prefetching parameter metadata before filling
            // the statement can reduce lots of JDBC communications.
            pmd = getParameterMetaData(stmt);

            for (final Object[] param : params) {
                this.fillStatement(stmt, pmd, param);
                stmt.addBatch();
            }
            rows = stmt.executeBatch();

        } catch (final SQLException e) {
            rethrow(e, sql, (Object[])params);
        } finally {
            close(stmt);
        }

        return rows;
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
     * @return The number of rows updated per statement.
     * @throws SQLException if a database access error occurs
     * @since 1.1
     */
    public int[] batch(final String sql, final Object[][] params) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.batch(conn, sql, params);
        }
    }

    /**
     * Execute an SQL statement, including a stored procedure call, which does
     * not return any result sets.
     * Any parameters which are instances of {@link OutParameter} will be
     * registered as OUT parameters.
     * <p>
     * Use this method when invoking a stored procedure with OUT parameters
     * that does not return any result sets.  If you are not invoking a stored
     * procedure, or the stored procedure has no OUT parameters, consider using
     * {@link #update(java.sql.Connection, String, Object...)}.
     * If the stored procedure returns result sets, use
     * {@link #execute(java.sql.Connection, String, org.apache.commons.dbutils.ResultSetHandler, Object...)}.
     *
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param params The query replacement parameters.
     * @return The number of rows updated.
     * @throws SQLException if a database access error occurs
     */
    public int execute(final Connection conn, final String sql, final Object... params) throws SQLException {
        if (conn == null) {
            throw new SQLException("Null connection");
        }

        if (sql == null) {
            throw new SQLException("Null SQL statement");
        }

        CallableStatement stmt = null;
        int rows = 0;

        try {
            stmt = prepareCall(conn, sql);
            this.fillStatement(stmt, params);
            stmt.execute();
            rows = stmt.getUpdateCount();
            retrieveOutParameters(stmt, params);

        } catch (final SQLException e) {
            rethrow(e, sql, params);

        } finally {
            close(stmt);
        }

        return rows;
    }

    /**
     * Execute an SQL statement, including a stored procedure call, which
     * returns one or more result sets.
     * Any parameters which are instances of {@link OutParameter} will be
     * registered as OUT parameters.
     * <p>
     * Use this method when: a) running SQL statements that return multiple
     * result sets; b) invoking a stored procedure that return result
     * sets and OUT parameters.  Otherwise you may wish to use
     * {@link #query(java.sql.Connection, String, org.apache.commons.dbutils.ResultSetHandler, Object...)}
     * (if there are no OUT parameters) or
     * {@link #execute(java.sql.Connection, String, Object...)}
     * (if there are no result sets).
     *
     * @param <T> The type of object that the handler returns
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param rsh The result set handler
     * @param params The query replacement parameters.
     * @return A list of objects generated by the handler
     * @throws SQLException if a database access error occurs
     */
    public <T> List<T> execute(final Connection conn, final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        if (conn == null) {
            throw new SQLException("Null connection");
        }

        if (sql == null) {
            throw new SQLException("Null SQL statement");
        }

        if (rsh == null) {
            throw new SQLException("Null ResultSetHandler");
        }

        CallableStatement stmt = null;
        final List<T> results = new LinkedList<>();

        try {
            stmt = prepareCall(conn, sql);
            this.fillStatement(stmt, params);
            boolean moreResultSets = stmt.execute();
            // Handle multiple result sets by passing them through the handler
            // retaining the final result
            while (moreResultSets) {
                try (@SuppressWarnings("resource")
                // assume the ResultSet wrapper properly closes
                ResultSet resultSet = wrap(stmt.getResultSet())) {
                    results.add(rsh.handle(resultSet));
                    moreResultSets = stmt.getMoreResults();
                }
            }
            retrieveOutParameters(stmt, params);

        } catch (final SQLException e) {
            rethrow(e, sql, params);

        } finally {
            close(stmt);
        }

        return results;
    }

    /**
     * Execute an SQL statement, including a stored procedure call, which does
     * not return any result sets.
     * Any parameters which are instances of {@link OutParameter} will be
     * registered as OUT parameters.
     * <p>
     * Use this method when invoking a stored procedure with OUT parameters
     * that does not return any result sets.  If you are not invoking a stored
     * procedure, or the stored procedure has no OUT parameters, consider using
     * {@link #update(String, Object...)}.
     * If the stored procedure returns result sets, use
     * {@link #execute(String, org.apache.commons.dbutils.ResultSetHandler, Object...)}.
     * <p>
     * The {@code Connection} is retrieved from the {@code DataSource}
     * set in the constructor.  This {@code Connection} must be in
     * auto-commit mode or the update will not be saved.
     *
     * @param sql The SQL statement to execute.
     * @param params Initializes the CallableStatement's parameters (i.e. '?').
     * @throws SQLException if a database access error occurs
     * @return The number of rows updated.
     */
    public int execute(final String sql, final Object... params) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.execute(conn, sql, params);
        }
    }

    /**
     * Execute an SQL statement, including a stored procedure call, which
     * returns one or more result sets.
     * Any parameters which are instances of {@link OutParameter} will be
     * registered as OUT parameters.
     * <p>
     * Use this method when: a) running SQL statements that return multiple
     * result sets; b) invoking a stored procedure that return result
     * sets and OUT parameters.  Otherwise you may wish to use
     * {@link #query(String, org.apache.commons.dbutils.ResultSetHandler, Object...)}
     * (if there are no OUT parameters) or
     * {@link #execute(String, Object...)}
     * (if there are no result sets).
     *
     * @param <T> The type of object that the handler returns
     * @param sql The SQL to execute.
     * @param rsh The result set handler
     * @param params The query replacement parameters.
     * @return A list of objects generated by the handler
     * @throws SQLException if a database access error occurs
     */
    public <T> List<T> execute(final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.execute(conn, sql, rsh, params);
        }
    }

    /**
     * Execute an SQL INSERT query without replacement parameters.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet} of auto-generated keys.
     * @return An object generated by the handler.
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> T insert(final Connection conn, final String sql, final ResultSetHandler<T> rsh) throws SQLException {
        return insert(conn, sql, rsh, (Object[]) null);
    }

    /**
     * Execute an SQL INSERT query.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet} of auto-generated keys.
     * @param params The query replacement parameters.
     * @return An object generated by the handler.
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> T insert(final Connection conn, final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        if (conn == null) {
            throw new SQLException("Null connection");
        }

        if (sql == null) {
            throw new SQLException("Null SQL statement");
        }

        if (rsh == null) {
            throw new SQLException("Null ResultSetHandler");
        }

        Statement stmt = null;
        T generatedKeys = null;

        try {
            if (params != null && params.length > 0) {
                final PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt = ps;
                this.fillStatement(ps, params);
                ps.executeUpdate();
            } else {
                stmt = conn.createStatement();
                stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            }
            try (ResultSet resultSet = stmt.getGeneratedKeys()) {
                generatedKeys = rsh.handle(resultSet);
            }
        } catch (final SQLException e) {
            rethrow(e, sql, params);
        } finally {
            close(stmt);
        }

        return generatedKeys;
    }

    /**
     * Executes the given INSERT SQL without any replacement parameters.
     * The {@code Connection} is retrieved from the
     * {@code DataSource} set in the constructor.
     * @param <T> The type of object that the handler returns
     * @param sql The SQL statement to execute.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet} of auto-generated keys.
     * @return An object generated by the handler.
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> T insert(final String sql, final ResultSetHandler<T> rsh) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return insert(conn, sql, rsh, (Object[]) null);
        }
    }

    /**
     * Executes the given INSERT SQL statement. The
     * {@code Connection} is retrieved from the {@code DataSource}
     * set in the constructor.  This {@code Connection} must be in
     * auto-commit mode or the insert will not be saved.
     * @param <T> The type of object that the handler returns
     * @param sql The SQL statement to execute.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet} of auto-generated keys.
     * @param params Initializes the PreparedStatement's IN (i.e. '?')
     * @return An object generated by the handler.
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> T insert(final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return insert(conn, sql, rsh, params);
        }
    }

    /**
     * Executes the given batch of INSERT SQL statements.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet} of auto-generated keys.
     * @param params The query replacement parameters.
     * @return The result generated by the handler.
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> T insertBatch(final Connection conn, final String sql, final ResultSetHandler<T> rsh, final Object[][] params) throws SQLException {
        if (conn == null) {
            throw new SQLException("Null connection");
        }

        if (sql == null) {
            throw new SQLException("Null SQL statement");
        }

        if (params == null) {
            throw new SQLException("Null parameters. If parameters aren't need, pass an empty array.");
        }

        PreparedStatement stmt = null;
        T generatedKeys = null;
        try {
            stmt = this.prepareStatement(conn, sql, Statement.RETURN_GENERATED_KEYS);

            for (final Object[] param : params) {
                this.fillStatement(stmt, param);
                stmt.addBatch();
            }
            stmt.executeBatch();
            try (ResultSet resultSet = stmt.getGeneratedKeys()) {
                generatedKeys = rsh.handle(resultSet);
            }
        } catch (final SQLException e) {
            rethrow(e, sql, (Object[])params);
        } finally {
            close(stmt);
        }

        return generatedKeys;
    }

    /**
     * Executes the given batch of INSERT SQL statements. The
     * {@code Connection} is retrieved from the {@code DataSource}
     * set in the constructor.  This {@code Connection} must be in
     * auto-commit mode or the insert will not be saved.
     * @param <T> The type of object that the handler returns
     * @param sql The SQL statement to execute.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet} of auto-generated keys.
     * @param params Initializes the PreparedStatement's IN (i.e. '?')
     * @return The result generated by the handler.
     * @throws SQLException if a database access error occurs
     * @since 1.6
     */
    public <T> T insertBatch(final String sql, final ResultSetHandler<T> rsh, final Object[][] params) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return insertBatch(conn, sql, rsh, params);
        }
    }

    /**
     * Execute an SQL SELECT query with a single replacement parameter. The
     * caller is responsible for closing the connection.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to execute the query in.
     * @param sql The query to execute.
     * @param param The replacement parameter.
     * @param rsh The handler that converts the results into an object.
     * @return The object returned by the handler.
     * @throws SQLException if a database access error occurs
     * @deprecated Use {@link #query(Connection, String, ResultSetHandler, Object...)}
     */
    @Deprecated
    public <T> T query(final Connection conn, final String sql, final Object param, final ResultSetHandler<T> rsh) throws SQLException {
        return this.<T>query(conn, sql, rsh, param);
    }

    /**
     * Execute an SQL SELECT query with replacement parameters.  The
     * caller is responsible for closing the connection.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to execute the query in.
     * @param sql The query to execute.
     * @param params The replacement parameters.
     * @param rsh The handler that converts the results into an object.
     * @return The object returned by the handler.
     * @throws SQLException if a database access error occurs
     * @deprecated Use {@link #query(Connection,String,ResultSetHandler,Object...)} instead
     */
    @Deprecated
    public <T> T query(final Connection conn, final String sql, final Object[] params, final ResultSetHandler<T> rsh) throws SQLException {
        return this.<T>query(conn, sql, rsh, params);
    }

    /**
     * Execute an SQL SELECT query without any replacement parameters.  The
     * caller is responsible for closing the connection.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to execute the query in.
     * @param sql The query to execute.
     * @param rsh The handler that converts the results into an object.
     * @return The object returned by the handler.
     * @throws SQLException if a database access error occurs
     */
    public <T> T query(final Connection conn, final String sql, final ResultSetHandler<T> rsh) throws SQLException {
        return this.<T>query(conn, sql, rsh, (Object[]) null);
    }

    /**
     * Execute an SQL SELECT query with replacement parameters.  The
     * caller is responsible for closing the connection.
     * @param <T> The type of object that the handler returns
     * @param conn The connection to execute the query in.
     * @param sql The query to execute.
     * @param rsh The handler that converts the results into an object.
     * @param params The replacement parameters.
     * @return The object returned by the handler.
     * @throws SQLException if a database access error occurs
     */
    public <T> T query(final Connection conn, final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        if (conn == null) {
            throw new SQLException("Null connection");
        }

        if (sql == null) {
            throw new SQLException("Null SQL statement");
        }

        if (rsh == null) {
            throw new SQLException("Null ResultSetHandler");
        }

        Statement stmt = null;
        ResultSet resultSet = null;
        T result = null;

        try {
            if (params != null && params.length > 0) {
                final PreparedStatement ps = this.prepareStatement(conn, sql);
                stmt = ps;
                this.fillStatement(ps, params);
                resultSet = wrap(ps.executeQuery());
            } else {
                stmt = conn.createStatement();
                resultSet = wrap(stmt.executeQuery(sql));
            }
            result = rsh.handle(resultSet);

        } catch (final SQLException e) {
            rethrow(e, sql, params);

        } finally {
            closeQuietly(resultSet);
            closeQuietly(stmt);
        }

        return result;
    }

    /**
     * Executes the given SELECT SQL with a single replacement parameter.
     * The {@code Connection} is retrieved from the
     * {@code DataSource} set in the constructor.
     * @param <T> The type of object that the handler returns
     * @param sql The SQL statement to execute.
     * @param param The replacement parameter.
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet}.
     *
     * @return An object generated by the handler.
     * @throws SQLException if a database access error occurs
     * @deprecated Use {@link #query(String, ResultSetHandler, Object...)}
     */
    @Deprecated
    public <T> T query(final String sql, final Object param, final ResultSetHandler<T> rsh) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.<T>query(conn, sql, rsh, param);
        }
    }

    /**
     * Executes the given SELECT SQL query and returns a result object.
     * The {@code Connection} is retrieved from the
     * {@code DataSource} set in the constructor.
     * @param <T> The type of object that the handler returns
     * @param sql The SQL statement to execute.
     * @param params Initialize the PreparedStatement's IN parameters with
     * this array.
     *
     * @param rsh The handler used to create the result object from
     * the {@code ResultSet}.
     *
     * @return An object generated by the handler.
     * @throws SQLException if a database access error occurs
     * @deprecated Use {@link #query(String, ResultSetHandler, Object...)}
     */
    @Deprecated
    public <T> T query(final String sql, final Object[] params, final ResultSetHandler<T> rsh) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.<T>query(conn, sql, rsh, params);
        }
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
     * @return An object generated by the handler.
     * @throws SQLException if a database access error occurs
     */
    public <T> T query(final String sql, final ResultSetHandler<T> rsh) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.<T>query(conn, sql, rsh, (Object[]) null);
        }
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
     * @return An object generated by the handler.
     * @throws SQLException if a database access error occurs
     */
    public <T> T query(final String sql, final ResultSetHandler<T> rsh, final Object... params) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.<T>query(conn, sql, rsh, params);
        }
    }

    /**
     * Set the value on all the {@link OutParameter} instances in the
     * {@code params} array using the OUT parameter values from the
     * {@code stmt}.
     * @param stmt the statement from which to retrieve OUT parameter values
     * @param params the parameter array for the statement invocation
     * @throws SQLException when the value could not be retrieved from the
     * statement.
     */
    private void retrieveOutParameters(final CallableStatement stmt, final Object[] params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof OutParameter) {
                    ((OutParameter<?>) params[i]).setValue(stmt, i + 1);
                }
            }
        }
    }

    /**
     * Execute an SQL INSERT, UPDATE, or DELETE query without replacement
     * parameters.
     *
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @return The number of rows updated.
     * @throws SQLException if a database access error occurs
     */
    public int update(final Connection conn, final String sql) throws SQLException {
        return this.update(conn, sql, (Object[]) null);
    }

    /**
     * Execute an SQL INSERT, UPDATE, or DELETE query with a single replacement
     * parameter.
     *
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param param The replacement parameter.
     * @return The number of rows updated.
     * @throws SQLException if a database access error occurs
     */
    public int update(final Connection conn, final String sql, final Object param) throws SQLException {
        return this.update(conn, sql, new Object[] { param });
    }

    /**
     * Execute an SQL INSERT, UPDATE, or DELETE query.
     *
     * @param conn The connection to use to run the query.
     * @param sql The SQL to execute.
     * @param params The query replacement parameters.
     * @return The number of rows updated.
     * @throws SQLException if a database access error occurs
     */
    public int update(final Connection conn, final String sql, final Object... params) throws SQLException {
        if (conn == null) {
            throw new SQLException("Null connection");
        }

        if (sql == null) {
            throw new SQLException("Null SQL statement");
        }

        Statement stmt = null;
        int rows = 0;

        try {
            if (params != null && params.length > 0) {
                final PreparedStatement ps = this.prepareStatement(conn, sql);
                stmt = ps;
                this.fillStatement(ps, params);
                rows = ps.executeUpdate();
            } else {
                stmt = conn.createStatement();
                rows = stmt.executeUpdate(sql);
            }

        } catch (final SQLException e) {
            rethrow(e, sql, params);

        } finally {
            close(stmt);
        }

        return rows;
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
     * @return The number of rows updated.
     */
    public int update(final String sql) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.update(conn, sql, (Object[]) null);
        }
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
     * @return The number of rows updated.
     */
    public int update(final String sql, final Object param) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.update(conn, sql, param);
        }
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
     * @return The number of rows updated.
     */
    public int update(final String sql, final Object... params) throws SQLException {
        try (Connection conn = prepareConnection()) {
            return this.update(conn, sql, params);
        }
    }
}
