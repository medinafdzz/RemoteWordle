package comun;

/**
 * Excepción para caracteres inválidos en la palabra. 
 */
public class FormatoErroneo extends Exception {

	private static final long serialVersionUID = -1281828700658572943L;
	
	public FormatoErroneo() {
		super();
	}

	public FormatoErroneo(String str) {
		super(str);
	}
}
