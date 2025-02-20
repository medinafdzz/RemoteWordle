package servidor;

import java.io.IOException;

import lib.ChannelException;
import lib.CommServer;
import optional.Trace;

/**
 * Ejemplo de programa servidor e hilo principal de éste.
 */
public class SrvWordle {
	

	/**
	 * Registra las operaciones de servicio (métodos públicos del OOS)
	 * como funciones anónimas.
	 * 
	 * <p>Registro de una operación de nombre {@code op}:</p>
	 * <ul><li>{@code TipoResultado operacion(T0 arg0, T1 arg1, ...)}</li>
	 * <li>{@code com.addListener("op", (o, x) -> ((TipoOOS)o).op((T0)x[0],
	 * (T1)x[1], ...))}</li></ul>
	 * 
	 * <p>Si la operación es unidireccional (cliente =&gt; servidor) o
	 * <em>oneway</em> debe indicarse poniendo un último argumento a 
	 * {@code true}.</p>
	 */
	private static void registrarOperaciones(CommServer com) {
		// Registro de las operaciones de servicio

		com.addFunction("conectarse",
			(o, x) -> ((WordleOOS) o).conectarse((String)x[0]));
		com.addFunction("obtenerGanador",
			(o, x) -> ((WordleOOS) o).obtenerGanador());

		com.addAction("reiniciarJuego",
				(o, x) -> ((WordleOOS) o).reiniciarJuego());
		com.addAction("nuevaPalabra",
				(o, x) -> ((WordleOOS) o).nuevaPalabra((int)x[0]));
		com.addFunction("obtenerDiferencias",
				(o, x) -> ((WordleOOS) o).obtenerDiferencias((String)x[0]));
		com.addFunction("juegoFinalizado",
				(o, x) -> ((WordleOOS) o).juegoFinalizado((String)x[0]));
		com.addFunction("solicitarPalabra",
				(o, x) -> ((WordleOOS) o).solicitarPalabra());
		com.addFunction("LongitudPalabra",
				(o, x) -> ((WordleOOS) o).LongitudPalabra());
		
	} 


	/**
	 * Crea un servidor con un servicio y lo pone en modo escucha.
	 * @param args no se utiliza (vacío)
	 */
	public static void main(String[] args) {		
		CommServer com=null;		// Canal de comunicaciones del servidor.
		int idCliente;		// Identificador del cliente
		
		/*
		// Compruebo si el diccionario de la clase servicio ha sido correctamente creado
		if (WordleOOS.palabrasAdivinar == null) {
			// ERROR GRAVE. SE DEBE FINALIZAR EL SERVIDOR
			System.err.printf("Error en el servidor, falló la lectura del diccionario de la clase servicio.\n");
			System.exit(-1);
		}
		*/
		// Crea el canal de comunicaciones
		try {
			com = new CommServer();
		} catch (IOException e) {
			// ERROR GRAVE. SE DEBE FINALIZAR EL SERVIDOR
			System.err.printf("Error en el servidor al crear el canal de comunicaciones: %s\n", e.getMessage());
			System.exit(-1);
		}
		
		// Activa trazas opcionales
		try {
			// Activa la traza en el servidor que por defecto
			// está desactivada. Esto es opcional.
			//Trace.activateTrace(com);  // opcional
			//Trace.printf("-- Creado el canal del servicio %d\n", com.IdComm());

			// Activa el registro de mensajes del servidor (opcional)
			com.activateMessageLog();
		} catch (ChannelException e) {
			// ERROR LEVE
			System.err.printf("Error en el servidor al activar la traza: %s\n", e.getMessage());
		}
			
		// Registrar operaciones del objeto de servicio (OOS)
		// y asignarles un identificador (para los mensajes)
		SrvWordle.registrarOperaciones(com);
								
		// Ofrecer el servicio (queda a la escucha)
		while (true) {
			try {
				// Espera a que se conecte un cliente
				idCliente = com.waitForClient();			
				
				// Conversación con el cliente en un hilo
				Trace.printf("-- Creando hilo para el cliente %d.\n", idCliente);
				new Thread(new HilosWordleOOS(com, idCliente)).start();
				Trace.printf("-- Creado hilo para el cliente %d.\n",
						idCliente);
			} catch (IOException | ChannelException e) {
				// ERROR MODERADO. NO SE PUDO CONTACTAR CON EL CLIENTE
				// NO SE SALE DEL SERVIDOR
				System.err.printf("Error en el servidor al conectarse un cliente: %s\n", e.getMessage());
				e.printStackTrace();
			}
		}
		
	} // main

} // class SrvWordle
