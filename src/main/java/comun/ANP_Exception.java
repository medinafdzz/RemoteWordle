package comun;

/**
 * Excepción para acción no permitida (ANP).
 */
public class ANP_Exception extends Exception {

	private static final long serialVersionUID = -5887103946435306026L;

	public ANP_Exception() {
		super();
	}
	
	public ANP_Exception(String str) {
		super(str);
	}
}
