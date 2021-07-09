package projetosd;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Map;

public class Servidor {
	
	private static boolean debug = true;
	
	private static int porta = 10098;
	
	// O servidor possui:
	// Thread principal (ouvir UDP)
	// Thread principal chama thread de indentificar pacotes UDP
	// Thread de indentificar pacotes UDP pode chamar threads de de processamento
	
	//static ArrayList<String> listaPeers = new ArrayList<String>();
	static Hashtable<String, String> listaPeers = new Hashtable<>();
	
	public static void main(String[] args) throws Exception {

		// Temos que especificar a porta porque o cliente vai se conectar nela
		DatagramSocket serverSocket = new DatagramSocket(porta);
		
		//TODO: continuar o ALIVE
		//Alive alive = new Alive();
		//alive.start();
		
		while (true) {
			
			// Declaração do buffer de recebimento
			byte[] recBuffer = new byte[1024];
			
			// Inicializar o pacote onde será recebido
			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			
			// Receber o pacote no recPkt
			printDebug("\n Esperando alguma mensagem");
			serverSocket.receive(recPkt); // BLOCKING
			
			IdentificarPacote idPkt = new IdentificarPacote(serverSocket, recPkt);
			idPkt.start();
		}

	}
	
	private static void mostrarPeers() {
		
		printDebug("\nPeers registrados:");
		
		for (Map.Entry<String, String> e : listaPeers.entrySet()) {
			printDebug("IP/PORT: " + e.getKey() + "/" + e.getValue());
		}
		
		printDebug("");
	}
	
	/*
	// Classe para identificar o tipo de requisição que o servidor está recebendo
	static class Alive extends Thread {
		
		public Alive(DatagramSocket ss, DatagramPacket pkt) {
			serverSocket = ss;
			recPkt = pkt;
		}
		
		public void run() {
			// Endereço de IP do servidor que receberá o datagram
			InetAddress IPAddress = InetAddress.getByName(ipServer);
			
			//Array de bytes a ser enviada
			byte[] sendData = new byte[1024];
			
			Mensagem msg = new Mensagem(argumentos);
			
			sendData = Mensagem.codificarUDP(msg);
			
			// Criação de um datagrama com endereço e porta do host remoto 10098
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, portaServerUDP);
					
			// Enviar o datagrama
			clientSocket.send(sendPacket);
		}
	}
	*/
	
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
					
        		default:
        			printDebug("Pacote não identificado!");
			}
			
			
		}
	}
	
	static class JoinPeer extends Thread{
		
		// TODO: NÃO PERMITIR QUE UM PEER SE COENCTE COM IP E PORTA REPETIDO!!!!
		
		DatagramSocket serverSocket;
		DatagramPacket recPkt;
		Mensagem mensagem;
		
		public JoinPeer(DatagramSocket ss, DatagramPacket pkt, Mensagem msg) {
			serverSocket = ss;
			recPkt = pkt;
			mensagem = msg;
		}
		
		public void run() {
			
			String key = mensagem.getIp() + "/" + mensagem.getPortaTCP();
			
			listaPeers.put(key, mensagem.getListaArquivos());
			
			printDebug("Peer registrado");
			mostrarPeers();
			
			String[] argumentos = {"JOIN_OK"};
			
			System.out.println("Peer " + mensagem.getIp() + ":" + mensagem.getPortaTCP() +  "adicionado com arquivos " + mensagem.getListaArquivos());
			
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
	
	// TODO: Duplicação de LEAVE (só tentar remover, se n achar continuar mandando o leave ok)
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
			
			String key = mensagem.getIp() + "/" + mensagem.getPortaTCP();
			
			listaPeers.remove(key);
			
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
	
	// TODO: duplicação de update (vai ficar 2 arquivos, mas n sei se é problema...)
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
			
			String key = mensagem.getIp() + "/" + mensagem.getPortaTCP();
			
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
	
	// TODO: e se o mesmo peer solicita SEARCH duas vezes?
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
			
			System.out.println("Peer " + "IP" + ":" + "porta" + " solicitou arquivo" + arquivoProcurado);
			
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
				printDebug("Erro ao enviar LEAVE_OK: " + e);
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
	

}
