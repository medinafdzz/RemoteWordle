package comun;

public class ErrorDiccionario extends Exception {

	/**
	 *  Excepcion si no hay palabra o hubo un error al generar el diccionario
	 */
	private static final long serialVersionUID = 7373605784302870986L;
	public ErrorDiccionario() {
		super();
	}
	public ErrorDiccionario(String msg) {
		super(msg);
	}
}
