package com.batchinference.store;

import com.batchinference.config.AppProperties;
import com.batchinference.dto.JobStatusResponse;
import com.batchinference.dto.ResultItem;
import com.batchinference.model.ItemStatus;
import com.batchinference.model.JobStatus;
import com.batchinference.model.PromptItem;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite-backed persistence for batch jobs and per-prompt results.
 * <p>
 * Stores job metadata, aggregate counters, and one row per prompt item. Results are written
 * incrementally during processing so memory does not grow with batch size.
 */
@Repository
public class JobStore {

    private final AppProperties appProperties;
    private final String jdbcUrl;

    public JobStore(AppProperties appProperties) {
        this.appProperties = appProperties;
        Path dbPath = Path.of(appProperties.getDataDir(), "jobs.db");
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    @PostConstruct
    void init() throws Exception {
        Files.createDirectories(Path.of(appProperties.getDataDir()));
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS jobs (
                        job_id TEXT PRIMARY KEY,
                        status TEXT NOT NULL,
                        source_file TEXT NOT NULL,
                        webhook_url TEXT,
                        total INTEGER NOT NULL DEFAULT 0,
                        completed INTEGER NOT NULL DEFAULT 0,
                        succeeded INTEGER NOT NULL DEFAULT 0,
                        failed INTEGER NOT NULL DEFAULT 0,
                        error_message TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS job_items (
                        job_id TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        prompt TEXT NOT NULL,
                        status TEXT NOT NULL,
                        response TEXT,
                        error TEXT,
                        PRIMARY KEY (job_id, item_id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_job_items_job_id ON job_items(job_id)");
        }
    }

    public String createJob(String sourceFile, String webhookUrl) throws SQLException {
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO jobs(job_id, status, source_file, webhook_url, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, jobId);
            ps.setString(2, JobStatus.PENDING.name());
            ps.setString(3, sourceFile);
            ps.setString(4, webhookUrl);
            ps.setString(5, now.toString());
            ps.setString(6, now.toString());
            ps.executeUpdate();
        }
        return jobId;
    }

    public void insertItems(String jobId, List<PromptItem> items) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO job_items(job_id, item_id, prompt, status)
                     VALUES (?, ?, ?, ?)
                     """)) {
            for (PromptItem item : items) {
                ps.setString(1, jobId);
                ps.setString(2, item.id());
                ps.setString(3, item.prompt());
                ps.setString(4, ItemStatus.PENDING.name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        updateJobCounts(jobId);
    }

    public void updateJobStatus(String jobId, JobStatus status, String errorMessage) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE jobs
                     SET status = ?, error_message = ?, updated_at = ?
                     WHERE job_id = ?
                     """)) {
            ps.setString(1, status.name());
            ps.setString(2, errorMessage);
            ps.setString(3, Instant.now().toString());
            ps.setString(4, jobId);
            ps.executeUpdate();
        }
    }

    public void markItemSuccess(String jobId, String itemId, String response) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE job_items
                     SET status = ?, response = ?, error = NULL
                     WHERE job_id = ? AND item_id = ?
                     """)) {
            ps.setString(1, ItemStatus.SUCCESS.name());
            ps.setString(2, response);
            ps.setString(3, jobId);
            ps.setString(4, itemId);
            ps.executeUpdate();
        }
        updateJobCounts(jobId);
    }

    public void markItemFailed(String jobId, String itemId, String error) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE job_items
                     SET status = ?, error = ?
                     WHERE job_id = ? AND item_id = ?
                     """)) {
            ps.setString(1, ItemStatus.FAILED.name());
            ps.setString(2, error);
            ps.setString(3, jobId);
            ps.setString(4, itemId);
            ps.executeUpdate();
        }
        updateJobCounts(jobId);
    }

    public Optional<JobStatusResponse> getJobStatus(String jobId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT job_id, status, source_file, total, completed, succeeded, failed,
                            created_at, updated_at, error_message
                     FROM jobs WHERE job_id = ?
                     """)) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int total = rs.getInt("total");
                int completed = rs.getInt("completed");
                return Optional.of(new JobStatusResponse(
                        rs.getString("job_id"),
                        JobStatus.valueOf(rs.getString("status")),
                        total,
                        completed,
                        rs.getInt("succeeded"),
                        rs.getInt("failed"),
                        total - completed,
                        Instant.parse(rs.getString("created_at")),
                        Instant.parse(rs.getString("updated_at")),
                        rs.getString("source_file"),
                        rs.getString("error_message")
                ));
            }
        }
    }

    public List<ResultItem> getResults(String jobId) throws SQLException {
        return queryResults(jobId, null, null, null);
    }

    /**
     * Returns item results, optionally filtered by status and paginated.
     *
     * @param statusFilter when null, returns only completed items (SUCCESS and FAILED)
     */
    public List<ResultItem> queryResults(
            String jobId,
            ItemStatus statusFilter,
            Integer limit,
            Integer offset
    ) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT item_id, status, prompt, response, error
                FROM job_items
                WHERE job_id = ?
                """);
        if (statusFilter != null) {
            sql.append(" AND status = ?");
        } else {
            sql.append(" AND status != 'PENDING'");
        }
        sql.append(" ORDER BY item_id");
        if (limit != null) {
            sql.append(" LIMIT ?");
        }
        if (offset != null) {
            sql.append(" OFFSET ?");
        }

        List<ResultItem> results = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int param = 1;
            ps.setString(param++, jobId);
            if (statusFilter != null) {
                ps.setString(param++, statusFilter.name());
            }
            if (limit != null) {
                ps.setInt(param++, limit);
            }
            if (offset != null) {
                ps.setInt(param, offset);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(toResultItem(rs));
                }
            }
        }
        return results;
    }

    private ResultItem toResultItem(ResultSet rs) throws SQLException {
        return new ResultItem(
                rs.getString("item_id"),
                ItemStatus.valueOf(rs.getString("status")),
                rs.getString("prompt"),
                rs.getString("response"),
                rs.getString("error")
        );
    }

    public Optional<String> getWebhookUrl(String jobId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT webhook_url FROM jobs WHERE job_id = ?")) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("webhook_url"));
            }
        }
    }

    public void updateWebhookUrl(String jobId, String webhookUrl) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE jobs SET webhook_url = ?, updated_at = ? WHERE job_id = ?
                     """)) {
            ps.setString(1, webhookUrl);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, jobId);
            ps.executeUpdate();
        }
    }

    private void updateJobCounts(String jobId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE jobs
                     SET total = (SELECT COUNT(*) FROM job_items WHERE job_id = ?),
                         completed = (SELECT COUNT(*) FROM job_items WHERE job_id = ? AND status != 'PENDING'),
                         succeeded = (SELECT COUNT(*) FROM job_items WHERE job_id = ? AND status = 'SUCCESS'),
                         failed = (SELECT COUNT(*) FROM job_items WHERE job_id = ? AND status = 'FAILED'),
                         updated_at = ?
                     WHERE job_id = ?
                     """)) {
            for (int i = 1; i <= 4; i++) {
                ps.setString(i, jobId);
            }
            ps.setString(5, Instant.now().toString());
            ps.setString(6, jobId);
            ps.executeUpdate();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
