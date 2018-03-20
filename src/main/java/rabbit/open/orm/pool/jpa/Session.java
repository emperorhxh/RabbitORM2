package rabbit.open.orm.pool.jpa;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;

import org.apache.log4j.Logger;

import rabbit.open.orm.pool.SessionFactory;

/**
 * <b>Description: 连接对象</b><br>
 * <b>@author</b> 肖乾斌
 * 
 */
public class Session extends AbstractConnection {

    private static final Integer DO_NOTHING = 9999;

    // 连接对象
    private Connection conn;

    // 上次活跃时间
    private long activeTime = 0;

    // 数据源
    private RabbitDataSource dataSource;

    // 缓存PreparedStatement
    private LinkedHashMap<String, PreparedStatement> cachedStmts;

    protected Logger logger = Logger.getLogger(getClass());

    public Session(Connection conn, RabbitDataSource dataSource) {
        super();
        cachedStmts = new LinkedHashMap<>();
        this.conn = conn;
        this.dataSource = dataSource;
        activeTime = System.currentTimeMillis();
    }

    /**
     * 释放连接回连接池
     */
    @Override
    public void close() {
        if (SessionFactory.hasSQLException()) {
            destroy();
        } else {
            releaseSession();
        }
    }

    private void releaseSession() {
        activeTime = System.currentTimeMillis();
        dataSource.releaseSession(this);
    }

    @Override
    public boolean isClosed() throws SQLException {
        return conn.isClosed();
    }

    @Override
    public void commit() throws SQLException {
        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (!conn.getAutoCommit()) {
            conn.rollback();
        }
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return conn.getAutoCommit();
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        if (autoCommit == conn.getAutoCommit()) {
            return;
        }
        conn.setAutoCommit(autoCommit);
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return conn.getMetaData();
    }

    @Override
    public Statement createStatement() throws SQLException {
        return conn.createStatement();
    }

    /**
     * 
     * <b>Description: 获取与session绑定的连接对象</b><br>
     * @return
     * 
     */
    public Connection getConnector() {
        return conn;
    }

    public long getActiveTime() {
        return activeTime;
    }

    private void closeStmts() {
        for (PreparedStatement stmt : cachedStmts.values()) {
            closeRealStmt(stmt);
        }
    }

    /**
     * 
     * <b>Description: 关闭真实的PreparedStatement</b><br>
     * @param stmt
     * 
     */
    private void closeRealStmt(PreparedStatement stmt) {
        try {
            for (Field field : stmt.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object fv = field.get(stmt);
                if (!(fv instanceof PreparedStatementProxy)) {
                    continue;
                }
                PreparedStatementProxy psp = (PreparedStatementProxy) fv;
                psp.destroy();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 
     * <b>Description: 关闭连接</b><br>
     * 
     */
    public void destroy() {
        closeStmts();
        dataSource.closeSession(this);
        logger.info("session{stmtments:" + cachedStmts.size() + "} closed, ["
                + dataSource.getCounter() + "] session left!");
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return prepareStatement(sql, DO_NOTHING);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int genKey)
            throws SQLException {
        String sqlKey = createSqlKey(sql);
        PreparedStatement cachedStmt = cachedStmts.remove(sqlKey);
        if (null != cachedStmt) {
            cachedStmts.put(sqlKey, cachedStmt);
            return cachedStmt;
        }
        if (cachedStmts.size() == dataSource.getMaxCachedStmt()) {
            PreparedStatement idleStmt = cachedStmts.remove(cachedStmts
                    .keySet().iterator().next());
            idleStmt.close();
            logger.warn("idle PreparedStatement is removed");
        }
        PreparedStatement stmt = null;
        if (DO_NOTHING == genKey) {
            stmt = PreparedStatementProxy.getProxy(conn.prepareStatement(sql));
        } else {
            stmt = PreparedStatementProxy.getProxy(conn.prepareStatement(sql,
                    genKey));
        }
        cachedStmts.put(sqlKey, stmt);
        return stmt;
    }

    private String createSqlKey(String sql) {
        return sql.replaceAll(" ", "");
    }

    public Session() {

    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        conn.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return conn.isReadOnly();
    }

}
