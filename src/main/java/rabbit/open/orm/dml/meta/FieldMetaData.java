package rabbit.open.orm.dml.meta;

import java.lang.reflect.Field;
import java.util.Date;

import rabbit.open.orm.annotation.Column;
import rabbit.open.orm.annotation.PrimaryKey;
import rabbit.open.orm.exception.RabbitDMLException;

/**
 * <b>Description: 	字段元信息对象</b><br>
 * <b>@author</b>	肖乾斌
 * 
 */
public class FieldMetaData {

	//字段在其所在的类中的Field对象
	private Field field;
	
	//字段属性的Column信息
	private Column column;
	
	//字段所在的表名
	private String fieldTableName;
		
	//字段的值
	private Object fieldValue;
	
	//是否是外键字段
	private boolean isForeignKey = false;
	
	//外键在其所在实体中的字段对象
	private Field foreignField;
	
	//是否是主键字段
	private boolean isPrimaryKey = false;

	private PrimaryKey primaryKey;
		

	public FieldMetaData(Field field, Column column) {
		super();
		this.field = field;
		this.column = column;
		if(baseDataType.contains(field.getType().getSimpleName())){
			throw new RabbitDMLException("data type[" + field.getType().getSimpleName() 
					+ "] is not supported by rabbit entity!");
		}
		setPrimaryKey(field.getAnnotation(PrimaryKey.class));
		//判断是否是外键类型
		if(!MetaData.isEntityClass(field.getType())){
			return;
		}
		this.isForeignKey = true;
		foreignField = MetaData.getPrimaryKeyField(field.getType());
	}
	
	public Field getField() {
		return field;
	}

	public Column getColumn() {
		return column;
	}
	
	public FieldMetaData(Field field, Column column, Object value, String tableName){
		this(field, column);
		this.fieldValue = value;
		this.fieldTableName = tableName;
	}
	
	//简单数据类型
	private String baseDataType = int.class.getName() + "|"
				 	   + float.class.getName() + "|"
				 	   + double.class.getName() + "|"
				 	   + short.class.getName() + "|"
				 	   + long.class.getName() + "|"
				 	   + char.class.getName() + "|"
				 	   + byte.class.getName() + "|"
				 	   + boolean.class.getName();

	//数字字段
	public boolean isNumerical(){
		return (Integer.class.getName() + "|" + Float.class.getName() + "|" + Double.class.getName() + "|"
		+ Short.class.getName() + "|" + Long.class.getName()).contains(field.getType().getName()) || 
		(int.class.getName() + "|" + float.class.getName() + "|" + double.class.getName() + "|"
				+ short.class.getName() + "|" + long.class.getName()).contains(field.getType().getName());
	}

	public boolean isForeignKey() {
		return isForeignKey;
	}

	public boolean isPrimaryKey() {
		return isPrimaryKey;
	}

	private void setPrimaryKey(PrimaryKey primaryKey) {
		this.isPrimaryKey = (null != primaryKey);
		this.primaryKey = primaryKey;
	}

	public PrimaryKey getPrimaryKey() {
		return primaryKey;
	}

	public String getFieldTableName() {
		return fieldTableName;
	}

	public Object getFieldValue() {
		return fieldValue;
	}

	public Field getForeignField() {
		return foreignField;
	}
	
	//字符类型字段varchar
	public boolean isString(){
		return field.getType().equals(String.class);
	}

	public boolean isBoolean(){
		return field.getType().equals(boolean.class) || field.getType().equals(Boolean.class);
	}

	//日期类型字段
	public boolean isDate(){
		return field.getType().equals(Date.class);
	}

}