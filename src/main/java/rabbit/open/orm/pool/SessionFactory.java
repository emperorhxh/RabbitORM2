package rabbit.open.orm.pool;

import java.sql.Connection;
import java.sql.SQLException;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.apache.log4j.Logger;

import rabbit.open.orm.ddl.DDLType;
import rabbit.open.orm.dialect.ddl.DDLHelper;
import rabbit.open.orm.dialect.dml.DeleteDialectAdapter;
import rabbit.open.orm.dialect.dml.DialectType;
import rabbit.open.orm.dml.DialectTransformer;
import rabbit.open.orm.dml.PolicyInsert;
import rabbit.open.orm.dml.xml.SQLParser;
import rabbit.open.orm.pool.jpa.SessionProxy;

public class SessionFactory {

    // 数据源
    protected DataSource dataSource;

    // 是否显示sql
    protected boolean showSql = false;

    // 是否格式化sql
    protected boolean formatSql = false;

    // 是否显示真是的预编译sql
    protected boolean maskPreparedSql = false;

    // ddl类型
    protected String ddl = DDLType.NONE.name();

    // 方言
    protected String dialect;

    private String mappingFiles;

    // 需要扫描的包
    protected String packages2Scan = "";

    private static Logger logger = Logger.getLogger(SessionFactory.class);

    private static ThreadLocal<Object> tag = new ThreadLocal<>();

    private static ThreadLocal<Connection> connectionContext = new ThreadLocal<>();

    public Connection getConnection() throws SQLException {
        if (isTransactionOpen()) {
            if (null != connectionContext.get()) {
                return connectionContext.get();
            } else {
                Connection conn = SessionProxy.getProxy(dataSource
                        .getConnection());
                connectionContext.set(conn);
                if (conn.getAutoCommit()) {
                    conn.setAutoCommit(false);
                }
                return conn;
            }
        } else {
            Connection conn = SessionProxy.getProxy(dataSource.getConnection());
            if (!conn.getAutoCommit()) {
                conn.setAutoCommit(true);
            }
            return conn;
        }
    }

    // 开启事务
    public static void beginTransaction(Object transactionObject) {
        if (null == tag.get()) {
            tag.set(transactionObject);
        }
    }

    /**
     * 
     * <b>Description: 提交事务</b><br>
     * @param transactionObject 事务对象
     * 
     */
    public static void commit(Object transactionObject) {
        if (null == tag.get() || !tag.get().equals(transactionObject)) {
            return;
        }
        tag.remove();
        Connection conn = connectionContext.get();
        if (null == conn) {
            return;
        }
        try {
            conn.commit();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                conn.close();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            connectionContext.remove();
        }
    }

    /**
     * 
     * <b>Description: 回滚操作</b><br>
     * @param transactionObj
     * 
     */
    public static void rollBack(Object transactionObj) {
        if (null == tag.get() || !tag.get().equals(transactionObj)) {
            return;
        }
        tag.remove();
        Connection conn = connectionContext.get();
        if (null == conn) {
            return;
        }
        try {
            conn.rollback();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                conn.close();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            connectionContext.remove();
        }
    }

    /**
     * 
     * <b>Description: 释放连接</b><br>
     * 
     * @param conn
     * 
     */
    public static void releaseConnection(Connection conn) {
        if (null == conn) {
            return;
        }
        if (isTransactionOpen()) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static boolean isTransactionOpen() {
        return null != tag.get();
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 
     * <b>Description: 执行ddl操作</b><br>
     * 
     */
    @PostConstruct
    public void setUp() {
        DialectTransformer.init();
        DeleteDialectAdapter.init();
        PolicyInsert.init();
        DDLHelper.init();
        DDLHelper.executeDDL(this, packages2Scan);
        if (!isEmpty(mappingFiles)) {
            new SQLParser(mappingFiles).doXmlParsing();
        }
    }

    private boolean isEmpty(String str) {
        return null == str || "".equals(str.trim());
    }

    public boolean isShowSql() {
        return showSql;
    }

    public void setShowSql(boolean showSql) {
        this.showSql = showSql;
    }

    public boolean isFormatSql() {
        return formatSql;
    }

    public void setFormatSql(boolean formatSql) {
        this.formatSql = formatSql;
    }

    public String getDdl() {
        return ddl;
    }

    public void setDdl(String ddl) {
        this.ddl = ddl;
    }

    public String getPackages2Scan() {
        return packages2Scan;
    }

    public void setPackages2Scan(String packages2Scan) {
        this.packages2Scan = packages2Scan;
    }

    public DialectType getDialectType() {
        return DialectType.format(dialect);
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public void setMappingFiles(String mappingFiles) {
        this.mappingFiles = mappingFiles;
    }

    public boolean isMaskPreparedSql() {
        return maskPreparedSql;
    }

    public void setMaskPreparedSql(boolean maskPreparedSql) {
        this.maskPreparedSql = maskPreparedSql;
    }

}