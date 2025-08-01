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
import java.util.Map;

import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;

/**
 * {@code ResultSetHandler} implementation that converts the first
 * {@code ResultSet} row into a {@code Map}. This class is thread
 * safe.
 *
 * @see org.apache.commons.dbutils.ResultSetHandler
 */
public class MapHandler implements ResultSetHandler<Map<String, Object>> {

    /**
     * The RowProcessor implementation to use when converting rows
     * into Maps.
     */
    private final RowProcessor convert;

    /**
     * Creates a new instance of MapHandler using a
     * {@code BasicRowProcessor} for conversion.
     */
    public MapHandler() {
        this(ArrayHandler.ROW_PROCESSOR);
    }

    /**
     * Creates a new instance of MapHandler.
     *
     * @param convert The {@code RowProcessor} implementation
     * to use when converting rows into Maps.
     */
    public MapHandler(final RowProcessor convert) {
        this.convert = convert;
    }

    /**
     * Converts the first row in the {@code ResultSet} into a
     * {@code Map}.
     * @param resultSet {@code ResultSet} to process.
     * @return A {@code Map} with the values from the first row or
     * {@code null} if there are no rows in the {@code ResultSet}.
     *
     * @throws SQLException if a database access error occurs
     * @see org.apache.commons.dbutils.ResultSetHandler#handle(java.sql.ResultSet)
     */
    @Override
    public Map<String, Object> handle(final ResultSet resultSet) throws SQLException {
        return resultSet.next() ? this.convert.toMap(resultSet) : null;
    }

}
