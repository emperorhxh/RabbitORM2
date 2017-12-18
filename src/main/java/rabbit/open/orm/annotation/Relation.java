package rabbit.open.orm.annotation;

public class Relation {

	//过滤条件
	public enum FilterType{
		
		EQUAL(" = "), NOT_EQUAL(" != "), GT(" > "), LT(" < "), GTE(" >= "), 
		LTE(" <= "), IN(" IN "), LIKE(" LIKE "), IS("IS"), IS_NOT("IS NOT");
		
		String value;
		
		FilterType(String value){
			this.value = value;
		}
		
		public String value(){
			return value;
		}
		
	}

}
