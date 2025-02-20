package comun;

/**
 * Interfaz de las operaciones de servicio.
 */
public interface IWordle extends lib.DefaultService {
	
	/**
	 * Tamaño en caracteres de la palabra a adivinar.
	 */
	static final int TAM_PALABRA = 5;

	/**
	 * OPERACION NUEVA DEL MODO COMPETITIVO
	 * Permite a un jugador conectarse al modo competitivo
	 * @param alias El alias del jugador que se está conectando.
	 * @throws ANP_Exception si no está permitido llamar a esta operación.
	 * @throws AliasInvalido si el alias no es válido o está repetido.
	 * @throws JuegoComenzado si el juego competitivo ya ha comenzado.
	 * @return Cierto si es el primer cliente que se conecta.
	 *         Falso en otro caso.
	 * 
	 */
	public boolean conectarse(String alias) throws ANP_Exception, AliasInvalido, JuegoComenzado;

	/**
	 * OPERACION MODIFICADA PARA EL MODO COMPETITIVO
	 * Reinicia el juego para que vuelva a la situación de comienzo
	 * del mismo (momento inmediatamente posterior a la conexión)
	 * Si la llama el primer jugador debe esperar a que todos los
	 * demás jugadores hayan llamado a esta misma operación (estén listos)
	 * antes de retornar
	 * @throws ANP_Exception si no está permitido llamar a esta operación.
	 */
	public void reiniciarJuego() throws ANP_Exception;
	
	/**
	 * OPERACION MODIFICADA PARA EL MODO COMPETITIVO
	 * Crea una palabra a adivinar y crea el diccionario para la competicion.
	 * Recibe  un entero n con el numero de letras que debe tener la palabra (se crea diccionario con letras de ese tamaño)
	 * SOLO DEBE CREARLA EL PRIMER CLIENTE CONECTADO
	 * EL RESTO DE CLIENTES SOLO DEBEN ESPERAR A QUE SE CREE. 
	 * La operación no debe retornar hasta que la palabra esta creada.
	 * @throws ANP_Exception si no está permitido llamar a esta operación. 
	 * @throws LongitudErronea si no hay palabras de la longitud indicada o se pide un tamaño de palabra <=0.
	 * @throws ErrorDiccionario si no se pudo crear el diccionario.
	 */
	public void nuevaPalabra(int n)throws ANP_Exception, LongitudErronea,ErrorDiccionario;
	
	/**
	 * Retorna las diferencias entre una palabra y la palabra a adivinar.
	 * @param palabra La palabra propuesta para comparar.
	 * @throws ANP_Exception si no está permitido llamar a esta operación.
	 * @throws LongitudErronea si la longitud de la palabra propuesta no es correcta.
	 * @throws FormatoErroneo si la palabra contiene caracteres no válidos.
	 * @return Una cadena de texto que representa las diferencias, carácter a carácter, 
	 *         entre la palabra propuesta y la palabra a adivinar. Por cada carácter:
	 *         "="   Si el carácter coincide.
	 *         "C"   Si el carácter existe pero va en otra posición.
	 *         "X"   Si el carácter no existe.
	 */
	public String obtenerDiferencias(String palabra)
		throws ANP_Exception, LongitudErronea, FormatoErroneo;
	
	/**
	 * OPERACION MODIFICADA PARA EL MODO COMPETITIVO
	 * Retorna si el juego ha finalizado o no y por qué motivo.
	 * @param palabra La palabra propuesta para comparar.
	 * @throws ANP_Exception si no está permitido llamar a esta operación.
	 * @throws LongitudErronea si la longitud de la palabra propuesta no es correcta.
	 * @throws FormatoErroneo si la palabra contiene caracteres no válidos.
	 * @return Una entero indicando si se ha finalizo o no (y por qué motivo se finalizó):
	 * 		   3  El juego ha finalizado (OTRO JUGADOR ADIVINÓ LA PALABRA)
	 *         1  El juego ha finalizado (EL PROPIO JUGADOR ADIVINÓ LA PALABRA)
	 *         4  El juego ha finalizado (NO LE QUEDAN INTENTOS A NINGÚN JUGADOR)
	 *         2  El juego ha finalizado (NO LE QUEDAN INTENTOS AL PROPIO JUGADOR)
	 *         0  El juego no ha finalizado (quedan intentos y no se adivinó la palabra)
	 *         EN CASO DE DARSE MÁS DE UN MOTIVO SE DEBERÁ DAR PRIORIDAD AL MOTIVO INDICADO
	 *         EN PRIMER LUGAR (EN LA LISTA INDICADA ANTERIORMENTE).
	 */
	public int juegoFinalizado(String palabra)
		throws ANP_Exception, LongitudErronea, FormatoErroneo;

	/**
	 * Retorna la palabra a adivinar.
	 * @throws ANP_Exception si no está permitido llamar a esta operación.
	 * @return Un String con la palabra a adivinar.
	 */
	public String solicitarPalabra() throws ANP_Exception;
	
	/**
	 * OPERACION NUEVA DEL MODO COMPETITIVO
	 * Retorna el jugador que ha adivinado la palabra (que ha ganado el juego).
	 * @throws ANP_Exception si no está permitido llamar a esta operación.
	 * @return Un String con el alias del jugador que adivinó la palabra ó texto vacío si no lo hizo nadie
	 */
	public String obtenerGanador() throws ANP_Exception;
	
	public int LongitudPalabra() throws ANP_Exception;

}
