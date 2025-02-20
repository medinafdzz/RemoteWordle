package comun;

/**
 * Excepción para alias no válido.
 */
public class JuegoComenzado extends Exception {

	private static final long serialVersionUID = -7087101742135399926L;

	public JuegoComenzado() {
		super();
	}
	
	public JuegoComenzado(String str) {
		super(str);
	}
}
