package rabbit.open.orm.dml;

import rabbit.open.orm.dialect.dml.DeleteDialectAdapter;


/**
 * <b>Description: 	oracle删除语句方言生成器</b><br>
 * <b>@author</b>	肖乾斌
 * 
 */
public class OracleDeleteGenerator extends DeleteDialectAdapter{

	@Override
	public StringBuilder createDeleteSql(Delete<?> delete) {
		StringBuilder sql = new StringBuilder("DELETE FROM " + delete.metaData.getTableName());
		sql.append(" WHERE " + delete.metaData.getTableName() + "." + delete.metaData.getPrimaryKey() + " IN (");
		sql.append("SELECT " + delete.metaData.getTableName() + "." + delete.metaData.getPrimaryKey() + " FROM " 
					+ delete.metaData.getTableName());
		sql.append(delete.generateInnerJoinsql());
		sql.append(delete.generateFilterSql());
		sql.append(")");
		return sql;
	}

}
