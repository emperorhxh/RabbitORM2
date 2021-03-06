package rabbit.open.orm.pool.jpa;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public abstract class AbstractConnection implements Connection{

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return null;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return false;
	}

	@Override
	public CallableStatement prepareCall(String sql) throws SQLException {
		return null;
	}

	@Override
	public String nativeSQL(String sql) throws SQLException {
		return null;
	}

	@Override
	public void setCatalog(String catalog) throws SQLException {
		
	}

	@Override
	public String getCatalog() throws SQLException {
		return null;
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return null;
	}

	@Override
	public void clearWarnings() throws SQLException {
		
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency)
			throws SQLException {
		
		return null;
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType,
			int resultSetConcurrency) throws SQLException {
		
		return null;
	}

	@Override
	public CallableStatement prepareCall(String sql, int resultSetType,
			int resultSetConcurrency) throws SQLException {
		
		return null;
	}

	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		
		return null;
	}

	@Override
	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		
		
	}

	@Override
	public void setHoldability(int holdability) throws SQLException {
		
		
	}

	@Override
	public int getHoldability() throws SQLException {
		
		return 0;
	}

	@Override
	public Statement createStatement(int resultSetType,
			int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		
		return null;
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType,
			int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		
		return null;
	}

	@Override
	public CallableStatement prepareCall(String sql, int resultSetType,
			int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		
		return null;
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
			throws SQLException {
		
		return null;
	}

	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames)
			throws SQLException {
		
		return null;
	}

	@Override
	public Clob createClob() throws SQLException {
		
		return null;
	}

	@Override
	public Blob createBlob() throws SQLException {
		
		return null;
	}

	@Override
	public NClob createNClob() throws SQLException {
		
		return null;
	}

	@Override
	public SQLXML createSQLXML() throws SQLException {
		
		return null;
	}

	@Override
	public boolean isValid(int timeout) throws SQLException {
		
		return false;
	}

	@Override
	public void setClientInfo(String name, String value)
			throws SQLClientInfoException {
		
		
	}

	@Override
	public void setClientInfo(Properties properties)
			throws SQLClientInfoException {
		
		
	}

	@Override
	public String getClientInfo(String name) throws SQLException {
		
		return null;
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		
		return null;
	}

	@Override
	public Array createArrayOf(String typeName, Object[] elements)
			throws SQLException {
		
		return null;
	}

	@Override
	public Struct createStruct(String typeName, Object[] attributes)
			throws SQLException {
		
		return null;
	}

	@Override
	public void setSchema(String schema) throws SQLException {
		
		
	}

	@Override
	public String getSchema() throws SQLException {
		
		return null;
	}

	@Override
	public void abort(Executor executor) throws SQLException {
		
		
	}

	@Override
	public void setNetworkTimeout(Executor executor, int milliseconds)
			throws SQLException {
		
		
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		
		return 0;
	}

	
}
