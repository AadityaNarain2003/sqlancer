package lama.cockroachdb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lama.IgnoreMeException;
import lama.Query;
import lama.QueryAdapter;
import lama.Randomly;
import lama.cockroachdb.CockroachDBProvider.CockroachDBGlobalState;
import lama.cockroachdb.CockroachDBSchema.CockroachDBColumn;
import lama.cockroachdb.CockroachDBSchema.CockroachDBIndex;
import lama.cockroachdb.CockroachDBSchema.CockroachDBTable;

public class RockroachDBCommentOnGenerator {
	
	private enum Option {
		TABLE, INDEX, COLUMN
	}

	public static Query comment(CockroachDBGlobalState globalState) {
		StringBuilder sb = new StringBuilder("COMMENT ON ");
		CockroachDBTable randomTable = globalState.getSchema().getRandomTable(t -> !t.isView());
		switch (Randomly.fromOptions(Option.values())) {
		case TABLE:
			sb.append("TABLE " + randomTable.getName());
			break;
		case INDEX:
			List<CockroachDBIndex> indexes = randomTable.getIndexes();
			if (indexes.isEmpty()) {
				throw new IgnoreMeException();
			}
			CockroachDBIndex index = Randomly.fromList(indexes);
			if (index.getIndexName().contains("primary")) {
				throw new IgnoreMeException();
			}
			sb.append("INDEX " + index.getIndexName() + "");
			break;
		case COLUMN:
			sb.append("COLUMN ");
			CockroachDBColumn randomColumn = randomTable.getRandomColumn();

			sb.append(randomColumn.getFullQualifiedName());
			break;
		default:
			throw new AssertionError();
		}
		sb.append(" IS '");
		sb.append(globalState.getRandomly().getString().replace("'", "''"));
		sb.append("'");
		Set<String> errors = new HashSet<>();
		CockroachDBErrors.addTransactionErrors(errors);
		return new QueryAdapter(sb.toString(), errors);
	}
	
}
