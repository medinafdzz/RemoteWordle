package servidor;

import java.io.IOException;

import lib.ChannelException;
import lib.CommServer;
import lib.ProtocolMessages;
import optional.Trace;

/**
 * Clase para los hilos secundarios del servidor (uno por cliente).
 */
public class HilosWordleOOS implements Runnable {
	private CommServer com;			// canal del comunicación del servidor
	private int idCliente;			// ID del cliente	
	private WordleOOS oos;			// OOS del cliente

	public HilosWordleOOS(CommServer com, int idCliente) {
		// Almacenamos el canal de comunicaciones y el ID de este cliente
		this.idCliente = idCliente;
		this.com = com;
	}

	@Override
	// PUNTO DE ENTRADA (DE EJECUCIÓN) PARA TODOS LOS HILOS
	public void run() {
		ProtocolMessages peticion;
		ProtocolMessages respuesta;

		try {			
			Trace.print(idCliente,
					"-- Creando el objeto de servicio ... ");

			// Cada cliente (hilo) tiene su propia instancia de la clase
			// servicio (sus propios datos). Si se desea compartir hay 
			// que usuar campos estáticos
			oos = new WordleOOS(idCliente);
			Trace.println(idCliente, "hecho.");

			// Bucle principal de dialogo con el cliente
			while (!com.closed(idCliente)) {
				try {
					// Espera una petición (evento) del cliente,
					// (llamar a una función remota)
					peticion = com.waitEvent(idCliente);

					// Evaluar la orden recibida
					// Decodifica el mensaje recibido y llama a la función
					// solicitada por el cliente
					respuesta = com.processEvent(idCliente, oos, peticion);

					// Si hay respuesta (sea valor retornado o sea excepción)
					// se envía de vuelta al cliente
					if (respuesta != null) {
						// Enviar el resultado al cliente
						com.sendReply(idCliente, respuesta);
					}
				} catch (ClassNotFoundException e) {
					System.err.printf("Recibido del cliente %d: %s\n",
							idCliente, e.getMessage());
				}				
			}
		} catch (IOException | ChannelException e) {
			// ERROR GRAVE. SE FINALIZA EL HILO
			System.err.printf("Error en hilo del cliente %d: %s\n", idCliente, e.getMessage());
		} finally {
			// Cerrar la instancia de la clase servicio
			if (oos != null) {
				oos.close();
			}
		}
	}

}
