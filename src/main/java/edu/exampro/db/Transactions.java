package edu.exampro.db;

import edu.exampro.exception.ExamException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs database work inside ONE transaction: commit when everything succeeds, rollback when
 * anything fails (SQL errors AND runtime errors). Keeping this in one place means no repository
 * can forget the rollback.
 */
public final class Transactions {
    private Transactions() { }

    /** A block of JDBC code that uses the transaction's connection. */
    @FunctionalInterface
    public interface SqlWork<R> {
        R execute(Connection connection) throws SQLException;
    }

    public static <R> R run(String failureMessage, SqlWork<R> work) {
        try (Connection connection = Database.connection()) {
            connection.setAutoCommit(false);
            try {
                R result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
            // The connection is closed right after this block, so autocommit is deliberately NOT
            // switched back on: doing that while a transaction is open would COMMIT it.
        } catch (SQLException exception) {
            throw new ExamException(failureMessage, exception);
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
