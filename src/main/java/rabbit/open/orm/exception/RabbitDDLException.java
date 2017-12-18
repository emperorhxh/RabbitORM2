package rabbit.open.orm.exception;

@SuppressWarnings("serial")
public class RabbitDDLException extends RuntimeException{

	public RabbitDDLException(Throwable cause) {
		super(cause);
	}
	
	public RabbitDDLException(String msg) {
		super(msg);
	}
}
