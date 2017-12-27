package rabbit.open.orm.pool.jpa;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.Logger;

import rabbit.open.orm.exception.DataSourceClosedException;
import rabbit.open.orm.exception.GetConnectionTimeOutException;
import rabbit.open.orm.exception.RabbitDMLException;
import rabbit.open.orm.exception.RabbitORMException;

/**
 * <b>Description: 	rabbit数据源</b><br>
 * <b>@author</b>	肖乾斌
 * 
 */
public class RabbitDataSource extends AbstractDataSource{

	protected Logger logger = Logger.getLogger(getClass());
	
	protected LinkedBlockingDeque<Connection> connectors = new LinkedBlockingDeque<>();
	
	protected DataSourceMonitor monitor;
	
	/**
	 * 标记数据源是否已经关闭
	 */
	protected boolean closed = false;
	
	/**
	 * 创建session时使用的锁
	 */
	private ReentrantLock sessionCreateLock = new ReentrantLock();
	
	/**
	 * 计数器
	 */
	private int counter = 0;
	
	/**
	 * 获取一个可用的连接
	 */
	@Override
	public Connection getConnection() throws SQLException {
		if(closed){
			throw new DataSourceClosedException("data source is closed!");
		}
		Connection first = connectors.pollFirst();
		if(null != first){
			return first;
		}
		try2CreateNewSession();
		if(counter < maxSize){
			return getConnection();
		}else{
			return pollConnection(15);
			
		}
	}

	/**
	 * 
	 * <b>Description: 获取一个连接</b><br>
	 * @param 	seconds		超时秒数
	 * @return
	 * @throws 	RabbitORMException
	 * 
	 */
	private Connection pollConnection(int seconds) throws RabbitORMException{
		try {
			Connection first;
			first = connectors.pollFirst(seconds, TimeUnit.SECONDS);
			if(null == first){
				throw new GetConnectionTimeOutException("get connection timeout[15s]!");
			}
			return first;
		} catch (Exception e) {
			throw new RabbitORMException(e);
		}
	}

	/**
	 * 
	 * <b>Description: 尝试新建一个数据库连接</b><br>
	 * 
	 */
	private void try2CreateNewSession(){
		try{
			sessionCreateLock.lock();
			if(closed){
				throw new DataSourceClosedException("data source is closed!");
			}
			if(counter >= maxSize){
				return;
			}
			Session session = new Session(DriverManager.getConnection(url, username, password), this);
            counter++;
			connectors.addFirst(session);
			logger.info("new session[" + session + "] is created! [" + counter + "] session alive! "
					+ "[" + connectors.size() + "] sessions is idle");
		} catch (Exception e) {
		    throw new RabbitDMLException(e);
		} finally {
			sessionCreateLock.unlock();
		}
	}
	
	/**
	 * 
	 * <b>Description: 释放连接资源</b><br>
	 * @param conn
	 * 
	 */
	public void releaseSession(Session conn){
		try {
			connectors.putFirst(conn);
		} catch (Exception e) {
			logger.info(e.getMessage(), e);
		}
	}
	
	/**
	 * 
	 * <b>Description: 初始化</b><br>
	 * 
	 */
	@PostConstruct
	public void init(){
		loadDriverClass();
		initSessions();
		monitor = new DataSourceMonitor(this);
		monitor.start();
	}

	/**
	 * 
	 * <b>Description: 初始化连接直至满足最小连接数</b><br>
	 * 
	 */
	protected void initSessions(){
	    while(counter < minSize){
	        try2CreateNewSession();
	    }
	}

	/**
	 * 
	 * <b>Description: 加载驱动class</b><br>
	 * 
	 */
	protected void loadDriverClass() {
		try {
			Class.forName(driverClass);
		} catch (ClassNotFoundException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 
	 * <b>Description: 获取db类型</b><br>
	 * @return
	 * 
	 */
	public DBType getDBType(){
		if(driverClass.toLowerCase().contains("oracle")){
			return DBType.ORACLE;
		}
		if(driverClass.toLowerCase().contains("sqlserver")){
			return DBType.SQLSERVER;
		}
		if(driverClass.toLowerCase().contains("db2")){
			return DBType.DB2;
		}
		if(driverClass.toLowerCase().contains("mysql")){
            return DBType.MYSQL;
        }
		throw new RabbitDMLException("unkown driver type[" + driverClass + "]");
	}
	
	
	
	/**
	 * 
	 * <b>Description: 销毁</b><br>
	 * 
	 */
	@PreDestroy
	public void shutdown(){
		logger.info("datasource is closing.....");
		monitor.shutdown();
		setDataSourceClosed(true);
		closeAllSessions();
		logger.info("datasource is successfully closed!");
	}
	
	/**
	 * 
	 * <b>Description:	重启数据源</b><br>
	 * 
	 */
	public void restart(){
		logger.info("datasource is restarting.....");
		setDataSourceClosed(true);
		closeAllSessions();
		setDataSourceClosed(false);
		try {
	        initSessions();
		} catch (Exception e) {
		    logger.warn(e.getMessage());
		} 
		logger.info("datasource is successfully restarted!");
	}

	private void setDataSourceClosed(boolean closed) {
		this.closed = closed;
	}

	/**
	 * 
	 * <b>Description: 关闭资源</b><br>
	 * @throws SQLException
	 * 
	 */
	public void closeSession(Session session){
		try{
			sessionCreateLock.lock();
			counter--;
			session.getConnector().close();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}finally{
			sessionCreateLock.unlock();
		}
	}
	
	/**
	 * 
	 * <b>Description: 关闭所有的连接</b><br>
	 * 
	 */
	private void closeAllSessions() {
		while(counter != 0){
			try {
				Session session = (Session) pollConnection(10);
				session.destroy();
			} catch (Exception e) {
				logger.warn(e.getMessage(), e);
			}
		}
	}

	public int getCounter() {
		return counter;
	}
	
	public LinkedBlockingDeque<Connection> getConnectors() {
		return connectors;
	}
}