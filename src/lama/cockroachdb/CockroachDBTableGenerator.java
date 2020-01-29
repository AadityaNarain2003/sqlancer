package lama.cockroachdb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lama.IgnoreMeException;
import lama.Query;
import lama.QueryAdapter;
import lama.Randomly;
import lama.cockroachdb.CockroachDBProvider.CockroachDBGlobalState;
import lama.cockroachdb.CockroachDBSchema.CockroachDBColumn;
import lama.cockroachdb.CockroachDBSchema.CockroachDBCompositeDataType;
import lama.cockroachdb.CockroachDBSchema.CockroachDBDataType;
import lama.cockroachdb.CockroachDBSchema.CockroachDBTable;

public class CockroachDBTableGenerator {

	private final CockroachDBGlobalState globalState;
	private final StringBuilder sb = new StringBuilder();
	private final boolean primaryKey = Randomly.getBoolean();
	private final List<CockroachDBColumn> columns = new ArrayList<>();
	private boolean singleColumnPrimaryKey = primaryKey && Randomly.getBoolean();
	private boolean compoundPrimaryKey = primaryKey && !singleColumnPrimaryKey;

	public CockroachDBTableGenerator(CockroachDBGlobalState globalState) {
		this.globalState = globalState;
	}

	public static Query generate(CockroachDBGlobalState globalState) {
		return new CockroachDBTableGenerator(globalState).gen();
	}

	private Query gen() {
		Set<String> errors = new HashSet<>();

		String tableName = Randomly.fromOptions("t0", "t1", "t2");
		sb.append("CREATE TABLE IF NOT EXISTS ");
		sb.append(tableName);
		for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
			String columnName = "c" + i;
			CockroachDBCompositeDataType columnType = CockroachDBCompositeDataType.getRandom();
			columns.add(new CockroachDBColumn(columnName, columnType, false, false));

		}
		CockroachDBExpressionGenerator gen = new CockroachDBExpressionGenerator(globalState).setColumns(columns);
		sb.append(" (");
		for (int i = 0; i < columns.size(); i++) {
			if (i != 0) {
				sb.append(", ");
			}
			CockroachDBColumn cockroachDBColumn = columns.get(i);
			sb.append(cockroachDBColumn.getName());
			sb.append(" ");
			sb.append(cockroachDBColumn.getColumnType());
			if (cockroachDBColumn.getColumnType().isString() && Randomly.getBoolean()) {
				sb.append(" COLLATE " + CockroachDBCommon.getRandomCollate());
			}
			if (Randomly.getBoolean() && false /* wait for https://github.com/cockroachdb/cockroach/issues/44132 */) {
				sb.append(" AS (");
				sb.append(CockroachDBVisitor.asString(gen.generateExpression(cockroachDBColumn.getColumnType())));
				sb.append(") STORED");
				errors.add("computed columns cannot reference other computed columns");
			}
			if (Randomly.getBoolean()) {
				sb.append(" UNIQUE ");
			}
			if (Randomly.getBoolean()) {
				sb.append(" NOT NULL ");
			}
			if (singleColumnPrimaryKey && Randomly.getBoolean()) {
				sb.append(" PRIMARY KEY");
				singleColumnPrimaryKey = false;
			}
			if (cockroachDBColumn.getColumnType().getPrimitiveDataType() != CockroachDBDataType.SERIAL && Randomly.getBoolean()) {
				sb.append(" DEFAULT (");
				sb.append(CockroachDBVisitor.asString(new CockroachDBExpressionGenerator(globalState)
						.generateExpression(cockroachDBColumn.getColumnType())));
				sb.append(")");
			}
			if (Randomly.getBooleanWithRatherLowProbability() && !globalState.getSchema().getDatabaseTables().isEmpty()) {
				// TODO: also allow referencing itself
				sb.append(" REFERENCES ");
				CockroachDBTable otherTable = globalState.getSchema().getRandomTable();
				List<CockroachDBColumn> applicableColumns = otherTable.getColumns().stream().filter(c -> c.getColumnType() == cockroachDBColumn.getColumnType()).collect(Collectors.toList());
				if (applicableColumns.isEmpty()) {
					throw new IgnoreMeException();
				}
				sb.append(otherTable.getName());
				sb.append("(");
				sb.append(Randomly.fromList(applicableColumns).getName());
				sb.append(")");
				if (Randomly.getBoolean()) {
					sb.append(" MATCH ");
					sb.append(Randomly.fromOptions("SIMPLE", "FULL"));
				}
				if (Randomly.getBoolean()) {
					errors.add("cannot add a SET DEFAULT cascading action on column");
					errors.add("cannot add a SET NULL cascading action on column ");
					List<String> options = Randomly.nonEmptySubset("UPDATE", "DELETE");
					for (String s : options) {
						sb.append(" ON ");
						sb.append(s);
						sb.append(" ");
						sb.append(Randomly.fromOptions("CASCADE", "SET NULL", "SET DEFAULT"));
					}
				}
				errors.add("there is no unique constraint matching given keys for referenced table");
			}
			if (Randomly.getBooleanWithRatherLowProbability() && false /* https://github.com/cockroachdb/cockroach/issues/44154  */) {
				sb.append(" CHECK (");
				sb.append(CockroachDBVisitor.asString(gen.generateExpression(CockroachDBDataType.BOOL.get())));
				sb.append(")");
			}
		}
		if (compoundPrimaryKey) {
			sb.append(", CONSTRAINT \"primary KEY\" PRIMARY KEY (");
			List<CockroachDBColumn> primaryKeyColumns = Randomly.nonEmptySubset(columns);
			for (int i = 0; i < primaryKeyColumns.size(); i++) {
				if (i != 0) {
					sb.append(", ");
				}
				sb.append(primaryKeyColumns.get(i).getName());
				if (Randomly.getBoolean()) {
					sb.append(Randomly.fromOptions(" ASC", " DESC"));
				}
			}
			sb.append(")");
		}
		if (Randomly.getBoolean()) {
			sb.append(", FAMILY \"primary\" (");
			sb.append(columns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
			sb.append(")");
		}
		sb.append(")");
		if (Randomly.getBooleanWithRatherLowProbability() && !globalState.getSchema().getDatabaseTables().isEmpty()) {
			CockroachDBTable parentTable = globalState.getSchema().getRandomTable();
			List<CockroachDBColumn> parentColumns = parentTable.getRandomNonEmptyColumnSubset();
			sb.append(" INTERLEAVE IN PARENT ");
			sb.append(parentTable.getName());
			sb.append("(");
			sb.append(parentColumns.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
			sb.append(")");
		}
		if (false && Randomly.getBoolean() && !globalState.getSchema().getDatabaseTables().isEmpty()) {
			sb.append(", ");
			// TODO: also allow referencing itself
			List<CockroachDBColumn> subset = Randomly.nonEmptySubset(columns);
			sb.append(" FOREIGN KEY (");
			sb.append(subset.stream().map(c -> c.getName()).collect(Collectors.joining(", ")));
			sb.append(") REFERENCES ");
			CockroachDBTable otherTable = globalState.getSchema().getRandomTable();
			sb.append(otherTable.getName());
			sb.append("(");
			for (int i = 0; i < subset.size(); i++) {
				if (i != 0) {
					sb.append(", ");
				}
				sb.append(otherTable.getRandomColumn().getName());
			}
			sb.append(")");
		}

		errors.add("must refer to a prefix of the primary key column names being interleaved");
		errors.add("must match the parent's primary index");
		errors.add("must match type and sort direction of the parent's primary index");
		errors.add("must be a prefix of the primary key columns being interleaved");
		errors.add("collatedstring");
		CockroachDBErrors.addExpressionErrors(errors);
		return new QueryAdapter(sb.toString(), errors, true);
	}

}
