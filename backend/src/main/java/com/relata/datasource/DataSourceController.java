package com.relata.datasource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong idSequence;

    public DataSourceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idSequence = new AtomicLong(nextDataSourceId());
        seedDefaultDataSource();
    }

    @GetMapping
    public List<DataSourceResponse> list() {
        return findAllRecords().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public DataSourceResponse detail(@PathVariable long id) {
        return toResponse(findRecord(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataSourceResponse create(@Valid @RequestBody CreateDataSourceRequest request) {
        long id = idSequence.getAndIncrement();
        jdbcTemplate.update("""
                        insert into data_source (
                            id, name, database_type, jdbc_url, username, password_cipher, description,
                            status, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, 'NOT_TESTED', current_timestamp, current_timestamp)
                        """,
                String.valueOf(id),
                request.name(),
                request.databaseType(),
                request.jdbcUrl(),
                request.username(),
                normalizePassword(request.password()),
                request.description()
        );
        return toResponse(findRecord(id));
    }

    @PutMapping("/{id}")
    public DataSourceResponse update(@PathVariable long id, @Valid @RequestBody UpdateDataSourceRequest request) {
        DataSourceRecord current = findRecord(id);
        jdbcTemplate.update("""
                        update data_source
                        set name = ?, database_type = ?, jdbc_url = ?, username = ?, password_cipher = ?,
                            description = ?, updated_at = current_timestamp
                        where id = ?
                        """,
                request.name(),
                request.databaseType(),
                request.jdbcUrl(),
                request.username(),
                request.password() == null || request.password().isBlank() ? current.password() : request.password(),
                request.description(),
                String.valueOf(id)
        );
        return toResponse(findRecord(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        DataSourceRecord record = findRecord(id);
        deleteMetadata(record.id());
        jdbcTemplate.update("delete from data_source where id = ?", record.id());
    }

    @PostMapping("/test")
    public TestConnectionResponse testConnection(@Valid @RequestBody TestConnectionRequest request) {
        try (Connection ignored = DriverManager.getConnection(request.jdbcUrl(), request.username(), normalizePassword(request.password()))) {
            return new TestConnectionResponse(true, "连接测试通过：" + request.name(), Instant.now());
        } catch (SQLException ex) {
            return new TestConnectionResponse(false, "连接测试失败：" + ex.getMessage(), Instant.now());
        }
    }

    @PostMapping("/{id}/test")
    public TestConnectionResponse testExistingConnection(@PathVariable long id) {
        DataSourceRecord record = findRecord(id);
        Instant testedAt = Instant.now();
        try (Connection ignored = connect(record)) {
            updateConnectionStatus(record.id(), "CONNECTED", testedAt);
            return new TestConnectionResponse(true, "连接测试通过：" + record.name(), testedAt);
        } catch (SQLException ex) {
            updateConnectionStatus(record.id(), "FAILED", testedAt);
            return new TestConnectionResponse(false, "连接测试失败：" + ex.getMessage(), testedAt);
        }
    }

    @PostMapping("/{id}/metadata")
    public MetadataCollectResponse collectMetadata(@PathVariable long id) {
        DataSourceRecord record = findRecord(id);
        List<TableMetadata> metadata = readDatabaseMetadata(record);
        Instant collectedAt = Instant.now();
        persistMetadata(record.id(), metadata, collectedAt);
        return new MetadataCollectResponse(
                Long.parseLong(record.id()),
                record.name(),
                metadata.size(),
                metadata.stream().mapToInt(table -> table.columns().size()).sum(),
                collectedAt,
                metadata
        );
    }

    @GetMapping("/{id}/metadata/tables")
    public MetadataTablePage listMetadataTables(
            @PathVariable long id,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        DataSourceRecord record = findRecord(id);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String likeKeyword = "%" + normalizedKeyword.toLowerCase() + "%";
        int offset = (safePage - 1) * safeSize;

        Integer total = jdbcTemplate.queryForObject("""
                        select count(*)
                        from table_metadata
                        where data_source_id = ?
                          and (
                              ? = ''
                              or lower(table_name) like ?
                              or lower(coalesce(table_comment, '')) like ?
                          )
                        """,
                Integer.class,
                record.id(),
                normalizedKeyword,
                likeKeyword,
                likeKeyword
        );

        List<MetadataTableItem> tables = jdbcTemplate.query("""
                        select t.id, t.table_name, t.table_comment, count(c.id) as column_count
                        from table_metadata t
                        left join column_metadata c on c.table_metadata_id = t.id
                        where t.data_source_id = ?
                          and (
                              ? = ''
                              or lower(t.table_name) like ?
                              or lower(coalesce(t.table_comment, '')) like ?
                          )
                        group by t.id, t.table_name, t.table_comment
                        order by t.table_name
                        limit ? offset ?
                        """,
                (rs, rowNum) -> new MetadataTableItem(
                        rs.getString("id"),
                        rs.getString("table_name"),
                        normalizeComment(rs.getString("table_comment")),
                        rs.getInt("column_count"),
                        loadColumns(rs.getString("id"))
                ),
                record.id(),
                normalizedKeyword,
                likeKeyword,
                likeKeyword,
                safeSize,
                offset
        );

        return new MetadataTablePage(
                Long.parseLong(record.id()),
                safePage,
                safeSize,
                total == null ? 0 : total,
                tables
        );
    }

    @DeleteMapping("/{id}/metadata/tables/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMetadataTable(@PathVariable long id, @PathVariable String tableId) {
        DataSourceRecord record = findRecord(id);
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from table_metadata where data_source_id = ? and id = ?",
                Integer.class,
                record.id(),
                tableId
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Metadata table not found: " + tableId);
        }
        jdbcTemplate.update("delete from column_metadata where table_metadata_id = ?", tableId);
        jdbcTemplate.update("delete from table_metadata where data_source_id = ? and id = ?", record.id(), tableId);
    }

    private long nextDataSourceId() {
        return jdbcTemplate.queryForList("select id from data_source", String.class).stream()
                .map(DataSourceController::parseLongOrNull)
                .filter(value -> value != null)
                .max(Long::compareTo)
                .map(value -> value + 1)
                .orElse(2L);
    }

    private void seedDefaultDataSource() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from data_source", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                        insert into data_source (
                            id, name, database_type, jdbc_url, username, password_cipher, description,
                            status, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, 'NOT_TESTED', current_timestamp, current_timestamp)
                        """,
                "1",
                "h2-local",
                "H2",
                "jdbc:h2:file:./data/relata;MODE=MySQL;DATABASE_TO_UPPER=false",
                "sa",
                "",
                "本地 H2 数据库"
        );
    }

    private List<DataSourceRecord> findAllRecords() {
        return jdbcTemplate.query("""
                        select id, name, database_type, jdbc_url, username, password_cipher, description,
                               coalesce(status, 'NOT_TESTED') as status, last_tested_at, last_collected_at
                        from data_source
                        order by created_at, id
                        """,
                (rs, rowNum) -> new DataSourceRecord(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("database_type"),
                        rs.getString("jdbc_url"),
                        rs.getString("username"),
                        normalizePassword(rs.getString("password_cipher")),
                        rs.getString("description"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("last_tested_at")),
                        toInstant(rs.getTimestamp("last_collected_at"))
                )
        );
    }

    private DataSourceRecord findRecord(long id) {
        List<DataSourceRecord> records = jdbcTemplate.query("""
                        select id, name, database_type, jdbc_url, username, password_cipher, description,
                               coalesce(status, 'NOT_TESTED') as status, last_tested_at, last_collected_at
                        from data_source
                        where id = ?
                        """,
                (rs, rowNum) -> new DataSourceRecord(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("database_type"),
                        rs.getString("jdbc_url"),
                        rs.getString("username"),
                        normalizePassword(rs.getString("password_cipher")),
                        rs.getString("description"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("last_tested_at")),
                        toInstant(rs.getTimestamp("last_collected_at"))
                ),
                String.valueOf(id)
        );
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Data source not found: " + id);
        }
        return records.get(0);
    }

    private DataSourceResponse toResponse(DataSourceRecord record) {
        List<TableMetadata> metadata = loadMetadata(record.id());
        return new DataSourceResponse(
                Long.parseLong(record.id()),
                record.name(),
                record.databaseType(),
                record.jdbcUrl(),
                record.username(),
                record.description(),
                record.status(),
                record.lastTestedAt(),
                record.lastCollectedAt(),
                metadata.size(),
                metadata
        );
    }

    private List<TableMetadata> loadMetadata(String dataSourceId) {
        List<TableRow> tableRows = jdbcTemplate.query("""
                        select id, table_name, table_comment
                        from table_metadata
                        where data_source_id = ?
                        order by table_name
                        """,
                (rs, rowNum) -> new TableRow(
                        rs.getString("id"),
                        rs.getString("table_name"),
                        normalizeComment(rs.getString("table_comment"))
                ),
                dataSourceId
        );
        return tableRows.stream()
                .map(table -> new TableMetadata(table.name(), table.comment(), loadColumns(table.id())))
                .toList();
    }

    private List<ColumnMetadata> loadColumns(String tableMetadataId) {
        return jdbcTemplate.query("""
                        select column_name, data_type, column_comment,
                               coalesce(primary_key_flag, false) as primary_key_flag,
                               coalesce(foreign_key_flag, false) as foreign_key_flag
                        from column_metadata
                        where table_metadata_id = ?
                        order by ordinal_position, column_name
                        """,
                (rs, rowNum) -> new ColumnMetadata(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getBoolean("primary_key_flag"),
                        rs.getBoolean("foreign_key_flag"),
                        normalizeComment(rs.getString("column_comment"))
                ),
                tableMetadataId
        );
    }

    private void updateConnectionStatus(String id, String status, Instant testedAt) {
        jdbcTemplate.update("""
                        update data_source
                        set status = ?, last_tested_at = ?, updated_at = current_timestamp
                        where id = ?
                        """,
                status,
                Timestamp.from(testedAt),
                id
        );
    }

    private void persistMetadata(String dataSourceId, List<TableMetadata> metadata, Instant collectedAt) {
        deleteMetadata(dataSourceId);
        for (TableMetadata table : metadata) {
            String tableId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                            insert into table_metadata (
                                id, data_source_id, table_name, table_comment, table_type, created_at
                            ) values (?, ?, ?, ?, 'TABLE', current_timestamp)
                            """,
                    tableId,
                    dataSourceId,
                    table.name(),
                    table.comment()
            );
            int position = 1;
            for (ColumnMetadata column : table.columns()) {
                jdbcTemplate.update("""
                                insert into column_metadata (
                                    id, table_metadata_id, column_name, data_type, column_comment,
                                    nullable_flag, primary_key_flag, foreign_key_flag, ordinal_position, created_at
                                ) values (?, ?, ?, ?, ?, true, ?, ?, ?, current_timestamp)
                                """,
                        UUID.randomUUID().toString(),
                        tableId,
                        column.name(),
                        column.type(),
                        column.comment(),
                        column.primaryKey(),
                        column.foreignKey(),
                        position++
                );
            }
        }
        jdbcTemplate.update("""
                        update data_source
                        set last_collected_at = ?, updated_at = current_timestamp
                        where id = ?
                        """,
                Timestamp.from(collectedAt),
                dataSourceId
        );
    }

    private void deleteMetadata(String dataSourceId) {
        jdbcTemplate.update("""
                        delete from column_metadata
                        where table_metadata_id in (
                            select id from table_metadata where data_source_id = ?
                        )
                        """,
                dataSourceId
        );
        jdbcTemplate.update("delete from table_metadata where data_source_id = ?", dataSourceId);
    }

    private Connection connect(DataSourceRecord record) throws SQLException {
        return DriverManager.getConnection(record.jdbcUrl(), record.username(), record.password());
    }

    private List<TableMetadata> readDatabaseMetadata(DataSourceRecord record) {
        try (Connection connection = connect(record)) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<TableMetadata> tables = new ArrayList<>();
            try (ResultSet tableRows = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (tableRows.next()) {
                    String schema = tableRows.getString("TABLE_SCHEM");
                    if (isSystemSchema(schema)) {
                        continue;
                    }
                    String tableName = tableRows.getString("TABLE_NAME");
                    String comment = normalizeComment(tableRows.getString("REMARKS"));
                    tables.add(new TableMetadata(tableName, comment, readColumns(metaData, connection.getCatalog(), schema, tableName)));
                }
            }
            tables.sort(Comparator.comparing(TableMetadata::name));
            return tables;
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "元数据采集失败：" + ex.getMessage(), ex);
        }
    }

    private List<ColumnMetadata> readColumns(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        Set<String> primaryKeys = readPrimaryKeys(metaData, catalog, schema, tableName);
        Set<String> foreignKeys = readForeignKeys(metaData, catalog, schema, tableName);
        List<ColumnMetadata> columns = new ArrayList<>();
        try (ResultSet columnRows = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (columnRows.next()) {
                String columnName = columnRows.getString("COLUMN_NAME");
                columns.add(new ColumnMetadata(
                        columnName,
                        columnRows.getString("TYPE_NAME"),
                        primaryKeys.contains(columnName),
                        foreignKeys.contains(columnName),
                        normalizeComment(columnRows.getString("REMARKS"))
                ));
            }
        }
        return columns;
    }

    private Set<String> readPrimaryKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet rows = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (rows.next()) {
                keys.add(rows.getString("COLUMN_NAME"));
            }
        }
        return keys;
    }

    private Set<String> readForeignKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (ResultSet rows = metaData.getImportedKeys(catalog, schema, tableName)) {
            while (rows.next()) {
                keys.add(rows.getString("FKCOLUMN_NAME"));
            }
        }
        return keys;
    }

    private static boolean isSystemSchema(String schema) {
        return schema != null && List.of("INFORMATION_SCHEMA", "SYS", "SYSTEM", "PG_CATALOG").contains(schema.toUpperCase());
    }

    private static String normalizeComment(String comment) {
        return comment == null || comment.isBlank() ? "" : comment;
    }

    private static String normalizePassword(String password) {
        return password == null ? "" : password;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record CreateDataSourceRequest(
            @NotBlank String name,
            @NotBlank String databaseType,
            @NotBlank String jdbcUrl,
            @NotBlank String username,
            String password,
            String description
    ) {
    }

    public record UpdateDataSourceRequest(
            @NotBlank String name,
            @NotBlank String databaseType,
            @NotBlank String jdbcUrl,
            @NotBlank String username,
            String password,
            String description
    ) {
    }

    public record TestConnectionRequest(
            @NotBlank String name,
            @NotBlank String databaseType,
            @NotBlank String jdbcUrl,
            @NotBlank String username,
            String password
    ) {
    }

    public record TestConnectionResponse(boolean success, String message, Instant testedAt) {
    }

    public record MetadataCollectResponse(
            long dataSourceId,
            String dataSourceName,
            int tableCount,
            int columnCount,
            Instant collectedAt,
            List<TableMetadata> tables
    ) {
    }

    public record MetadataTablePage(
            long dataSourceId,
            int page,
            int size,
            int total,
            List<MetadataTableItem> tables
    ) {
    }

    public record MetadataTableItem(
            String id,
            String name,
            String comment,
            int columnCount,
            List<ColumnMetadata> columns
    ) {
    }

    public record DataSourceResponse(
            long id,
            String name,
            String databaseType,
            String jdbcUrl,
            String username,
            String description,
            String status,
            Instant lastTestedAt,
            Instant lastCollectedAt,
            int tableCount,
            List<TableMetadata> metadata
    ) {
    }

    public record TableMetadata(String name, String comment, List<ColumnMetadata> columns) {
    }

    public record ColumnMetadata(String name, String type, boolean primaryKey, boolean foreignKey, String comment) {
    }

    private record DataSourceRecord(
            String id,
            String name,
            String databaseType,
            String jdbcUrl,
            String username,
            String password,
            String description,
            String status,
            Instant lastTestedAt,
            Instant lastCollectedAt
    ) {
    }

    private record TableRow(String id, String name, String comment) {
    }
}
