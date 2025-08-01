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
package org.apache.commons.dbutils.handlers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.dbutils.ResultSetHandler;

/**
 * Abstract class that simplify development of {@code ResultSetHandler}
 * classes that convert {@code ResultSet} into {@code List}.
 *
 * @param <T> the target List generic type
 * @see org.apache.commons.dbutils.ResultSetHandler
 */
public abstract class AbstractListHandler<T> implements ResultSetHandler<List<T>> {

    /**
     * Constructs a new instance for subclasses.
     */
    public AbstractListHandler() {
        // empty
    }

    /**
     * Whole {@code ResultSet} handler. It produce {@code List} as
     * result. To convert individual rows into Java objects it uses
     * {@code handleRow(ResultSet)} method.
     *
     * @see #handleRow(ResultSet)
     * @param resultSet {@code ResultSet} to process.
     * @return a list of all rows in the result set
     * @throws SQLException error occurs
     */
    @Override
    public List<T> handle(final ResultSet resultSet) throws SQLException {
        final List<T> rows = new ArrayList<>();
        while (resultSet.next()) {
            rows.add(this.handleRow(resultSet));
        }
        return rows;
    }

    /**
     * Row handler. Method converts current row into some Java object.
     *
     * @param resultSet {@code ResultSet} to process.
     * @return row processing result
     * @throws SQLException error occurs
     */
    protected abstract T handleRow(ResultSet resultSet) throws SQLException;
}
