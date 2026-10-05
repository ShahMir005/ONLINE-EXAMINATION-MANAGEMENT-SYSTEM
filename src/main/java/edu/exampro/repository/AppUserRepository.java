package edu.exampro.repository;

import edu.exampro.db.Database;
import edu.exampro.exception.ExamException;
import edu.exampro.model.AppUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** JDBC CRUD for app_users using PreparedStatement. */
public final class AppUserRepository implements CrudRepository<AppUser> {
    private static final String INSERT =
        "INSERT INTO app_users(full_name, email, password_hash, role, enabled) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE =
        "UPDATE app_users SET full_name = ?, email = ?, password_hash = ?, role = ?, enabled = ? WHERE id = ?";
    private static final String SELECT_BY_ID =
        "SELECT id, full_name, email, password_hash, role, enabled FROM app_users WHERE id = ?";
    private static final String SELECT_BY_EMAIL =
        "SELECT id, full_name, email, password_hash, role, enabled FROM app_users WHERE email = ?";
    private static final String SELECT_ALL =
        "SELECT id, full_name, email, password_hash, role, enabled FROM app_users ORDER BY id";
    private static final String DELETE = "DELETE FROM app_users WHERE id = ?";

    @Override
    public AppUser save(AppUser user) {
        return user.getId() == null ? insert(user) : update(user);
    }

    private AppUser insert(AppUser user) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getFullName());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getPasswordHash());
            statement.setString(4, user.getRole());
            statement.setBoolean(5, user.isEnabled());
            statement.executeUpdate();
            user.setId(Database.generatedKey(statement));
            return user;
        } catch (SQLException exception) {
            throw new ExamException("Could not create user account", exception);
        }
    }

    private AppUser update(AppUser user) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, user.getFullName());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getPasswordHash());
            statement.setString(4, user.getRole());
            statement.setBoolean(5, user.isEnabled());
            statement.setLong(6, user.getId());
            if (statement.executeUpdate() == 0) {
                throw new ExamException("Cannot update: no user with id " + user.getId());
            }
            return user;
        } catch (SQLException exception) {
            throw new ExamException("Could not update user account", exception);
        }
    }

    @Override
    public Optional<AppUser> findById(long id) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(toUser(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ExamException("Could not read user", exception);
        }
    }

    public Optional<AppUser> findByEmail(String email) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_EMAIL)) {
            statement.setString(1, email.trim().toLowerCase(Locale.ROOT));
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(toUser(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ExamException("Could not read user by email", exception);
        }
    }

    @Override
    public List<AppUser> findAll() {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet row = statement.executeQuery()) {
            List<AppUser> users = new ArrayList<>();
            while (row.next()) {
                users.add(toUser(row));
            }
            return users;
        } catch (SQLException exception) {
            throw new ExamException("Could not list users", exception);
        }
    }

    @Override
    public boolean deleteById(long id) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new ExamException("Could not delete user", exception);
        }
    }

    private static AppUser toUser(ResultSet row) throws SQLException {
        return new AppUser(
            row.getLong("id"),
            row.getString("full_name"),
            row.getString("email"),
            row.getString("password_hash"),
            row.getString("role"),
            row.getBoolean("enabled")
        );
    }
}
