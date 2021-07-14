package projetosd;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Servidor {
	
	private static boolean debug = false;
	
	private static int porta;
	
	/*
	 * Threads do servidor:
	 * 
	 * Thread principal (ouvir UDP)
	 * IdentificarPacote - Thread de identificação de pacotes UDP, chama pela thread principal quando um pacote é lido
	 * JoinPeer ; LeavePeer ; UpdatePeer ; SearchPeer - Threads chamadas pela thread de identificação para terminar processamentos
	 * AliveTimer - Thread que controla o envio de sinais ALIVE para os peers e checa quais responderam com ALIVE_OK
	 * Alive - Thread utilizada para enviar um sinal de ALIVE para um peer específico, chamada pela thread AliveTimer
	 * *O ALIVE_OK recebido é processado na thread IdentificarPacote
	 */
	
	
	/*
	 * Estrutura de armazenamento do peers
	 * */
	
	// Formato da key:
	// [IP]:[PORT]
	
	// Arquivos relacionados a uma key
	static Hashtable<String, String> listaPeers = new Hashtable<>();
	// Datagrampackets relacionados a keys (para enviar o ALIVE)
	static Hashtable<String, DatagramPacket> listaPktPeers = new Hashtable<>();
	
	// Lista de peers que responderam a um ALIVE
	static Map<String, String> vivos = new ConcurrentHashMap<String, String>();
	
	public static void main(String[] args) throws Exception {
		
		// Obter a porta UDP para fazer a comunicação com os peers
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Insira a porta UDP para comunicação (padrão: 10098)");
		porta = Integer.parseInt(reader.readLine()); //BLOCKING!
		System.out.println("Iniciando servidor!");
		
		// Temos que especificar a porta porque o cliente vai se conectar nela
		DatagramSocket serverSocket = new DatagramSocket(porta);
		
		AliveTimer at = new AliveTimer();
		at.start();
		
		while (true) {
			
			// Declaração do buffer de recebimento
			byte[] recBuffer = new byte[1024];
			
			// Inicializar o pacote onde será recebido
			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			
			// Receber o pacote no recPkt
			printDebug("Esperando alguma mensagem");
			serverSocket.receive(recPkt); // BLOCKING
			
			IdentificarPacote idPkt = new IdentificarPacote(serverSocket, recPkt);
			idPkt.start();
		}

	}
	
	private static void mostrarPeers() {
		
		printDebug("\nPeers registrados:");
		
		for (Map.Entry<String, String> e : listaPeers.entrySet()) {
			printDebug(e.getKey() + "\nArquivos: " + e.getValue());
		}
		
		printDebug("");
	}
	
	
	static class AliveTimer extends Thread {
		
		public void run() {
			try {
				while (true) {
					
					TimeUnit.SECONDS.sleep(25);
					
					vivos = new Hashtable<>();
					
					// Enviar o ALIVE para todos os peers
					for (Map.Entry<String, DatagramPacket> e : listaPktPeers.entrySet()) {
						Alive alive = new Alive(e.getValue());
						alive.start();
					}
					
					// Aguardar 5 segundos para os peers poderem responder
					TimeUnit.SECONDS.sleep(5);
					
					// Verificar se foi recebido um ALIVE_OK do peer
					printDebug("Lista de vivos:");
					if (debug) {
						System.out.println(vivos);
					}
					
					List<String> eliminados = new ArrayList<>();
					
					// Fazer uma lista de quais peers não responderam
					for (Map.Entry<String, DatagramPacket> e : listaPktPeers.entrySet()) {
						String key = e.getKey();
						
						if (vivos.get(key) == null) {
							 System.out.println(key + " morto. Eliminando seus arquivos " + listaPeers.get(key));
							 eliminados.add(key);
						}
					}
					
					// Eliminar os peers que não responderam, evitando concorrência
					for (String key : eliminados) {
						listaPeers.remove(key);
						listaPktPeers.remove(key);
					}

				}
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	static class Alive extends Thread {
		
		DatagramPacket pkt;
		
		public Alive(DatagramPacket p) {
			pkt = p;
		}
		
		public void run() {
			// Endereço de IP do peer que receberá o datagram
			try {
				// Declaração e preenchimento do buffer de envio
				byte[] sendBuffer = new byte[1024];
				
				String[] argumentos = {"ALIVE"};
				
				Mensagem msg = new Mensagem(argumentos);
				
				sendBuffer = Mensagem.codificarUDP(msg);
				
				// Descobrir o endereço de IP e porta do cliente pelo pacote recebido
				InetAddress IPAddress = pkt.getAddress();
				int port = pkt.getPort();
				
				printDebug("\nENVIANDO ALIVE\nIP: " + IPAddress.toString()  + "\nPort: " + port);
				
				DatagramPacket sendPkt = new DatagramPacket(sendBuffer, sendBuffer.length, IPAddress, port);
				
				DatagramSocket serverSocket = new DatagramSocket();
				serverSocket.send(sendPkt);
				
				serverSocket.close();				
				
			} catch (Exception e) {
				printDebug(e);
			}
        }
	}
	
	
	
	// Classe para identificar o tipo de requisição que o servidor está recebendo
	static class IdentificarPacote extends Thread {
		
		DatagramPacket recPkt;
		DatagramSocket serverSocket;
		
		public IdentificarPacote(DatagramSocket ss, DatagramPacket pkt) {
			serverSocket = ss;
			recPkt = pkt;
		}
		
		public void run() {
			printDebug("Pacote recebido, identificando...");
			
			Mensagem mensagem = Mensagem.decodificarUDP(recPkt);
			
			switch (mensagem.getTipo()) {
			
	        	case "JOIN":
					JoinPeer joinPeer = new JoinPeer(serverSocket, recPkt, mensagem);
					joinPeer.start();
					break;
					
	        	case "LEAVE":
					LeavePeer leavePeer = new LeavePeer(serverSocket, recPkt, mensagem);
					leavePeer.start();
					break;
					
	        	case "SEARCH":
	        		SearchPeer searchPeer = new SearchPeer(serverSocket, recPkt, mensagem);
					searchPeer.start();
					break;
					
	        	case "UPDATE":
	        		UpdatePeer updatePeer = new UpdatePeer(serverSocket, recPkt, mensagem);
	        		updatePeer.start();
	        		break;
	        		
	        	case "ALIVE_OK":
	        		String key = mensagem.getIp() + ":" + mensagem.getPortaTCP();
	        		vivos.put(key, key);
	        		break;
					
        		default:
        			printDebug("Pacote não identificado: " + mensagem.getTipo());
			}
			
			
		}
	}
	
	static class JoinPeer extends Thread{
		
		DatagramSocket serverSocket;
		DatagramPacket recPkt;
		Mensagem mensagem;
		
		public JoinPeer(DatagramSocket ss, DatagramPacket pkt, Mensagem msg) {
			serverSocket = ss;
			recPkt = pkt;
			mensagem = msg;
		}
		
		public void run() {
			
			String key = mensagem.getIp() + ":" + mensagem.getPortaTCP();
			String peer = listaPeers.get(key);
			
			if (peer != null) {
				printDebug("Peer já cadastrado!");
				return;
			}
			
			listaPeers.put(key, mensagem.getListaArquivos());
			listaPktPeers.put(key, recPkt);
			
			printDebug("Peer registrado");
			mostrarPeers();
			String[] argumentos = {"JOIN_OK"};
			System.out.println("Peer " + mensagem.getIp() + ":" +
			 mensagem.getPortaTCP() +  " adicionado com arquivos " + mensagem.getListaArquivos());
			
			try {
				enviarCallbackUDP(serverSocket, recPkt, argumentos);
				printDebug("JOIN_OK enviado");
				printDebug("IP: " + recPkt.getAddress());
				printDebug("PORTA UDP: " + recPkt.getPort());
			}
			catch(Exception e) {
				printDebug("Erro ao enviar LEAVE_OK: " + e);
			}
		}
	}
	
	
	static class LeavePeer extends Thread{
		
		DatagramSocket serverSocket;
		DatagramPacket recPkt;
		Mensagem mensagem;
		
		public LeavePeer(DatagramSocket ss, DatagramPacket pkt, Mensagem msg) {
			serverSocket = ss;
			recPkt = pkt;
			mensagem = msg;
		}
		
		public void run() {
			
			String key = mensagem.getIp() + ":" + mensagem.getPortaTCP();
			
			listaPeers.remove(key);
			listaPktPeers.remove(key);
			
			printDebug("Peer removido");
			mostrarPeers();
			
			String[] argumentos = {"LEAVE_OK"};
			
			try {
				enviarCallbackUDP(serverSocket, recPkt, argumentos);
				printDebug("LEAVE_OK enviado");
				printDebug("IP: " + recPkt.getAddress());
				printDebug("PORTA UDP: " + recPkt.getPort());
			}
			catch(Exception e) {
				printDebug("Erro ao enviar LEAVE_OK: " + e);
			}
			
		}
	}
	
	
	static class UpdatePeer extends Thread {
		
		DatagramSocket serverSocket;
		DatagramPacket recPkt;
		Mensagem mensagem;
		
		public UpdatePeer(DatagramSocket ss, DatagramPacket pkt, Mensagem msg) {
			serverSocket = ss;
			recPkt = pkt;
			mensagem = msg;
		}
		
		public void run() {
			
			String key = mensagem.getIp() + ":" + mensagem.getPortaTCP();
			
			String arquivoUpdate = mensagem.getArquivoUpdate();
			
			// Selecionar o peer com o ip e porta identificado
			String arquivosNovos = listaPeers.get(key) + arquivoUpdate + "/";
			
			listaPeers.put(key, arquivosNovos);
			
			printDebug("Peer atualizado");
			mostrarPeers();
			
			String[] argumentos = {"UPDATE_OK"};
			
			try {
				enviarCallbackUDP(serverSocket, recPkt, argumentos);
				printDebug("UPDATE_OK enviado");
				printDebug("IP: " + recPkt.getAddress());
				printDebug("PORTA UDP: " + recPkt.getPort());
			}
			catch(Exception e) {
				printDebug("Erro ao enviar UPDATE_OK: " + e);
			}
		}
	}
	
	
	static class SearchPeer extends Thread{
		DatagramSocket serverSocket;
		DatagramPacket recPkt;
		Mensagem mensagem;
		
		public SearchPeer(DatagramSocket ss, DatagramPacket pkt, Mensagem msg) {
			serverSocket = ss;
			recPkt = pkt;
			mensagem = msg;
		}
		
		public void run() {
			String arquivoProcurado = mensagem.getArquivoProcurado();
			System.out.println("Peer " + mensagem.getIp() + ":" + mensagem.getPortaTCP() + " solicitou arquivo " + arquivoProcurado);
			String resultadoSearch = "";
			
			// Varrer todos os arquivos de todos os peers procurando a string com o nome
			for (Map.Entry<String, String> e : listaPeers.entrySet()) {
				
				String[] listaArquivos = e.getValue().split("/");
				
				for(String arquivo : listaArquivos){
					if(arquivo.equals(arquivoProcurado)){
						resultadoSearch = resultadoSearch + e.getKey() + "/";
				        break;
				    }
				}
			}
			
			// Enviar para a classe de mensagem para converter em uma lista e enviar somente a lista
			String[] argumentos = {
					"SEARCH_OK",
					resultadoSearch
			};
			
			try {
				enviarCallbackUDP(serverSocket, recPkt, argumentos);
				printDebug("SEARCH_OK enviado");
				printDebug("IP: " + recPkt.getAddress());
				printDebug("PORTA UDP: " + recPkt.getPort());
			}
			catch(Exception e) {
				printDebug("Erro ao enviar SEARCH_OK: " + e);
			}
			
		}
	}
	
	
	private static void enviarCallbackUDP(DatagramSocket serverSocket, DatagramPacket recPkt, String[] argumentos) throws Exception {
		// Declaração e preenchimento do buffer de envio
		byte[] sendBuffer = new byte[1024];
		
		Mensagem msg = new Mensagem(argumentos);
		
		sendBuffer = Mensagem.codificarUDP(msg);
		
		// Descobrir o endereço de IP e porta do cliente pelo pacote recebido
		InetAddress IPAddress = recPkt.getAddress();
		int port = recPkt.getPort();
		
		DatagramPacket sendPkt = new DatagramPacket(sendBuffer, sendBuffer.length, IPAddress, port);
		
		serverSocket.send(sendPkt);
		
	}
	
	private static void printDebug(String msg) {
		if (debug) {
			System.out.println(msg);
		}
	}
	
	private static void printDebug(Exception e) {
		if (debug) {
			System.out.println(e);
		}
	}
	

}
