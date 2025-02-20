package comun;

/**
 * Excepción para longitud de la palabra errónea. 
 */
public class LongitudErronea extends Exception {

	private static final long serialVersionUID = -1281828111658572943L;
	
	public LongitudErronea() {
		super();
	}

	public LongitudErronea(String str) {
		super(str);
	}
}
