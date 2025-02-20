package comun;

/**
 * Excepción si se intenta conectar alguien cuando ya ha comenzado el juego.
 */
public class AliasInvalido extends Exception {

	private static final long serialVersionUID = -5887101742135306026L;

	public AliasInvalido() {
		super();
	}
	
	public AliasInvalido(String str) {
		super(str);
	}
}
