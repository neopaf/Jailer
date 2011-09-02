/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.jailer.ui.databrowser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.jailer.database.Session;
import net.sf.jailer.datamodel.Column;
import net.sf.jailer.datamodel.Table;
import net.sf.jailer.util.SqlUtil;


/**
 * Builder for Insert/Delete/Update statements.
 * 
 * @author Ralf Wisser
 */
public class SQLDMLBuilder {

	/**
	 * Build Update statements.
	 * 
	 * @param table the table
	 * @param rows rows
	 * @param session current DB session
	 * @return update statements for rows
	 */
	public static String buildUpdate(Table table, List<Row> rows, Session session) {
		StringBuilder sb = new StringBuilder();
		
		for (Row row: unique(rows)) {
			sb.append(buildUpdate(table, row, session)).append("\n");
		}
		return sb.toString();
	}
	
	/**
	 * Build Update statements.
	 * 
	 * @param table the table
	 * @param row row to be updated
	 * @param session current DB session
	 * @return update statement for row
	 */
	public static String buildUpdate(Table table, Row row, Session session) {
		String sql = "Update " + table.getName() + " \nSet ";
		boolean f = true;
		int i = 0;
		for (Column column : table.getColumns()) {
			String name = column.name;
			String value = getSQLLiteral(row.values[i++], session);
			if (value == null) {
				continue;
			}
			sql += (f? "" : ", \n    ") + name + " = " + value;
			f = false;
		}
		sql += " \nWhere " + SqlUtil.replaceAliases(row.rowId, null, null) + ";\n";
		return sql;
	}
	
	/**
	 * Build Insert statements.
	 * 
	 * @param table the table
	 * @param rows rows
	 * @param session current DB session
	 * @return insert statements for rows
	 */
	public static String buildInsert(Table table, List<Row> rows, Session session) {
		StringBuilder sb = new StringBuilder();
		
		for (Row row: unique(rows)) {
			sb.append(buildInsert(table, row, session)).append("\n");
		}
		return sb.toString();
	}
	
	/**
	 * Build Insert statements.
	 * 
	 * @param table the table
	 * @param row row to be updated
	 * @param session current DB session
	 * @return update statement for row
	 */
	public static String buildInsert(Table table, Row row, Session session) {
		String sql = "Insert into " + table.getName() + " ( \n    ";
		String values = "";
		boolean f = true;
		int i = 0;
		for (Column column : table.getColumns()) {
			String name = column.name;
			String value = getSQLLiteral(row.values[i++], session);
			if (value == null) {
				continue;
			}
			sql += (f? "" : ", \n    ") + name;
			values += (f? "" : ", \n    ") + value;
			f = false;
		}
		sql += ") \nValues ( \n    " + values + ")" + ";\n";
		return sql;
	}
	
	/**
	 * Build Delete statements.
	 * 
	 * @param table the table
	 * @param row row to be updated
	 * @param session current DB session
	 * @return update statement for row
	 */
	public static String buildDelete(Table table, Row row, Session session) {
		String sql = "Delete from " + table.getName() + " Where " + SqlUtil.replaceAliases(row.rowId, null, null) + ";\n";
		return sql;
	}

	/**
	 * Build Delete statements.
	 * 
	 * @param table the table
	 * @param rows rows
	 * @param session current DB session
	 * @return delete statements for rows
	 */
	public static String buildDelete(Table table, List<Row> rows, Session session) {
		StringBuilder sb = new StringBuilder();
		
		for (Row row: unique(rows)) {
			sb.append(buildDelete(table, row, session));
		}
		return sb.toString();
	}
	
	/**
	 * Removes all duplicates out of a list of rows.
	 * 
	 * @param rows list of rows
	 * @return list of rows without duplicates
	 */
	private static List<Row> unique(List<Row> rows) {
		List<Row> result = new ArrayList<Row>();
		Set<String> ids = new HashSet<String>();
		for (Row row: rows) {
			if (!ids.contains(row.rowId)) {
				ids.add(row.rowId);
				result.add(row);
			}
		}
		return result;
	}

	/**
	 * Gets SQL literal for a given object. Returns <code>null</code> if the object cannot be converted into a SQL literal (LOBs).
	 * 
	 * @param value the value
	 * @param session current DB session
	 * @return SQL literal or <code>null</code>
	 */
	private static String getSQLLiteral(Object value, Session session) {
		if (value instanceof LobValue) {
			return null;
		}
		return SqlUtil.toSql(value, session);
	}
	
}