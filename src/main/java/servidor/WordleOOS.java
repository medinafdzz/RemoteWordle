package servidor;

import comun.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


/**
 * Clase del objeto de operaciones de servicio (OOS).
 *
 */
public class WordleOOS implements IWordle {
	// Constantes
	static final int MAX_INTENTOS = 5;

	// Area de datos
	private int idCliente;
	private int numeroIntentos;
	private int estado;

	// Area de datos COMPARTIDA

	// Lista de posibles palabras a adivinar
	// Todas las palabras serán de tamaño IWordle.TAM_PALABRA
	// NO ES NECESARIO GARANTIZAR LA EXCLUSIÓN MUTUA DE ESTA VARIABLE
	// PORQUE NUNCA SE MODIFICA.
	static List<String> palabrasAdivinar;

	private static int tampalabra;
	// Palabra a adivinar pasa a ser global. Habrá que sustituir this. por WordleOOS. en todas sus apariciones
	// NO VA A SER NECESARIO GARANTIZAR SU EXCLUSIÓN MUTUA
	private static String palabraSecreta = null;
	// Almacena el primer cliente que se ha conectado AL JUEGO (-1 indica que de momento ninguno)
	private static int primerCliente = -1; 
	// Indica si ya dado comienzo la partida o no
	private static boolean partidaComenzada = false;
	// Almacena los alias de los jugadores (la clave es idCliente)
	private static Map<Integer, String> aliasJugadores = new TreeMap<>(); 
	// Indica si un jugador agotó ya todos sus intentos (nos permitirá identificar el fin del juego por esta causa)
	private static Map<Integer, Boolean> intentosAgotados = new TreeMap<>(); 
	// Indica el jugador que adivinó la palabra (nos permitirá identificar el fin del juego por este motivo)
	private static int ganador = -1;

	// CAMPOS UTILIZADOS PARA REALIZAR LA SINCRONIZACIÓN EN CIERTOS MOMENTOS DEL JUEGO
	// El siguiente campo se usa para que el primer cliente sepa si el resto de jugadores están ya listos para la
	// siguiente ronda, han llamado ya a reiniciarJuego(), y pueda así generar otra palabra nueva a adivinar
	private static Map<Integer, Boolean> jugadorPreparado = new TreeMap<>();
	// El siguiente campo se usa para que el primer cliente indique a los demás jugadores si ya se generó una nueva
	// palabra a adivinar o no y que éstos puedan comenzar a jugar.
	private static boolean palabraGenerada = false;

	// CAMPOS UTILIZADOS PARA GARANTIZAR LA EXCLUSIÓN MUTUA
	private static volatile Object mutexPrimerCliente = new Object();
	private static volatile Object mutexPartidaComenzada = new Object();
	private static volatile Object mutexAliasJugadores = new Object();
	private static volatile Object mutexIntentosAgotados = new Object();
	private static volatile Object mutexGanador = new Object();
	private static volatile Object mutexJugadorPreparado = new Object();
	private static volatile Object mutexPalabraGenerada = new Object();


	// Constructor
	public WordleOOS(int id) {
		// Almacenamos el ID del cliente
		this.idCliente = id;
		
		// Inicializamos datos no compartidos
		this.numeroIntentos = 0;

		this.estado = -1;

	}

	@Override
	public boolean conectarse(String alias) throws ANP_Exception, AliasInvalido, JuegoComenzado {
		boolean ret = false;

		if (this.estado != -1) {
			throw new ANP_Exception("No está permitido llamar a conectarse en estos momentos.");
		}
		// Comprobamos si todavía es posible unirse a la partida o no
		// ACCESO EXCLUSIVO A WordleOOS.partidaComenzada
		synchronized (WordleOOS.mutexPartidaComenzada) {
			if (WordleOOS.partidaComenzada) {
				throw new JuegoComenzado("El juego ya ha comenzado. No puedes unirte. Inténtalo más tarde.");
			}
		}
		// Comprobamos si el alias es válido
		if (alias.trim().isEmpty()) {
			throw new AliasInvalido("El alias no tiene un formato correcto o está vacío");
		}

		// Si es el primer cliente que se conecta, almacenamos su ID
		// ACCESO EXCLUSIVO A WordleOOS.primerCliente
		synchronized (WordleOOS.mutexPrimerCliente) {
			if (WordleOOS.primerCliente == -1) {
				WordleOOS.primerCliente = this.idCliente;
				ret = true;
			}
		}
		
		// Almacenamos el alias
		// ACCESO EXCLUSIVO A WordleOOS.aliasJugadores
		// ES MUY IMPORTANTE QUE TODAS LAS LÍNEAS QUE VIENEN A CONTINUACIÓN
		// (HASTA EL SIGUIENTE COMENTARIO) ESTÉN BAJO LA MISMA SECCIÓN CRÍTICA
		// (MISMO synchronized) DE LO CONTRARIO PODRÍA HABER ALIAS REPETIDOS
		synchronized (WordleOOS.mutexAliasJugadores) {
			if (WordleOOS.aliasJugadores.containsValue(alias)) {
				throw new AliasInvalido("El alias ya lo está usando otro jugador");
			}
			WordleOOS.aliasJugadores.put(this.idCliente, alias);
		}

		// Crea una entrada nueva para este cliente en WordleOOS.jugadorPreparado
		// ACCESO EXCLUSIVO A WordleOOS.jugadorPreparado
		synchronized (WordleOOS.mutexJugadorPreparado) {
			WordleOOS.jugadorPreparado.put(this.idCliente, true);
		}

		// Crea una entrada nueva para este cliente en WordleOOS.intentosAgotados
		// ACCESO EXCLUSIVO A WordleOOS.intentosAgotados
		synchronized (WordleOOS.mutexIntentosAgotados) {
			WordleOOS.intentosAgotados.put(this.idCliente, false);
		}

		// Pasamos al estado 0 y retornamos
		this.estado = 0;

		return ret;
	}

	@Override
	public void reiniciarJuego() throws ANP_Exception {
		boolean todosPreparados = false;

		if (this.estado != 4) {
			throw new ANP_Exception("No está permitido llamar a reiniciarJuego en estos momentos.");
		}

		// La palabra secreta NO SE PUEDE cambiar aquí porque otros clientes pueden estar en el estado 4
		// y solicitarla
		// this.palabraSecreta = null;

		// Se resetean los intentos del jugador
		this.numeroIntentos = 0;		
		// ACCESO EXCLUSIVO A WordleOOS.intentosAgotados
		synchronized (WordleOOS.mutexIntentosAgotados) {
			WordleOOS.intentosAgotados.put(this.idCliente, false);
		}

		// Indico que este jugador ya está preparado para jugar la siguiente ronda
		// ACCESO EXCLUSIVO A WordleOOS.jugadorPreparado
		synchronized (WordleOOS.mutexJugadorPreparado) {
			WordleOOS.jugadorPreparado.put(this.idCliente, true);
		}

		// Comprobamos si es el primer cliente o no
		// NO ES NECESARIO GARANTIZAR LA EXCLUSION MUTUA DE WordleOOS.primerCliente porque una
		// vez que comienza el juego nadie la modifica
		if (this.idCliente == WordleOOS.primerCliente) {
			// Soy el primer cliente. Dirijo el juego y debo esperar a que todos estén listos 
			// para la siguiente ronda (todos hayan llamado a esta operación)
			// ACCESO EXCLUSIVO A WordleOOS.jugadorPreparado PERO OJO SOLO CUANDO NO ESTÉ HACIENDO
			// LA ESPERA PASIVA
			while (!todosPreparados) {
				// Espera pasiva de un segundo;
				WordleOOS.esperaPasiva(1000);
				synchronized (WordleOOS.mutexJugadorPreparado) {
					todosPreparados = ! WordleOOS.jugadorPreparado.containsValue(false);
				}
			}

			// Este punto es el más idóneo para resetear el ganador. No se puede hacer antes porque
			// otros clientes lo necesitan mientras estén en el estado 4
			// NO ES NECESARIO GARANTIZAR SU EXCLUSIÓN MUTUA PUES NADIE MÁS PUEDE ESTAR ACCEDIENDO
			WordleOOS.ganador = -1;
		}

		// Pasamos al estado 0 y retornamos
		this.estado = 0;
	}

	@Override
	public void nuevaPalabra(int n) throws ANP_Exception, LongitudErronea, ErrorDiccionario {
		int numPalabras;
		int alAzar;
		Random aleatorio = new Random();
		boolean palGenerada = false;

		if (this.estado != 0) {
			throw new ANP_Exception("No está permitido solicitar una nueva palabra en este momento.");
		}
		if(this.idCliente == WordleOOS.primerCliente) {
			if(n<=0)
				throw new LongitudErronea();
			WordleOOS.tampalabra=n;
			WordleOOS.palabrasAdivinar=WordleOOS.inicializarDiccionario();
			if(WordleOOS.palabrasAdivinar==null)
				throw new ErrorDiccionario();
		}
		
		// Sólo el primer cliente debe generar la nueva palabra
		// NO ES NECESARIO GARANTIZAR LA EXCLUSION MUTUA DE WordleOOS.primerCliente porque una
		// vez que comienza el juego nadie lo va a modificar nunca (se usa sólo para leer)
		if (this.idCliente == WordleOOS.primerCliente) {
			// Soy el primer cliente. Dirijo el juego

			// Este es el momento de actualizar la variable compartida WordleOOS.partidaComenzada
			// (si todavía no está puesta a cierto se pone a cierto (también se podría poner siempre)
			// Obsérvese que sólo lo hace el cliente que dirige el juego
			// ACCESO EXCLUSIVO A WordleOOS.mutexPartidaComenzada
			synchronized (WordleOOS.mutexPartidaComenzada) {
				if (! WordleOOS.partidaComenzada) {
					// A partir de este momento ya nadie se puede unir al juego
					WordleOOS.partidaComenzada = true;
				}
			}
			
			// Obtiene el número de palabras a adivinar que hay
			numPalabras = WordleOOS.palabrasAdivinar.size();

			// Genera un número aleatorio entre 0 y numPalabras-1
			alAzar = aleatorio.nextInt(numPalabras);

			// La palabra a adivinar será la que ocupe la posición alAzar 
			// en la lista de palabras a adivinar
			// NO ES NECESARIO GARANTIZAR LA EXCLUSION MUTUA DE WordleOOS.palabraSecreta porque
			// en este momento solo este cliente acceda a ella (el resto están esperando)
			WordleOOS.palabraSecreta = WordleOOS.palabrasAdivinar.get(alAzar);

			// Indico que ya se generó la nueva palabra
			// ACCESO EXCLUSIVO A WordleOOS.palabraGenerada
			synchronized (WordleOOS.mutexPalabraGenerada) {
				WordleOOS.palabraGenerada = true;
			}
		}
		else {
			// No soy el primer cliente. Tengo que esperar a que se genere la palabra
			// ACCESO EXCLUSIVO A WordleOOS.palabraGenerada
			// PERO OJO SOLO CUANDO NO ESTÉ HACIENDO LA ESPERA PASIVA
			while (! palGenerada) {
				// Espera pasiva de 1 segundo
				WordleOOS.esperaPasiva(1000);
				synchronized (WordleOOS.mutexPalabraGenerada) {
					palGenerada = WordleOOS.palabraGenerada;
				}
			}
		}

		this.estado = 1;
	}



	@Override
	public String obtenerDiferencias(String palabra) throws ANP_Exception, LongitudErronea, FormatoErroneo {
		String palMayusculas;
		String diferencias = "";
		Map<Integer, Integer> ocurrenciasPalabraSecreta = new HashMap<>();
		Map<Integer, Integer> ocurrenciasPalabra = new HashMap<>();
		int c, cSecreto;

		if (this.estado != 1) {
			throw new ANP_Exception("No está permitido llamar a obtenerDiferencias en estos momentos.");
		}

		// NO ES NECESARIO GARANTIZAR EL ACCESO EXCLUSIVO A WordleOOS.palabraSecreta EN TODA LA FUNCIÓN
		// PORQUE EN ESTE PUNTO NADIE LA PUEDE MODIFICAR (SOLO SE USA PARA CONSULTAR)

		if (palabra.length() != WordleOOS.palabraSecreta.length()) {
			throw new LongitudErronea("El número de caracteres de la palabra no es correcto.");
		}
		if (! WordleOOS.palabraValida(palabra)) {
			throw new FormatoErroneo("La palabra contiene caracteres que no son válidos.");
		}

		palMayusculas = palabra.toUpperCase();

		// Para informar correctamente de las diferencias son necesarias dos pasadas 
		// dos bucles:
		
		// En el primer bucle contaremos el número de ocurrencias de 
		// cada carácter en la palabra secreta y el número de ocurrencias de cada carácter
		// BIEN COLOCADO, en la palabra propuesta
		// Necesitaremos esas ocurrencias para hacer correctamente el segundo bucle

		// En el segundo bucle generamos el string de diferencias:
		// Carácter en su sitio --> "="
		// Carácter no existe --> "X"
		// Carácter existe y el nº ocurrencias hasta el momento + Nº ocurrencias bien colocadas que van después
		// no supera el número de ocurrencias totales en la palabra a adivinar --> "C"
		// Carácter existe y el nº ocurrencias hasta el momento + Nº ocurrencias bien colocadas que van después
		// SUPERA el número de ocurrencias totales en la palabra a adivinar --> "X"
		
		// Son necesarias dos pasadas PORQUE HAY QUE PRIORIZAR las ocurrencias BIEN COLOCADAS
		// frente a las ocurrencias cambiadas de sitio.

		// PRIMERA PASADA: Contamos las ocurrencias...
		for (int i=0; i < palMayusculas.length(); i++) {
			// Obtenemos el carácter actual de la palabra propuesta y de la palabra a adivinar
			c = palMayusculas.charAt(i);
			cSecreto = WordleOOS.palabraSecreta.charAt(i);

			// Actualizamos las ocurrencias del caracter actual en la palabra a adivinar
			if (ocurrenciasPalabraSecreta.containsKey(cSecreto)) {
				ocurrenciasPalabraSecreta.put(cSecreto, ocurrenciasPalabraSecreta.get(cSecreto) + 1);
			}
			else {
				ocurrenciasPalabraSecreta.put(cSecreto, 1);
			}

			// ¿Está el carácter actual bien colocado?
			if (c == cSecreto) {
				// El carácter actual está en la posición correcta.
				// Indicamos que tenemos una ocurrencia más, bien colocada, de este carácter
				if (ocurrenciasPalabra.containsKey(c))
				{
					ocurrenciasPalabra.put(c, ocurrenciasPalabra.get(c) + 1);
				}
				else {
					ocurrenciasPalabra.put(c, 1);
				}
			}
		}

		// SEGUNDA PASADA. Generamos el string de diferencias
		for (int i=0; i < palMayusculas.length(); i++) {
			// Obtenemos el carácter actual de la palabra propuesta y de la palabra a adivinar
			c = palMayusculas.charAt(i);
			cSecreto = WordleOOS.palabraSecreta.charAt(i);

			// ¿El carácter está bien colocado?
			if (c == cSecreto) {
				diferencias = diferencias + "=";
			}
			else {
				// ¿El carácter está en la palabra a adivinar?
				if (! ocurrenciasPalabraSecreta.containsKey(c)) {
					diferencias = diferencias + "X";
				}
				else {
					// El carácter está cambiado de sitio. Añadimos esta ocurrencia
					// en la palabra propuesta
					if (ocurrenciasPalabra.containsKey(c)) {
						ocurrenciasPalabra.put(c, ocurrenciasPalabra.get(c) + 1);
					}
					else {
						ocurrenciasPalabra.put(c, 1);
					}
					// ¿Excede el número de ocurrencias a las ocurrencias totales?
					if (ocurrenciasPalabra.get(c) <= ocurrenciasPalabraSecreta.get(c)) {
						diferencias = diferencias + "C";
					}
					else {
						diferencias = diferencias + "X";
					}
				}
			}
		}

		this.numeroIntentos++;
		this.estado = 2;
		return diferencias;
	}

	@Override
	public int juegoFinalizado(String palabra) throws ANP_Exception, LongitudErronea, FormatoErroneo {
		// Es necesario detectar el primer momento en que se detecta el final del juego porque es el momento
		// más apropiado para resetear las variables utilizadas para la sincronización entre jugadores:
		// WordleOOS.juegosPreparados y WordleOOS.palabraGenerada
		// Hay dos momentos en que recién finaliza el juego:
		//     1  Un jugador adivinó la palabra
		//     2  Todos los jugadores agotaron todos los intentos

		if (this.estado != 2 && this.estado != 3) {
			throw new ANP_Exception("No está permitido llamar a juegoFinalizado en estos momentos.");
		}
		// NO ES NECESARIO GARANTIZAR LA EXCLUSIÓN MUTUA DE WordleOOS.palabraSecreta PORQUE NADIE LA PUEDE
		// MODIFICAR EN ESTE MOMENTO
		if (palabra.length() != WordleOOS.palabraSecreta.length()) {
			throw new LongitudErronea("El número de caracteres de la palabra no es correcto.");
		}
		if (! WordleOOS.palabraValida(palabra)) {
			throw new FormatoErroneo("La palabra contiene caracteres que no son válidos.");
		}

		// ESTAS COMPROBACIONES HAY QUE HACERLAS EXACTAMENTE EN ESTE ORDEN PARA NO DAR COMO GANADOR AL
		// JUGADOR EQUIVOCADO

		// ¿Juego finalizado porque otro jugador ya adivinó la palabra?
		// HAY QUE GARANTIZAR EL ACCESO EXCLUSIVO A WordleOOS.ganador y la exclusión mutua
		// DEBE ABARCAR TODO EL CÓDIGO INDICADO A CONTINUACIÓN. DE LO CONTRARIO PODRÍA
		// DARSE EL CASO DE QUE DOS O MÁS JUGADORES SE CREYERAN GANADORES DEL JUEGO
		synchronized (WordleOOS.mutexGanador) {
			if (WordleOOS.ganador != -1) {
				this.estado = 4;
				return 3;
			}

			// ¿Juego finalizado porque ESTE jugador ya adivinó la palabra?
			// ESTO SÓLO LO COMPROBAREMOS SI EL ESTADO ACTUAL ES 2
			// NO ES NECESARIO GARANTIZAR LA EXCLUSIÓN MUTUA DE WordleOOS.palabraSecreta PORQUE NADIE LA PUEDE
			// MODIFICAR EN ESTE MOMENTO
			if (this.estado == 2 && WordleOOS.palabraSecreta.equals(palabra.toUpperCase())) {
				// ESTE JUGADOR adivinó la palabra. Anotamos que es el ganador
				WordleOOS.ganador = this.idCliente;

				// Reseteamos las variables utilizadas para sincronizar (leer comentario al comienzo de esta función)
				// NO ES NECESARIO GARANTIZAR LA EXCLUSIÓN MUTUA DE LAS DOS VARIABLES UTILIZADAS A CONTINUACIÓN PORQUE
				// EN ESTE PUNTO NADIE PUEDE ESTAR ACCEDIENDO A ELLAS SALVO EL CLIENTE ACTUAL
				for (Integer idC: WordleOOS.jugadorPreparado.keySet()) {
					WordleOOS.jugadorPreparado.put(idC, false);
				}
				WordleOOS.palabraGenerada = false;

				this.estado = 4;
				return 1;
			}
		} // Fin exclusión mutua WordleOOS.ganadorr
	   
		// ¿Juego finalizado porque no le quedan intentos a ningún jugador?
		// ACCESO EXCLUSIVO A WordleOOS.intentosAgotados
		synchronized (WordleOOS.mutexIntentosAgotados) {
			if (! WordleOOS.intentosAgotados.containsValue(false)) {			
				this.estado = 4;
				return 4;
			}
		}

		// ¿El jugador está esperando a que otros jugadores terminen?
		if (this.estado == 3) {
			// El jugador ya agotó sus intentos y está esperando a que los demás terminen
			return 2;
		}

		// ¿Es el último intento de ESTE JUGADOR?
		if (this.estado == 2 && this.numeroIntentos == WordleOOS.MAX_INTENTOS) {
			// Ya no le quedan intentos. Lo anotamos
			// ACCESO EXCLUSIVO A WordleOOS.intentosAgotados
			synchronized (WordleOOS.mutexIntentosAgotados) {
				WordleOOS.intentosAgotados.put(this.idCliente, true);
				// ¿Es este el último jugador que agota sus intentos?
				if (! WordleOOS.intentosAgotados.containsValue(false)) {
					// Sí. Es el último jugador que agota sus intentos
					// Reseteamos las variables utilizadas para sincronizar (leer comentario al comienzo de esta función)
					// NO ES NECESARIO GARANTIZAR LA EXCLUSIÓN MUTUA DE LAS DOS VARIABLES UTILIZADAS A CONTINUACIÓN PORQUE
					// EN ESTE PUNTO NADIE PUEDE ESTAR ACCEDIENDO A ELLAS SALVO EL CLIENTE ACTUAL
					for (Integer idC: WordleOOS.jugadorPreparado.keySet()) {
						WordleOOS.jugadorPreparado.put(idC, false);
					}
					WordleOOS.palabraGenerada = false;
					this.estado = 4;
					return 4;
				}
			}
			this.estado = 3;
			return 2;
		}


		// El juego no ha finalizado (quedan intentos y no se adivinó la palabra)
		// Hay que seguir adivinando...
		this.estado = 1;
		return 0;
	}

	@Override
	public String solicitarPalabra() throws ANP_Exception {
		
		if (this.estado != 3 && this.estado != 4) {

			throw new ANP_Exception("No está permitido llamar a solicitarPalabra en estos momentos.");
		}
		return WordleOOS.palabraSecreta;
	}

	@Override
	public String obtenerGanador() throws ANP_Exception {
		// NO ES NECESARIO GARANTIZAR LA EXCLUSION MUTUA DE NINGUNA
		// DE LAS DOS VARIABLES PORQUE NADIE LAS PUEDE MODIFICAR
		// EN ENTOS MOMENTOS
		if (this.estado != 4) {
			throw new ANP_Exception("No está permitido llamar a obtenerGanador en estos momentos.");
		}
		return WordleOOS.aliasJugadores.get(WordleOOS.ganador);
	}


	/**
	 * Duerme a un hilo del Wordle para realizar una espera pasiva
	 * @param milisegundos Tiempo en milisegundos que se desea dormir al hilo
	 */
	static private void esperaPasiva(int milisegundos) {
		try {
			Thread.sleep(milisegundos);
		} catch (InterruptedException e) {
			return;
		}
	}

	/**
	 * Comprueba si los caracteres de la palabra son válidos
	 * @param palabra
	 * @return Cierto si la palabra contiene letras válidas. Falso en otro caso
	 */
	static private boolean palabraValida(String palabra) {
		return palabra.matches("[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ]+");
	}

	/**
	 * 
	 * @return Una lista con las palabras de la RAE que sean de longitud IWordle.TAM_PALABRA
	 */
	static private List<String> inicializarDiccionario() {
		String ruta_dicc = "src/main/resources/palabras_español.txt";
		String palabra;
		int tam;
		List<String> diccionario;

		// Creamos la lista de palabras a adivinar
		diccionario = new ArrayList<>(5000);

		// Abrimos el fichero diccionario para almacenar las de tamaño IWordle.TAM_PALABRA
		// Si no se puede abrir el fichero o si falla la lectura, se retornará null
		try {
			Path path = Paths.get(ruta_dicc);
			BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
			palabra = reader.readLine();
			while (palabra != null) {
				// Obtenemos la longitud de la palabra
				tam = palabra.length();
				// Comprobamos que la palabra no tenga caracteres extraños y que tenga
				// el tamaño adecuado
				if (tam == WordleOOS.tampalabra && WordleOOS.palabraValida(palabra)) {
					// Pasamos la palabra a mayúsculas y la introducimos en la lista
					diccionario.add(palabra.toUpperCase());
				}
				// Leemos la siguiente palabra del fichero
				palabra = reader.readLine();
			}
			// Cerramos el fichero
			reader.close();
		} catch (FileNotFoundException e) {
			// Esto nos permitirá indicar que falló la lectura del diccionario
			diccionario = null;
		} catch (IOException e) {
			// Esto nos permitirá indicar que falló la lectura del diccionario
			diccionario = null;
		}

		return diccionario;
	}
	
	public int LongitudPalabra() throws ANP_Exception{
		return palabraSecreta.length();
	}
}
