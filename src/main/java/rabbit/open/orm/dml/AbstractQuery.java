package rabbit.open.orm.dml;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rabbit.open.orm.annotation.Entity;
import rabbit.open.orm.annotation.ManyToMany;
import rabbit.open.orm.annotation.OneToMany;
import rabbit.open.orm.annotation.Relation.FilterType;
import rabbit.open.orm.dml.meta.DynamicFilterDescriptor;
import rabbit.open.orm.dml.meta.FieldMetaData;
import rabbit.open.orm.dml.meta.FilterDescriptor;
import rabbit.open.orm.dml.meta.JoinFieldMetaData;
import rabbit.open.orm.dml.meta.JoinFilter;
import rabbit.open.orm.dml.meta.MetaData;
import rabbit.open.orm.exception.InvalidFetchOperationException;
import rabbit.open.orm.exception.OrderAssociationException;
import rabbit.open.orm.exception.RabbitDMLException;
import rabbit.open.orm.pool.SessionFactory;

/**
 * <b>Description: 	查询操作</b><br>
 * <b>@author</b>	肖乾斌
 * @param <T>
 * 
 */
public abstract class AbstractQuery<T> extends DMLAdapter<T>{

	
	private static final String AND = " AND ";

    private static final String INNER_JOIN = " INNER JOIN ";

    private static final String LEFT_JOIN = "LEFT JOIN ";

    //希望查询出来的实体
	protected HashSet<Class<?>> entity2Fetch = new HashSet<>();
	
	//manytoone的过滤条件
	protected List<FilterDescriptor> many2oneFilterDescripters = new ArrayList<>();
	
	//是否分页查询
	protected boolean page = false;

	protected int pageSize = 0;
	
	protected int pageIndex = 0;
	
	protected boolean distinct = false;
	
	protected Map<Class<?>, HashSet<String>> asc = new ConcurrentHashMap<>();
	
	protected Map<Class<?>, HashSet<String>> desc = new ConcurrentHashMap<>();
	
	//缓存需要fetch的对象
	private Map<Class<?>, Class<?>> fetchClzes = new ConcurrentHashMap<>();
	
	//别名映射key是表名
	protected Map<String, String> aliasMapping = new HashMap<>();
	
	//一对多查询条件
	protected List<JoinFieldMetaData<?>> joinFieldMetas = new ArrayList<>();
	
	public AbstractQuery(SessionFactory fatory, Class<T> clz) {
		super(fatory, clz);
	}

	public AbstractQuery(SessionFactory fatory, T filterData, Class<T> clz) {
		super(fatory, filterData, clz);
	}
	
	/**
	 * 
	 * <b>Description:	执行sql命令</b><br>
	 * @return
	 * 
	 */
	public Result<T> execute() {
		createQuerySql();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try{
			conn = sessionFactory.getConnection();
			showSql();
			stmt = conn.prepareStatement(sql.toString());
			setPreparedStatementValue(stmt);
			rs = stmt.executeQuery();
			List<T> resultList = readDataFromResultSets(rs);
			rs.close();
			return new Result<>(resultList);
		} catch (Exception e){
			throw new RabbitDMLException(e.getMessage(), e);
		} finally {
		    closeResultSet(rs);
		    closeConnection(conn);
		    DMLAdapter.closeStmt(stmt);
		}
	}

	private AbstractQuery<T> setPageIndex(int pageIndex){
		this.pageIndex = pageIndex;
		page = true;
		return this;
	}

	/**
	 * 
	 * <b>Description:	分页</b><br>
	 * @param pageIndex
	 * @param pageSize
	 * @return	
	 * 
	 */
	public AbstractQuery<T> page(int pageIndex, int pageSize){
		setPageIndex(pageIndex);
		setPageSize(pageSize);
		return this;
	}
	
	private AbstractQuery<T> setPageSize(int pageSize){
		this.pageSize = pageSize;
		page = true;
		return this;
	}

	private List<T> readDataFromResultSets(ResultSet rs) throws Exception{
		List<T> resultList = new ArrayList<>();
		while(rs.next()){
			T rowData = readRowData(rs);
			Object pkv = getPrimaryKeyValue(rowData);
			boolean exist = false;
			for(int i = 0; i < resultList.size(); i++){
				if(pkv.equals(getPrimaryKeyValue(resultList.get(i)))){
					exist = true;
					combineResult(resultList, rowData, i);
					break;
				}
			}
			if(!exist){
				resultList.add(rowData);
			}
		}
		return resultList;
	}
	
	/**
	 * 
	 * 合并结果集
	 * @param 	resultList
	 * @param 	rowData
	 * @param 	index
	 * @throws 	IllegalAccessException 
	 * 
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void combineResult(List<T> resultList, T rowData, int index) throws IllegalAccessException {
		for(JoinFieldMetaData<?> jfm : joinFieldMetas){
			jfm.getField().setAccessible(true);
			List list = (List) jfm.getField().get(resultList.get(index));
			if(null == list){
				jfm.getField().set(resultList.get(index), jfm.getField().get(rowData));
			}else{
				List listNew = (List) jfm.getField().get(rowData);
				if(null == listNew){
					continue;
				}
				for(Object o : listNew){
					boolean exist = false;
					for(Object e : list){
						if(getPrimaryKeyValue(o).equals(getPrimaryKeyValue(e))){
							exist = true;
							break;
						}
					}
					if(!exist){
						list.add(o);
					}
				}
			}
		}
	}
	
	private Object getPrimaryKeyValue(Object readRowData) throws IllegalAccessException {
		Field primaryKey = MetaData.getPrimaryKeyField(readRowData.getClass());
		primaryKey.setAccessible(true);
		return primaryKey.get(readRowData);
	}
	
	/**
	 * 
	 * 读取当前行的数据
	 * @param  rs
	 * @throws SQLException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 * 
	 */
	@SuppressWarnings("unchecked")
	private T readRowData(ResultSet rs) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, SQLException{
		//缓存表名和实体对象
		Map<String, Object> fetchEntity = new HashMap<>();
		//缓存joinFetch的实体
		Map<String, Object> joinFetcEntity = new HashMap<>();
		readEntity(rs, fetchEntity, joinFetcEntity);
		T target = (T) fetchEntity.get(metaData.getTableName());
		for(Object entity : fetchEntity.values()){
			if(entity == target){
				continue;
			}
			injectFetchDependency(fetchEntity, entity);
		}
		for(Object entity : joinFetcEntity.values()){
			if(entity == target){
				continue;
			}
			injectJoinDependency(target, entity);
		}
		return target;
	}
	
	private void injectJoinDependency(T target, Object entity) throws IllegalAccessException {
		for(JoinFieldMetaData<?> jfd : joinFieldMetas){
			if(!entity.getClass().equals(jfd.getJoinClass())){
				continue;
			}
			jfd.getField().setAccessible(true);
			ArrayList<Object> list = new ArrayList<>();
			list.add(entity);
			jfd.getField().set(target, list);
		}
	}

	/**
	 * 注入fetch对象
	 * @param fetchEntity
	 * @param entity
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 */
	private void injectFetchDependency(Map<String, Object> fetchEntity, Object entity) throws IllegalAccessException {
		if(null == clzesEnabled2Join){
			return;
		}
		List<FilterDescriptor> deps = clzesEnabled2Join.get(entity.getClass());
		for(FilterDescriptor fd : deps){
			Object depObj = fetchEntity.get(MetaData.getTablenameByClass(fd.getJoinDependency()));
			if(null == depObj){
				continue;
			}
			//从缓存map中找出entity的依赖对象
			fd.getJoinField().setAccessible(true);
			//依赖对象中该类型的值
			Object value = fd.getJoinField().get(depObj);
			if(null == value){
				continue;
			}
			Field pk = MetaData.getPrimaryKeyField(entity.getClass());
			pk.setAccessible(true);
			if(pk.get(value).equals(pk.get(entity))){
				fd.getJoinField().set(depObj, entity);
			}
		}
	}
	
	private void readEntity(ResultSet rs, Map<String, Object> fetchEntity, Map<String, Object> joinFetcEntity) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, SQLException {
		for(int i = 1; i <= rs.getMetaData().getColumnCount(); i++){
			Object colValue = rs.getObject(i);
			if(null == colValue){
				continue;
			}
			String colName = rs.getMetaData().getColumnLabel(i);
			if(colName.split("\\" + SEPARATOR).length == 2){
				readFetchEntity(fetchEntity, colValue, colName);
			}else if(colName.split("\\" + SEPARATOR).length == 3){
				readMany2ManyEntity(joinFetcEntity, colValue, colName);
			}
		}
	}

	private void readMany2ManyEntity(Map<String, Object> joinFetcEntity, Object colValue, String colName) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException{
		String tableAlias = null;
		String tableName = null;
		String fieldNameAlias = null;
		String fieldName = null;
		//joinFetch出来的数据
		tableAlias = colName.split("\\" + SEPARATOR)[1];
		tableName = getTableNameByAlias(tableAlias);
		fieldNameAlias = colName.split("\\" + SEPARATOR)[2];
		fieldName = MetaData.getFieldsAliasMapping(MetaData.getClassByTableName(tableName)).get(fieldNameAlias);
		if(null == joinFetcEntity.get(tableName)){
			Object entity = MetaData.getClassByTableName(tableName).getConstructor().newInstance();
			joinFetcEntity.put(tableName, entity);
		}
		try {
			Field field = getFieldByName(tableName, fieldName);
			Entity entiyAnno = field.getType().getAnnotation(Entity.class);
			if(null != entiyAnno){
				Object newInstance = field.getType().getConstructor().newInstance();
				Field pkField = MetaData.getPrimaryKeyField(field.getType());
				pkField.setAccessible(true);
				setValue2Field(colValue, newInstance, pkField);
				field.setAccessible(true);
				field.set(joinFetcEntity.get(tableName), newInstance);
			}else{
				field.setAccessible(true);
				setValue2Field(colValue, joinFetcEntity.get(tableName), field);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	/**
	 * 
	 * 通过别名获取表名
	 * @param alias
	 * @return
	 * 
	 */
	protected String getTableNameByAlias(String alias) {
		Iterator<String> it = aliasMapping.keySet().iterator();
		while(it.hasNext()){
			String tableName = it.next();
			if(alias.equals(aliasMapping.get(tableName))){
				return tableName;
			}
		}
		return "";
	}
	
	private void readFetchEntity(Map<String, Object> fetchEntityMap, Object colValue, String colName) throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException{
		String tableAlias = null; 
		String tableName = null;
		String fieldNameAlias = null;
		String fieldName = null;
		tableAlias = colName.split("\\" + SEPARATOR)[0];
		tableName = getTableNameByAlias(tableAlias);
		fieldNameAlias = colName.split("\\" + SEPARATOR)[1];
		fieldName = MetaData.getFieldsAliasMapping(MetaData.getClassByTableName(tableName)).get(fieldNameAlias);
		if(null == fetchEntityMap.get(tableName)){
			Object entity = MetaData.getClassByTableName(tableName).getConstructor().newInstance();
			fetchEntityMap.put(tableName, entity);
		}
		Field field;
		try {
			field = getFieldByName(tableName, fieldName);
		} catch (Exception e) {
			throw new RabbitDMLException(e);
		}
		Entity entiyAnno = field.getType().getAnnotation(Entity.class);
		if(null != entiyAnno){
			Object newInstance = field.getType().getConstructor().newInstance();
			Field pkField = MetaData.getPrimaryKeyField(field.getType());
			pkField.setAccessible(true);
			setValue2Field(colValue, newInstance, pkField);
			field.setAccessible(true);
			field.set(fetchEntityMap.get(tableName), newInstance);
		}else{
			field.setAccessible(true);
			setValue2Field(colValue, fetchEntityMap.get(tableName), field);
		}
	}
	
	protected Field getFieldByName(String tableName, String fieldName){
		Class<?> clz = MetaData.getClassByTableName(tableName);
		List<FieldMetaData> fmds = MetaData.getCachedFieldsMetas(clz);
		for(FieldMetaData fmd : fmds){
			if(fmd.getField().getName().equals(fieldName)){
				return fmd.getField();
			}
		}
		throw new RabbitDMLException("new field [" + fieldName + "] was found in " + tableName);
	}
	
	private void setValue2Field(Object value, Object instance,
			Field field) {
		try{
		    if(value instanceof Number){
		        field.set(instance, RabbitValueConverter.cast(new BigDecimal(value.toString()), 
		                field.getType()));
		    }else{
                field.set(instance, value);
            }
		}catch(Exception e){
			throw new RabbitDMLException(e.getMessage(), e);
		}
		
	}
	
	/**
	 * 
	 * <b>Description:	生成sql语句</b><br>	
	 * 
	 */
	protected void createQuerySql(){
		runCallBackTask();
		//分析并准备查询条件
		prepareFilterMetas();
		combineFilters();
		convertJoinFilter2Metas();
		prepareMany2oneFilters();
		doOrderCheck();
		//创建被查询的表字段sql片段
		createFieldsSql();
		transformFieldsSql();
		createFromSql();
		createJoinSql();
		createFilterSql();
		createOrderSql();
		createPageSql();
	}

    /**
     * 
     * <b>Description:  检查order条件的合法性</b><br>.	
     * 
     */
    private void doOrderCheck() {
        for(Class<?> clz : asc.keySet()){
		    if(!isValidOrderOperation(clz)){
		        throw new OrderAssociationException(clz);
		    }
		}
		for(Class<?> clz : desc.keySet()){
		    if(!isValidOrderOperation(clz)){
		        throw new OrderAssociationException(clz);
		    }
		}
    }

    private boolean isValidOrderOperation(Class<?> clz) {
        if(clz.equals(getMetaData().getEntityClz())){
            return true;
        }
        if(entity2Fetch.contains(clz)){
            return true;
        }
        for(JoinFieldMetaData<?> jfm : joinFieldMetas){
            if(clz.equals(jfm.getJoinClass())){
                return true;
            }
        }
        for(JoinFilter jf : joinFilters.values()){
            if(jf.getAssocicatedClass().contains(clz)){
                return true;
            }
        }
        return false;
    }

	/**
	 * 
	 * <b>Description:	构建分页sql片段</b><br>	
	 * 
	 */
	protected void createPageSql() {
		if(!page){
			return;
		}
		DialectTransformer transformer = DialectTransformer.getTransformer(sessionFactory.getDialectType());
		sql = transformer.createPageSql(this);
		
	}
	/**
	 * 
	 * <b>Description:	创建排序sql</b><br>	
	 * 
	 */
	protected void createOrderSql() {
		DialectTransformer transformer = DialectTransformer.getTransformer(sessionFactory.getDialectType());
		sql.append(transformer.createOrderSql(this));
	}
	
	/**
	 * 
	 * <b>Description:	生成过滤条件sql</b><br>	
	 * 
	 */
	private void createFilterSql(){
		sql.append(generateFilterSql());
	}
		
	/**
	 * 
	 * <b>Description:	创建inner join 和 left join的sql片段</b><br>	
	 * 
	 */
	protected void createJoinSql(){
		
		//创建过滤条件内连接的sql
		createInnerJoinsql();
		
		//创建addInnerJoinFilter添加的内连接sql部分
		createDynamicInnerJoinSql();
		
		//创建many2one的sql，  fetch部分
		createLeftJoinSql();
		
		//创建一对多或者多对多部分的sql, joinFetch部分
		createJoinFetchSql();
		
		
	}
	
	private void createInnerJoinsql(){
		sql.append(generateInnerJoinsql());
	}
	
	private void createJoinFetchSql(){
		sql.append(" ");
		for(JoinFieldMetaData<?> jfm : joinFieldMetas){
		    if(joinFilters.containsKey(jfm.getJoinClass())){
		        //如果包含了动态innerJoinFilter 就忽略左连接
		        continue;
		    }
	        if(jfm.getAnnotation() instanceof OneToMany){
	            sql.append(createOTMJoinSql(jfm));
	        }else if(jfm.getAnnotation() instanceof ManyToMany){
	            sql.append(createMTMJoinSql(jfm));
	        }
	        //动态添加的join条件
	        sql.append(addDynFilterSql(jfm));
		}
	}
	
	private StringBuilder addDynFilterSql(JoinFieldMetaData<?> jfm) {
		return addDynFilterSql(jfm, addedJoinFilters);
	}
	
	/**
	 * 
	 * <b>Description:	生成一对多的过滤sql</b><br>
	 * @param jfm
	 * @return
	 * 
	 */
	private StringBuilder createOTMJoinSql(JoinFieldMetaData<?> jfm) {
		return createOTMJoinSql(jfm, true);
	}

	/**
	 * 
	 * <b>Description:	生成多对多的过滤sql</b><br>
	 * @param jfm
	 * @return
	 * 
	 */
	private StringBuilder createMTMJoinSql(JoinFieldMetaData<?> jfm) {
		return createMTMJoinSql(jfm, true);
	}
	
	private void createLeftJoinSql() {
		for(FilterDescriptor fd : many2oneFilterDescripters){
			boolean innered = false;
			for(FilterDescriptor fdi : filterDescriptors){
				if(fd.getFilterTable().equals(fdi.getFilterTable())){
					innered = true;
					break;
				}
			}
			if(!innered){
				sql.append(" LEFT JOIN " + fd.getFilterTable() + " " + getAliasByTableName(fd.getFilterTable()));
				sql.append(" ON " + fd.getKey() + fd.getFilter() + fd.getValue());
			}
		}
	}
	
	/**
	 * 创建Many2Many/One2Many的innerJoin sql
	 * @throws Exception
	 * @throws SQLException
	 */
	private void createDynamicInnerJoinSql(){
	    for(JoinFilter jf : joinFilters.values()){
	        sql.append(jf.getInnerJoinSQL());
	    }
	}
	
	/**
	 * 
	 * <b>Description:	添加动态添加的过滤条件</b><br>
	 * @param jfm
	 * @param addedJoinFilters	
	 * 
	 */
	protected StringBuilder addDynFilterSql(JoinFieldMetaData<?> jfm, Map<Class<?>, Map<String, List<DynamicFilterDescriptor>>> addedJoinFilters) {
		StringBuilder sql = new StringBuilder();
	    Class<?> jc = jfm.getJoinClass();
		if(!addedJoinFilters.containsKey(jc)){
			return sql;
		}
		Map<String, List<DynamicFilterDescriptor>> map = addedJoinFilters.get(jc);
		Iterator<String> fields = map.keySet().iterator();
		while(fields.hasNext()){
			String field = fields.next();
			List<DynamicFilterDescriptor> list = map.get(field);
			for(DynamicFilterDescriptor dfd : list){
				FieldMetaData fmd = MetaData.getCachedFieldsMeta(jc, field);
				sql.append("AND ");
				String key = getAliasByTableName(jfm.getTableName()) + "." + fmd.getColumn().value();
				String filter = dfd.getFilter().value();
				if(dfd.isReg()){
					key = dfd.getKeyReg().replaceAll(REPLACE_WORD, key);
				}
				Object holderValue = RabbitValueConverter.convert(dfd.getValue(), fmd);
				if(FilterType.IS.value().equals(filter.trim()) || FilterType.IS_NOT.value().equals(filter.trim())){
					sql.append(key + " " + filter + " NULL ");
				}else{
					cachePreparedValues(holderValue);
					sql.append(key + " " + filter + " " + createPlaceHolder(filter, holderValue) + " ");
				}
			}
		}
		return sql;
	}
	
	/**
	 * 
	 * 生成多对多的过滤sql
	 * @param jfm
	 * @param leftJoin  是左连接?
	 * @return
	 * 
	 */
	protected StringBuilder createMTMJoinSql(JoinFieldMetaData<?> jfm, boolean leftJoin){
		StringBuilder sb = new StringBuilder();
		ManyToMany mtm = (ManyToMany) jfm.getAnnotation();
		sb.append((leftJoin ? LEFT_JOIN : INNER_JOIN) + mtm.joinTable() + " " + getAliasByTableName(mtm.joinTable()) + " ON ");
		sb.append(getAliasByTableName(metaData.getTableName()) + "." + metaData.getPrimaryKey() + " = ");
		sb.append(getAliasByTableName(mtm.joinTable()) + "." + mtm.joinColumn() + " ");
		sb.append((leftJoin ? LEFT_JOIN : INNER_JOIN) + jfm.getTableName() + " " + getAliasByTableName(jfm.getTableName()) + " ON ");
		sb.append(getAliasByTableName(mtm.joinTable()) + "." + mtm.reverseJoinColumn() + " = ");
		sb.append(getAliasByTableName(jfm.getTableName()) + "." + jfm.getPrimaryKey());
		List<FieldMetaData> filterMetas = getNonEmptyFieldMetas(jfm.getFilter(), jfm.getJoinClass());
		for(FieldMetaData fmd : filterMetas){
			if(fmd.isForeignKey()){
				if(fmd.getFieldValue().getClass().equals(metaData.getEntityClz())){
					continue;
				}
				Field pk = MetaData.getPrimaryKeyField(fmd.getFieldValue().getClass());
				pk.setAccessible(true);
				Object pkv = getValue(pk, fmd.getFieldValue());
				if(null == pkv){
					continue;
				}
				String key = getAliasByTableName(jfm.getTableName()) + "." + fmd.getColumn().value();
				String filter = FilterType.EQUAL.name();
				sb.append(AND + key);
				Object hv = RabbitValueConverter.convert(pkv, fmd);
				cachePreparedValues(hv);
				sb.append(" " + filter + " " + createPlaceHolder(filter, hv));
			}else{
				String key = getAliasByTableName(jfm.getTableName()) + "." + fmd.getColumn().value();
				String filter = FilterType.EQUAL.name();
				sb.append(AND + key);
				Object hv = RabbitValueConverter.convert(fmd.getFieldValue(), fmd);
				cachePreparedValues(hv);
				sb.append(" " + filter + " " + createPlaceHolder(filter, hv));
			}
		}
		sb.append(" ");
		return sb;
	}
	
	/**
	 * 
	 * <b>Description:	生成一对多的过滤sql</b><br>
	 * @param jfm
	 * @param leftJoin
	 * @return	
	 * 
	 */
	protected StringBuilder createOTMJoinSql(JoinFieldMetaData<?> jfm, boolean leftJoin){
		StringBuilder sb = new StringBuilder();
		OneToMany otm = (OneToMany) jfm.getAnnotation();
		String lj = leftJoin ? " LEFT JOIN " : INNER_JOIN;
		sb.append(lj + jfm.getTableName() + " " + getAliasByTableName(jfm.getTableName()) + " ON ");
		sb.append(getAliasByTableName(metaData.getTableName()) + "." + metaData.getPrimaryKey() + " = ");
		sb.append(getAliasByTableName(jfm.getTableName()) + "." + otm.joinColumn());
		List<FieldMetaData> filterMetas = getNonEmptyFieldMetas(jfm.getFilter(), jfm.getJoinClass());
		for(FieldMetaData fmd : filterMetas){
			if(fmd.isForeignKey()){
				if(fmd.getFieldValue().getClass().equals(metaData.getEntityClz())){
					continue;
				}
				Field pk = MetaData.getPrimaryKeyField(fmd.getFieldValue().getClass());
				pk.setAccessible(true);
				Object pkv = getValue(pk, fmd.getFieldValue());
				if(null == pkv){
					continue;
				}
				String key = getAliasByTableName(jfm.getTableName()) + "." + fmd.getColumn().value();
				String filter = FilterType.EQUAL.name();
				sb.append(AND + key);
				Object hv = RabbitValueConverter.convert(pkv, fmd);
				cachePreparedValues(hv);
				sb.append(" " + filter + " " + createPlaceHolder(filter, hv));
			}else{
				String key = getAliasByTableName(jfm.getTableName()) + "." + fmd.getColumn().value();
				String filter = FilterType.EQUAL.name();
				sb.append(AND + key);
				Object hv = RabbitValueConverter.convert(fmd.getFieldValue(), fmd);
				cachePreparedValues(hv);
				sb.append(" " + filter + " " + createPlaceHolder(filter, hv));
			}
		}
		sb.append(" ");
		return sb;
	}

	/**
	 * 
	 * 创建from语句
	 * @throws SQLException 
	 * 
	 */
	protected void createFromSql(){
		sql.append(" FROM " + metaData.getTableName() + " " + getAliasByTableName(metaData.getTableName()));
	}
	/**
	 * 
	 * <b>Description:	根据db的不同，对字段片段的sql部分进行包装</b><br>	
	 * 
	 */
	protected void transformFieldsSql() {
		DialectTransformer transformer = DialectTransformer.getTransformer(sessionFactory.getDialectType());
		sql = transformer.completeFieldsSql(this);
	}
	
	/**
	 * 
	 * <b>Description:	将动态添加的过滤条件转换成meta信息</b><br>	
	 * 
	 */
	private void convertJoinFilter2Metas(){
		Iterator<Class<?>> it = addedJoinFilters.keySet().iterator();
		while(it.hasNext()){
			Class<?> enc = it.next();
			for(JoinFieldMetaData<?> jfm : metaData.getJoinMetas()){
				if(!enc.equals(jfm.getJoinClass())){
					continue;
				}
				if(!isExistsJoinFieldMeta(enc)){
					joinFieldMetas.add(jfm);
				}
				break;
			}
		}
	}

    private boolean isExistsJoinFieldMeta(Class<?> enc) {
        for(JoinFieldMetaData<?> jfme : joinFieldMetas){
        	if(jfme.getJoinClass().equals(enc)){
        		return true;
        	}
        }
        return false;
    }
	
	public AbstractQuery<T> distinct(){
		this.distinct = true;
		return this;
	}
	
	/**
	 * 
	 * <b>Description:	创建被查询的表字段sql片段</b><br>	
	 * 
	 */
	protected void createFieldsSql(){
		if(distinct){
			sql.append(" DISTINCT ");
		}
		//主表字段
		sql.append(createFieldsSqlByMetas(metaData.getEntityClz()));
		
		//many2one关联表字段
		for(Class<?> clz : entity2Fetch){
			if(clz.equals(metaData.getEntityClz())){
				continue;
			}
			sql.append(createFieldsSqlByMetas(clz));
		}
		//多对多、一对多关联表字段
		for(JoinFieldMetaData<?> jfm : joinFieldMetas){
			sql.append(createFieldsSqlByJoinMetas(jfm));
		}
		sql.deleteCharAt(sql.lastIndexOf(","));
	}
	
	/**
	 * 
	 * <b>Description:	根据JoinFieldMetaData创建字段sql片段</b><br>
	 * @param jfm
	 * @return	
	 * 
	 */
	private StringBuilder createFieldsSqlByJoinMetas(JoinFieldMetaData<?> jfm) {
		List<FieldMetaData> fieldsMetas = MetaData.getCachedFieldsMetas(jfm.getJoinClass());
		StringBuilder sb = new StringBuilder();
		Map<String, String> aliasMappings = MetaData.getFieldsAliasMapping(jfm.getJoinClass());
		if(null == aliasMappings){
			aliasMappings = new ConcurrentHashMap<>();
			MetaData.setFieldsAliasMapping(jfm.getJoinClass(), aliasMappings);
		}
		for(int i = 0; i < fieldsMetas.size(); i++){
			FieldMetaData fmd = fieldsMetas.get(i);
			String fn = fmd.getField().getName();
			String alias = Integer.toString(i);
			aliasMappings.put(alias, fn);
			sql.append(getAliasByTableName(jfm.getTableName()) + "." + fmd.getColumn().value());
			sql.append(" AS ");
			sql.append("J" + SEPARATOR + getAliasByTableName(jfm.getTableName()) + SEPARATOR + alias);
			sql.append(", ");
		}
		return sb;
	}
	
	/**
	 * 
	 * <b>Description:	通过meta字段信息拼接需要被查询的字段的sql</b><br>
	 * @param clz
	 * @return	
	 * 
	 */
	private StringBuilder createFieldsSqlByMetas(Class<?> clz){
		StringBuilder sb = new StringBuilder();
		List<FieldMetaData> fieldsMetas = MetaData.getCachedFieldsMetas(clz);
		Map<String, String> aliasMappings = MetaData.getFieldsAliasMapping(clz);
		if(null == aliasMappings){
			aliasMappings = new ConcurrentHashMap<>();
			MetaData.setFieldsAliasMapping(clz, aliasMappings);
		}
		for(int i = 0; i < fieldsMetas.size(); i++){
			FieldMetaData fmd = fieldsMetas.get(i);
			String fn = fmd.getField().getName();
			String alias = Integer.toString(i);
			aliasMappings.put(alias, fn);
			sql.append(getAliasByTableName(MetaData.getTablenameByClass(clz)) + "." + fmd.getColumn().value());
			sql.append(" AS ");
			sql.append(getAliasByTableName(MetaData.getTablenameByClass(clz)) + SEPARATOR + alias);
			sql.append(", ");
		}
		return sb;
	}
	
	/**
	 * 
	 * <b>Description:	将fetch的filterdescriptor信息准备好</b><br>	
	 * 
	 */
	protected void prepareMany2oneFilters(){
		removeInvalidFetch();
		Iterator<Class<?>> it = fetchClzes.keySet().iterator();
		while(it.hasNext()){
			Class<?> key = it.next();
			FilterDescriptor fd = getFilterDescriptorsByClzAndDep(key, fetchClzes.get(key));
			addFirst(fd);
			entity2Fetch.add(key);
			Class<?> dep = fd.getJoinDependency();
			while(!metaData.getEntityClz().equals(dep)){
				if(fetchClzes.containsKey(dep)){
					fd = getFilterDescriptorsByClzAndDep(fd.getJoinDependency(), fetchClzes.get(dep));
					addFirst(fd);
					entity2Fetch.add(dep);
				}else{
					List<FilterDescriptor> deps = getClzesEnabled2Join().get(dep);
					if(deps.size() > 1){
						throw new RabbitDMLException("ambiguous dependency for class [" + fd.getJoinDependency().getName() + "]");
					}else{
						fd = deps.get(0);
						addFirst(fd);
						entity2Fetch.add(dep);
					}
				}
				dep = fd.getJoinDependency();
			}
		}
	}
	
	/**
	 * 
	 * <b>Description:	通过clz和其依赖找出过滤描述符</b><br>
	 * @param clz
	 * @param dep
	 * @return	
	 * 
	 */
	private FilterDescriptor getFilterDescriptorsByClzAndDep(Class<?> clz, Class<?> dep){
		Map<Class<?>, List<FilterDescriptor>> clzesEnabled2Join = getClzesEnabled2Join();
		if(!clzesEnabled2Join.containsKey(clz)){
			throw new InvalidFetchOperationException("class[" + clz.getName() + "] can't be fetched from class[" 
					+ metaData.getEntityClz().getName() + "]");
		}
		for(FilterDescriptor fd : clzesEnabled2Join.get(clz)){
			if(fd.getJoinDependency().equals(dep)){
				return fd;
			}
		}
		throw new RabbitDMLException("no FilterDescriptors is found for[" + clz + ", " + dep + "]");
	}
	
	private void addFirst(FilterDescriptor fd) {
		for(FilterDescriptor f : many2oneFilterDescripters){
			if(f.getFilterTable().equals(fd.getFilterTable())){
				many2oneFilterDescripters.remove(f);
				break;
			}
		}
		many2oneFilterDescripters.add(0,fd);
	}
	
	/**
	 * 
	 * <b>Description:	清除掉错误的fetch逻辑</b><br>	
	 * 
	 */
	private void removeInvalidFetch() {
		Iterator<Class<?>> it = fetchClzes.keySet().iterator();
		while(it.hasNext()){
			Class<?> key = it.next();
			if(!getClzesEnabled2Join().containsKey(key)){
				continue;
			}
			boolean enableFetch = false;
			for(FilterDescriptor fd : getClzesEnabled2Join().get(key)){
				if(fd.getJoinDependency().equals(fetchClzes.get(key))){
					enableFetch = true;
				}
			}
			if(enableFetch){
				continue;
			}
			fetchClzes.remove(key);
		}
	}
	/**
	 * 
	 * 通过表名获取别名
	 * @param tableName
	 * @return
	 * 
	 */
	public String getAliasByTableName(String tableName){
		if(isEmpty(aliasMapping.get(tableName))){
			Collection<String> alias = aliasMapping.values();
			for(int i = 0; ; i++){
				String suffix = "";
				if(0 != i){
					suffix = Integer.toString(i);
				}
				for(char c = 'A'; c <= 'Z'; c++){
					if(alias.contains((c + suffix).toUpperCase())){
						continue;
					}
					aliasMapping.put(tableName, c + suffix);
					return aliasMapping.get(tableName);
				}
			}
		}
		return aliasMapping.get(tableName);
	}
	
	/**
	 * 
	 * <b>Description:	动态新增内连接过滤条件</b><br>
	 * @param reg
	 * @param value
	 * @param ft
	 * @param depsPath	依赖路径
	 * @return	
	 * 
	 */
	public abstract AbstractQuery<T> addFilter(String reg, Object value, FilterType ft, Class<?>... depsPath);

	/**
	 * 
	 * <b>Description:	动态新增内连接过滤条件</b><br>
	 * @param reg
	 * @param value
	 * @param depsPath
	 * @return	
	 * 
	 */
	public abstract AbstractQuery<T> addFilter(String reg, Object value, Class<?>... depsPath);
	
	/**
	 * 
	 * <b>Description:	新增空查询条件</b><br>
	 * @param reg
	 * @param isNull
	 * @param depsPath
	 * @return	
	 * 
	 */
	public abstract AbstractQuery<T> addNullFilter(String reg, boolean isNull, Class<?>... depsPath);

	/**
	 * 
	 * <b>Description:	新增空查询条件</b><br>
	 * @param reg
	 * @param depsPath
	 * @return	
	 * 
	 */
	public abstract AbstractQuery<T> addNullFilter(String reg, Class<?>... depsPath);
	
	/**
	 * 
	 * <b>Description:	新增多对多/一对多过滤条件</b><br>
	 * @param reg
	 * @param ft
	 * @param value
	 * @param target
	 * @return	
	 * 
	 */
	public abstract AbstractQuery<T> addJoinFilter(String reg, FilterType ft, Object value, Class<?> target);
	
	/**
	 * 
	 * <b>Description:	新增多对多/一对多过滤条件</b><br>
	 * @param reg
	 * @param value
	 * @param target
	 * @return	
	 * 
	 */
	public abstract AbstractQuery<T> addJoinFilter(String reg, Object value, Class<?> target);
	
	/**
     * 
     * <b>Description:  新增一对多/多对多 内链接查询</b><br>.
     *                  该方法会覆盖addJoinFilter、joinFetch函数中同表的过滤条件
     * @param filter
     * @return  
     * 
     */
    public AbstractQuery<T> addInnerJoinFilter(JoinFilter filter){
        joinFilters.put(filter.getJoinClass(), filter);
        return this;
    }
	
	/**
	 * 
	 * <b>Description:	添加内链接过滤条件，相同target的内链接过滤条件和合并</b><br>
	 * @param reg
	 * @param ft
	 * @param value
	 * @param target
	 * @return	
	 * 
	 */
	public abstract AbstractQuery<T> addInnerJoinFilter(String reg, FilterType ft, Object value, Class<?> target);
	
	/**
	 * 
	 * <b>Description:	addJoinFilter添加的是left join。该方法添加的是inner join过滤条件</b><br>
	 * @param reg
	 * @param value
	 * @param target
	 * @return	
	 * 
	 */
	public abstract AbstractQuery<T> addInnerJoinFilter(String reg, Object value, Class<?> target);
	
	/**
	 * 
	 * <b>Description:	设置需要升序的字段, 多次调用只有最后一次生效</b><br>
	 * @param fieldName
	 * @return	
	 * 
	 */
	public AbstractQuery<T> asc(String fieldName){
		return asc(fieldName, metaData.getEntityClz());
	}
	
	/**
	 * 
	 * <b>Description:	设置需要升序的字段</b><br>
	 * @param fieldName	升序的字段名
	 * @param targetClz	升序字段对应的实体
	 * @return	
	 * 
	 */
	public AbstractQuery<T> asc(String fieldName, Class<?> targetClz){
		if(null ==  asc.get(targetClz)){
			asc.put(targetClz, new HashSet<>());
		}
		String columnName = getColumnNameByFieldAndClz(fieldName, targetClz);
		asc.get(targetClz).add(columnName);
		return this;
	}
	
	/**
	 * 
	 * <b>Description:	根据字段名和clz信息从缓存中获取对应的表字段名</b><br>
	 * @param fieldName
	 * @param targetClz
	 * @return	
	 * 
	 */
	private String getColumnNameByFieldAndClz(String fieldName, Class<?> targetClz){
		List<FieldMetaData> fms = MetaData.getCachedFieldsMetas(targetClz);
		for(FieldMetaData fmd : fms){
			if(fmd.getField().getName().equals(fieldName)){
				return fmd.getColumn().value();
			}
		}
		throw new RabbitDMLException("field [" + fieldName + "] is no declared in " + targetClz.getName());
	}
	
	/**
	 * 
	 * <b>Description:	设置需要降序排列的字段, 多次调用只有最后一次生效</b><br>
	 * @param fieldName
	 * @return	
	 * 
	 */
	public AbstractQuery<T> desc(String fieldName){
		return desc(fieldName, metaData.getEntityClz());
	}
	
	/**
	 * 
	 * <b>Description:	设置需要降序排列的字段, 多次调用只有最后一次生效</b><br>
	 * @param fieldName
	 * @param targetClz
	 * @return	
	 * 
	 */
	public AbstractQuery<T> desc(String fieldName, Class<?> targetClz){
		if(null ==  desc.get(targetClz)){
			desc.put(targetClz, new HashSet<>());
		}
		String columnName = getColumnNameByFieldAndClz(fieldName, targetClz);
		desc.get(targetClz).add(columnName);
		return this;
	}
	
	/**
	 * 
	 * <b>Description:	统计数据条数</b><br>
	 * 
	 */
	public long count() {
		createCountSql();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try{
			conn = sessionFactory.getConnection();
			showSql();
			stmt = conn.prepareStatement(sql.toString());
			setPreparedStatementValue(stmt);
			rs = stmt.executeQuery();
			if(rs.next()){
				return Long.parseLong(rs.getObject(1).toString());
			}
			return 0L;
		} catch (Exception e) {
		    throw new RabbitDMLException(e);
		} finally {
		    closeResultSet(rs);
			closeConnection(conn);
			closeStmt(stmt);
		}
	}

    private void closeResultSet(ResultSet rs){
        if(null != rs){
            try {
                rs.close();
            } catch (SQLException e) {
                throw new RabbitDMLException(e);
            }
        }
    }
	
	protected void createCountSql() {
		runCallBackTask();
		prepareFilterMetas();
		combineFilters();
		prepareMany2oneFilters();
		generateCountSql();
		createFromSql();
		createJoinSql();
		createFilterSql();
	}
	
	protected void generateCountSql() {
		sql.append("SELECT COUNT(1) ");
	}
	
	/**
	 * 
	 * <b>Description:	关联查询</b><br>
	 * @param clz
	 * @param dependency
	 * @return	
	 * 
	 */
	public AbstractQuery<T> fetch(Class<?> clz, Class<?>... dependency){
		if(0 == dependency.length){
			fetchEntity(clz);
		}else{
			fetchEntity(clz, dependency[0]);
			for(int i = 0; i < dependency.length; i++){
				if(i + 1 == dependency.length){
					break;
				}
				fetchEntity(dependency[i], dependency[i + 1]);
			}
		}
		return this;
	}
	
	/**
	 * 
	 * <b>Description:	关联查询</b><br>
	 * @param clz
	 * @param dependency
	 * @return	
	 * 
	 */
	private AbstractQuery<T> fetchEntity(Class<?> clz, Class<?> dependency){
		if(null == clz){
			return this;
		}
		//不能自己fetch自己
		if(metaData.getEntityClz().equals(clz)){
			throw new RabbitDMLException("invalid fetch operation for class[" 
			        + clz.getName() + "]");
		}
		fetchClzes.put(clz, dependency);
		return this;
	}
	
	private AbstractQuery<T> fetchEntity(Class<?> clz){
		if(null == clz){
			return this;
		}
		fetchClzes.put(clz, metaData.getEntityClz());
		return this;
	}
	
	/**
	 * 
	 * 查询出一对多或者多对多的数据。
	 * @param entity
	 * @return
	 * 
	 */
	public <E> AbstractQuery<T> joinFetch(Class<E> entity){
		return joinFetch(entity, null);
	}
	
	/**
	 * 
	 * <b>Description:	联合查询多端数据</b><br>
	 * @param entityClz
	 * @param filter	多端过滤条件
	 * @return	
	 * 
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <E> AbstractQuery<T> joinFetch(Class<E> entityClz, E filter){
		for(JoinFieldMetaData jfm : metaData.getJoinMetas()){
			if(!entityClz.equals(jfm.getJoinClass())){
				continue;
			}
			jfm.setFilter(filter);
			for(JoinFieldMetaData jfme : joinFieldMetas){
				if(jfme.getJoinClass().equals(entityClz)){
					joinFieldMetas.remove(jfme);
					break;
				}
			}
			joinFieldMetas.add(jfm);
			break;
		}
		return this;
	}
	
	/**
	 * 
	 * 设置查询时的别名, SQLQuery对象必须调用该方法来指定表的别名信息
	 * @param entityClz
	 * @param alias
	 * @return
	 * 
	 */
	public AbstractQuery<T> alias(Class<?> entityClz, String alias){
		aliasMapping.put(MetaData.getTablenameByClass(entityClz), alias.toUpperCase());
		return this;
	}

}