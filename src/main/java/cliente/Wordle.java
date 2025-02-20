package cliente;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;

import comun.*;
import lib.*;

/**
 * Ejemplo de programa cliente.
 */
public class Wordle {

	// AREA DE DATOS
	private static CommClient com;	  	/* Canal de comunicaciones */

	private static boolean dirijoJuego; /* Cierto si el jugador dirige el juego */

	private static int intentos=0;		/* Número de intentos hasta el momento */
	private static String palabra="", palabra_anterior="", diferencias="";
	private static Scanner entrada = new Scanner(System.in); 	/* Entrada estándar */
    private static int tampalabra;
	
	/**
	 * Envío del mensaje correspondiente a un evento que no tendrá
	 * respuesta por parte del servidor. Para crear el mensaje se
	 * requiere la clave con la que se registro el evento en el
	 * servidor y la información adicional que éste precise (argumentos
	 * del evento).
	 * @param id identificador del evento
	 * @param args argumentos requeridos por la función asociada el evento. El parámetro es opcional.
	 * @throws IOException si la conexión falla
	 * @throws ChannelException si hay un error del canal de comunicación
	 */
	private static void enviarSinRespuesta(String id, Object...args)
				throws IOException, ChannelException {
		ProtocolMessages peticion;
		
		// Crear mensaje a enviar
		peticion = new ProtocolMessages(id, args);
		// Enviar mensaje de solicitud al servidor
		// (También se ejecutará la función asociada)
		Wordle.com.sendEvent(peticion);		
	}
	
	/**
	 * Envío del mensaje correspondiente a un evento y que debe
	 * tener respuesta por parte del servidor. El envío del mensaje
	 * es síncrono y ha de esperarse el mensaje de respuesta (puede
	 * ser una excepción). Tras recibir el mensaje de respuesta,
	 * procesa éste y se retorna un objeto o lanza una excepción.
	 * Se retorna null si la función asociada al evento no retorna
	 * ningún resultado (es de tipo void)
	 * @param id identificador del evento
	 * @param args argumentos requeridos por la función asociada el evento. El parámetro es opcional.
	 * @return el valor retornado por la función asociada al evento o null si dicha función retorna void.
	 * @throws IOException si la conexión falla
	 * @throws ChannelException si hay un error del canal de comunicación
	 * @throws ClassNotFoundException si el mensaje recibido no es del formato correcto
	 * @throws UnknownOperation si el evento fue enviado al servidor fue desconocido
	 * @throws Exception si hay un error genérico
	 */
	private static Object enviarConRespuesta(String id, Object...args)
			throws IOException, ChannelException, ClassNotFoundException, UnknownOperation, Exception {
		ProtocolMessages respuesta;
		Object result = null;

		// De momento enviamos la petición
		Wordle.enviarSinRespuesta(id, args);
		// Esperar por la respuesta
		respuesta = Wordle.com.waitReply();
		// Procesar valor del retorno de la función asociada al evento o excepción
		result = Wordle.com.processReply(respuesta);	
		
		// Retornar el resultado
		return result;		
	}

	/**
	 * Intrucciones del programa (para ser mostradas al usuario)
	 */
	private static void instrucciones() {
		System.out.println("Deberás adivinar una palabra generada al azar en un máximo de intentos.");
		System.out.println("Cada vez que propongas una palabra nueva se te indicarán sus diferencias");
		System.out.println("con la palabra a adivinar. Para ello, se compara, letra a letra, tu");
		System.out.println("palabra con la palabra a adivinar, y por cada letra se te indica:");
		System.out.println("\t=    Si la letra es correcta.");
		System.out.println("\tC    Si la letra existe en la palabra a adivinar pero va en otra posición.");
		System.out.println("\tX    Si la letra NO existe en la palabra a adivinar.");
		System.out.println("");
		System.out.println("");
	}

	/**
	 * Muestra un string con un espacio en blanco entre cada caracter
	 * para que se lea más cómodamente y hace un cambio de línea final
	 * @param s string a mostrar
	 */
	private static void muestraPalabra(String s) {
		int i;

		for (i=0; i < s.length(); i++) {
			System.out.print(s.charAt(i) + " ");
		}
		System.out.println("");
	}

	/**
	 * Pide un alias al jugador y se intenta conectar al juego con ese alias
	 * @return Cierto si el jugador debe dirigir el juego. Falso en otro caso
	 * @throws JuegoComenzado si el juego ya ha comenzado
	 * @throws ClassNotFoundException si el mensaje recibido no es del formato correcto
	 * @throws IOException si la conexión falla
	 * @throws ChannelException si hay un error del canal de comunicación
	 * @throws UnknownOperation si el evento fue enviado al servidor fue desconocido
	 * @throws Exception si hay un error genérico
	 */
	private static boolean conectar() 
			throws JuegoComenzado, ClassNotFoundException, IOException, ChannelException, UnknownOperation, Exception {
		boolean ret = false, aliasValido = false;
		String alias;

		while (!aliasValido) {
			System.out.print("Para conectarte al juego debes indicar un alias: ");
			alias = Wordle.entrada.nextLine();
			try  {
				ret = (boolean) Wordle.enviarConRespuesta("conectarse", alias);
				aliasValido = true;
			} catch (AliasInvalido e) {
				System.err.println("¡El alias tiene caracteres raros o ya lo tiene otro jugador! Tendrás que elegir otro.");
			}
		}
		
		// Si este jugador dirige el juuego se le avisa de ello y se espera a que pulse enter
		if (ret) {
			System.out.println("Eres el primer jugador en unirte POR LO QUE DIRIGES EL JUEGO.");
			System.out.println("Espera a que se unan todos los jugadores que quieras y LUEGO PULSA ENTER CUANDO QUIERAS COMENZAR.");
			Wordle.entrada.nextLine();
		}
		return ret;
	}

	/**
	 * Espera pasiva a que terminen el resto de jugadores que aun están jugando
	 * @throws UnknownOperation si el evento fue enviado al servidor fue desconocido
	 * @throws ChannelException si hay un error del canal de comunicación
	 * @throws IOException si la conexión falla
	 * @throws ClassNotFoundException si el mensaje recibido no es del formato correcto
	 * @throws Exception si hay un error genérico
	 */
	private static void esperarSiguienteRonda() 
			throws ClassNotFoundException, IOException, ChannelException, UnknownOperation, Exception {
		String aliasGanador;
		Object result;
		int finalizar = 2;

		System.out.println("");
		System.out.println("¡¡El resto de jugadores no han acabado. Tienes que esperar!!");
		System.out.println("Te avisaremos cuando finalice esta ronda...");
		System.out.println("");
		while (finalizar == 2) {
			// Espera pasiva de un segundo
			Thread.sleep(1000);
			// LLamamos a la operación del servidor que nos indica si finalizó la adivinación actual
			try {
				finalizar = (int) Wordle.enviarConRespuesta("juegoFinalizado", Wordle.palabra);
			} catch (LongitudErronea e) {
				// ESTO NO DEBERÍA DE PASAR NUNCA PORQUE YA HABÍA PASADO ESTE FILTRO
				System.err.println("juegoFinalizado(): ¡El número de letras de la palabra no tiene la longitud esperada!");
				return;
			} catch (FormatoErroneo e) {
				// ESTO NO DEBERÍA DE PASAR NUNCA PORQUE YA HABÍA PASADO ESTE FILTRO
				System.err.println("juegoFinalizado(): ¡La palabra tiene caracteres que no son válidos!");
				return;
			}

		}

		// La ronda ha finalizado. Se informa de por qué motivo
		if (finalizar == 3) {							
			// Otro jugador adivinó la palabra
			// Llamamos a la operación del servidor que dice quién gano
			result = Wordle.enviarConRespuesta("obtenerGanador");
			if (result != null) {
				aliasGanador = (String) result;
			}
			else {
				// ERROR. NO HEMOS OBTENIDO EL GANADOR
				System.err.println("¡ERROR! Algo ha fallado al solicitar el ganador al servidor");
				System.err.println("");
				aliasGanador = "";
			}
			System.out.println("");
			System.out.printf("¡¡EL JUGADOR %s ADIVINÓ LA PALABRA !!\n", aliasGanador);
			System.out.println("");
		}
		else {
			// Todos los jugadores han agotado sus intentos y nadie acertó
			System.out.println("");
			System.out.println("¡¡NADIE ADIVINÓ LA PALABRA !!");
			System.out.println("");
		}
}

	/**
	 * Informa, si procede, al usuario de la palabra anterior y sus diferencias con la palabra a adivinar
	 * Solicita al usuario una nueva palabra y comprueba sus diferencias con la palabra a adivinar
	 * Si la palabra es incorrecta vuelve a solicitarla
	 * @throws UnknownOperation si el evento fue enviado al servidor fue desconocido
	 * @throws ChannelException si hay un error del canal de comunicación
	 * @throws IOException si la conexión falla
	 * @throws ClassNotFoundException si el mensaje recibido no es del formato correcto
	 * @throws Exception si hay un error genérico
	 */
	private static void solicitarPalabraUsuario() 
			throws ClassNotFoundException, IOException, ChannelException, UnknownOperation, Exception {
		boolean solicitarEntrada = true;
		String pal;

		while (solicitarEntrada) {
			try {
				System.out.printf("\n¡ INTENTO %d !\n", Wordle.intentos);
				// Muestra la palabra introducida anteriormente y sus diferencias
				if (Wordle.intentos > 1) {
					System.out.println("Última palabra introducida y sus diferencias:");
					Wordle.muestraPalabra(Wordle.diferencias);
					Wordle.muestraPalabra(Wordle.palabra_anterior);
					System.out.println("Introduce otra palabra (puedes poner blancos entre caracteres):");
				}
				else {
					System.out.println("Introduce una palabra (puedes poner blancos entre caracteres):");
				}
				// Solicitamos la siguiente palabra
				pal = Wordle.entrada.nextLine();
				// Eliminamos espacios en blanco de la palabra
				Wordle.palabra = pal.replace(" ", "");
				// Llamamos a la operación del servidor que solicita las diferencias
				Object result = Wordle.enviarConRespuesta("obtenerDiferencias", Wordle.palabra);
				if (result != null) {
					Wordle.diferencias = (String) result;
					solicitarEntrada=false;
				}
			} catch (NoSuchElementException e) {
				System.out.println("¡Debes introducir una palabra!");
				System.out.println("");
			} catch (LongitudErronea e) {
				System.out.printf("¡El número de letras de la palabra debe ser %d!\n",Wordle.tampalabra);
				System.out.println("");
			} catch (FormatoErroneo e) {
				System.out.println("¡La palabra sólo puede contener letras!");
				System.out.println("");
			}
		} 
	}

	/**
	 * Pregunta al usuario si está listo o no para empezar la siguiente ronda
	 * @return Cierto si desea adivinar otra palabra, Falso en otro caso
	 */
	private static boolean adivinarOtraPalabra() {
		String respuesta = "";

		while (! respuesta.equals("s") && ! respuesta.equals("n")) {
			try {

				System.out.print("¿Estás listo para adivinar otra palabra (siguiente ronda)? (S/N) ");

				respuesta = Wordle.entrada.nextLine().toLowerCase();
			} catch (NoSuchElementException e) {
				System.out.println("¡ Debes introducir S/N !");
				System.out.println("");
			}
		} // Del bucle para solicitar si desea seguir o no
		return respuesta.equals("s");
	}

	/**
	 * Bucle principal del cliente
	 */  
	private static void run() {
		boolean salir=false;
		int finalizar=0;
		

		// Imprime la bienvenida y las instrucciones
		System.out.println("¡¡BIENVENIDO AL WORDLE REMOTO!!");
		System.out.println("");
		Wordle.instrucciones();
		
		try {

			Wordle.dirijoJuego = Wordle.conectar();

			while (!salir) {				

				// Elige una palabra a adivinar al azar
				try {
					if (Wordle.dirijoJuego) {
						System.out.println("");
						System.out.println("¡¡Todos los jugadores preparados!!");
						System.out.println("Generando nueva palabra a adivinar...");
						System.out.println("");
						System.out.println("Introduce el numero de letras de la palabra a adivinar");
						boolean creada=false;
						while(!creada){
							try {
								Wordle.tampalabra=Wordle.entrada.nextInt();
								Wordle.enviarConRespuesta("nuevaPalabra",Wordle.tampalabra);
								creada=true;
							}
							catch(InputMismatchException e){
								System.out.println("Debe introducirse un valor entero");
							}
							catch(ErrorDiccionario e) {
								System.out.println("No se pudo crear diccionario de ese numero de letras introduzca otr numero");
							}
						}
						
					}
					else {
						System.out.println("");
						System.out.println("¡¡Esperando a que el jugador que dirige el juego genere una nueva palabra...!!");
						System.out.println("");
						try {
							Wordle.enviarConRespuesta("nuevaPalabra",0);
							Wordle.tampalabra=(int)Wordle.enviarConRespuesta("LongitudPalabra");
						}
						catch(ErrorDiccionario e) {
							System.out.println("error en el diccionario");
						}
					}
					// Operación bloqueante para todos los jugadores que no dirigen el juego
					
				} catch (LongitudErronea e) {
					// ERROR GRAVE. NO HAY PALABRAS DE LA LONGITUD INDICADA
					System.err.printf("ERROR: ¡No hay palabras de %d letras en el diccionario!\n", Wordle.tampalabra);
					return;
				}
				
				System.out.printf("¡Palabra a adivinar generada! (tiene %d letras)\n", Wordle.tampalabra);

				// Bucle para tratar la adivinación de la palabra actual
				Wordle.intentos = 0;
				finalizar = 0;
				while (finalizar == 0) {
					// Un intento más
					Wordle.intentos++; 

					// Solicita al usuario una palabra y comprueba sus diferenias con la palabra a adivinar,
					// (llama a la operación del servidor que retorna las diferencias).
					// Si la palabra no es correcta vuelve a preguntar otra
					Wordle.solicitarPalabraUsuario();

					// Comprueba si se adivinó la palabra o quedan intentos.
					// LLamamos a la operación del servidor que nos indica si finalizó la adivinación actual
					try {
						Object result = Wordle.enviarConRespuesta("juegoFinalizado", Wordle.palabra);
						if (result != null) {
							finalizar = (int) result;
						}
						else {
							// Indicamos un valor imposible para que más adelante entre por el default del switch
							// y detecte error
							finalizar = -1;
						}
					} catch (LongitudErronea e) {
						// ESTO NO DEBERÍA DE PASAR NUNCA PORQUE YA HABÍA PASADO ESTE FILTRO
						System.err.println("juegoFinalizado(): ¡El número de letras de la palabra no tiene la longitud esperada!");
						return;
					} catch (FormatoErroneo e) {
						// ESTO NO DEBERÍA DE PASAR NUNCA PORQUE YA HABÍA PASADO ESTE FILTRO
						System.err.println("juegoFinalizado(): ¡La palabra tiene caracteres que no son válidos!");
						return;
					}

					// Si se finalizó, indica por qué al usuario
					switch (finalizar) {
						case 0:
							// No se adivinó la palabra pero quedan más intentos
							Wordle.palabra_anterior = Wordle.palabra;
							break;
						case 1:
							// Se adivinó la palabra
							System.out.println("");
							System.out.println("¡¡GANASTE ESTA RONDA!!");
							System.out.printf("¡¡ACERTASTE LA PALABRA EN %d INTENTOS!!\n", Wordle.intentos);
							System.out.println("");
							break;
						case 2:
							
						case 4:

							// Se agoraton los intentos
							System.out.println("");
							System.out.println("¡¡LO SIENTO, YA NO TE QUEDAN MÁS INTENTOS!!");
							System.out.println("");
							System.out.println(("La palabra a adivinar era:"));
							// Llamamos a la operación del servidor que dice cuál era la palabra a adivinar
							Object result = Wordle.enviarConRespuesta("solicitarPalabra");
							if (result != null) {
								Wordle.muestraPalabra((String)result);
							}
							else {
								// ERROR. NO HEMOS OBTENIDO LA PALABRA A ADIVINAR
								System.err.println("¡ERROR! Algo ha fallado al solicitar la palabra a adivinar al servidor");
								System.err.println("");
							}

							if (finalizar == 4) {
								// Es el último jugador que agotó los intentos
								System.out.println("");
								System.out.println("¡¡ SOLO QUEDABAS TÚ!! ¡¡NADIE ADIVINÓ LA PALABRA !!");
								System.out.println("");
							}
							break;
						case 3:
							// Otro jugador adivinó la palabra
							String aliasGanador;
							Object result2;
							// Llamamos a la operación del servidor que dice quién gano
							result2 = Wordle.enviarConRespuesta("obtenerGanador");
							if (result2 != null) {
								aliasGanador = (String) result2;
							}
							else {
								// ERROR. NO HEMOS OBTENIDO EL GANADOR
								System.err.println("¡ERROR! Algo ha fallado al solicitar el ganador al servidor");
								System.err.println("");
								aliasGanador = "";
							}
							System.out.println("");
							System.out.printf("¡¡LO SIENTO, SE TE HAN ADELANTADO. EL JUGADOR %s ADIVINÓ LA PALABRA !!\n", aliasGanador);
							System.out.println("");
							System.out.println(("La palabra a adivinar era:"));
							// Llamamos a la operación del servidor que dice cuál era la palabra a adivinar
							result2 = Wordle.enviarConRespuesta("solicitarPalabra");
							if (result2 != null) {
								Wordle.muestraPalabra((String)result2);
							}
							else {
								// ERROR. NO HEMOS OBTENIDO LA PALABRA A ADIVINAR
								System.err.println("¡ERROR! Algo ha fallado al solicitar la palabra a adivinar al servidor");
								System.err.println("");
							}
							break;

						default:
							// ERROR GRAVE. juegoFinalizado() no nos ha retornado un valor correcto
							System.err.println("juegoFinalizado(): No ha retornado un valor correcto");
							return;
					} // Del switch
				} // Del bucle para tratar la palabra a adivinar actual

				// Si finalizar vale 2 hay que esperar a que valga 3 ó 4. Se avisa al jugador
				if (finalizar == 2) {					
					// El jugador debe esperar a que finalicen todos los demás jugadores
					Wordle.esperarSiguienteRonda();
				}

				// COMENTARIOS CAMBIADOS PORQUE AHORA NO SE DEJA SALIR. DARÍA PROBLEMAS...
				// Pregunta al usuario está listo para adivinar otra palabra más (siguiente ronda)
				while (!Wordle.adivinarOtraPalabra());

				// El usuario está listo para adivinar otra palabra
				// Llamamos a la operación del servidor que reinicia todo
				// Esta operación es BLOQEANTE para el jugador que dirige el juego
				// (no regresa hasta que no estén todos los demás jugadores listos)
				if (Wordle.dirijoJuego) {
					System.out.println("");
					System.out.println("¡¡Diriges el juego, tienes que esperar a que todos los jugadores estén listos antes de generar otra palabra!!");
					System.out.println("Te avisaremos cuando ocurra...");
					System.out.println("");
				}
				Wordle.enviarConRespuesta("reiniciarJuego");
			} // Del bucle principal del programa
		} catch (JuegoComenzado e) {
			// ERROR GRAVE. LA PARTIDA YA HA COMENZADO Y YA NO ES POSIBLE UNIRSE
			System.err.println("¡La partida ya ha comenzado y no te puedes unir! ¡Inténtalo más tarde!");
			return;


		} catch (ANP_Exception  e) {
			// ERROR GRAVE. EL SERVIDOR NO ESPERABA QUE LLAMARAMOS A ESTA OPERACIÓN
			// SIGNIFICA QUE SERVIDOR Y CLIENTE NO VAN SINCRONIZADOS
			System.err.printf("¡ERROR GRAVE EN EL PROGRAMA! Error: %s\n", e.getMessage());
			System.err.println("Servidor y cliente no van sincronizados)");
			return;
		} catch (Exception e) {
			// ERROR GRAVE. ERRROR DESCONOCIDO
			System.err.printf("¡ERROR GRAVE EN EL PROGRAMA2! Error: %s\n", e.getMessage());
			return;
		} 
	} // run

	public static void main(String[] args) {		
		try {
			// Establece la comunicación con el servidor,
			// crear el canal de comunicación y establecer la
			// conexión con el servicio, por defecto en localhost
			Wordle.com = new CommClient();
			
			// Activa el registro de mensajes del cliente (opcional)
			Wordle.com.activateMessageLog();
		} catch (UnknownHostException e) {
			// ERROR GRAVE. SE FINALIZA EL CLIENTE
			System.err.printf("Servidor desconocido. %s\n", e.getMessage());
			System.exit(-1);
		} catch (IOException | ChannelException e) {
			// ERROR GRAVE. SE FINALIZA EL CLIENTE
			System.err.printf("Error: %s\n", e.getMessage());
			System.exit(-2);
		}
		
		// Ejecuta el bucle principal del cliente
		Wordle.run();

		// Cierra la entrada estándar
		Wordle.entrada.close();

		// Cierra el canal de comunicación y desconecta el cliente
		Wordle.com.disconnect();
	} // main

} // class Wordle
