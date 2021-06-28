package projetosd;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Map;

import com.google.gson.Gson;

public class Servidor {
	
	private static int porta = 10098;
	
	// #############################################################################
	// #############################################################################
	// #############################################################################
	// #############################################################################
	// #############################################################################
	// OBSERVAÇÕES:
	// Minha ideia foi criar uma list aproveitando o objeto mensagem no formato json
	// (não estava conseguindo uma list de objetos Mensagem, mas vou tentar descobrir o porque, mas a ideia é a mesma)
	// Fiz isso porque o objeto já tem informação do IP, porta, arquivos que contém....
	
	//static ArrayList<String> listaPeers = new ArrayList<String>();
	static Hashtable<String[], String> listaPeers = new Hashtable<>();

	public static void main(String[] args) throws Exception {

		// Temos que especificar a porta porque o cliente vai se conectar nela
		DatagramSocket serverSocket = new DatagramSocket(porta);
		
		while (true) {
			
			// Declaração do buffer de recebimento
			byte[] recBuffer = new byte[1024];
			
			//Inicializar o pacote onde será recebido
			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			
			// Receber o pacote no recPkt
			System.out.println("\n Esperando alguma mensagem");
			serverSocket.receive(recPkt); // BLOCKING
			
			IdentificarPacote idPkt = new IdentificarPacote(serverSocket, recPkt);
			idPkt.start();
		}

	}
	
	private static void mostrarPeers() {
		
		System.out.println("Peers registrados:");
		
        // Iterating using enhanced for loop
		for (Map.Entry<String[], String> e : listaPeers.entrySet()) {
			System.out.println("IP: " + e.getKey()[0] + " ; PORT: " + e.getKey()[1] + " \n " + e.getValue() + "\n");
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
			System.out.println("Pacote recebido, identificando...");
			
			Mensagem mensagem = Mensagem.decodificar(recPkt);
			
			System.out.println("PORT: " + mensagem.getPortaTCP());
			
			switch (mensagem.getTipo()) {
	        	case "JOIN":
					JoinPeer joinPeer = new JoinPeer(serverSocket, recPkt, mensagem);
					joinPeer.start();
					break;
	        	case "LEAVE":
					LeavePeer leavePeer = new LeavePeer(serverSocket, recPkt, mensagem);
					leavePeer.start();
					break;
        		default:
        			System.out.println("Pacote não identificado!");
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
			
			String[] key = {
					mensagem.getIp(),
					mensagem.getPortaTCP()
			};
			
			listaPeers.put(key, mensagem.getListaArquivos());
			
			mostrarPeers();
			
			try {
				enviarCallbackUDP(serverSocket, recPkt, "JOIN_OK");
				System.out.println("JOIN_OK enviado");
				System.out.println("IP: " + recPkt.getAddress());
				System.out.println("PORTA UDP: " + recPkt.getPort());
			}
			catch(Exception e) {
				System.out.println("Erro ao enviar LEAVE_OK: " + e);
			}
			
		}
	}
	
	static class LeavePeer extends Thread{
		
		DatagramPacket recPkt;
		DatagramSocket serverSocket;
		Mensagem mensagem;
		
		public LeavePeer(DatagramSocket ss, DatagramPacket pkt, Mensagem msg) {
			serverSocket = ss;
			recPkt = pkt;
			mensagem = msg;
		}
		
		public void run() {
			
			String[] argumentos = {
					"DADO",
					mensagem.getIp(),
					mensagem.getPortaTCP(),
					mensagem.getListaArquivos()
			};
			
			Mensagem peer = new Mensagem(argumentos);
			
			Gson gson = new Gson();
			String peerJson = gson.toJson(peer);
			
			listaPeers.remove(peerJson);
			
			mostrarPeers();
			
			try {
				enviarCallbackUDP(serverSocket, recPkt, "LEAVE_OK");
				System.out.println("LEAVE_OK enviado");
				System.out.println("IP: " + recPkt.getAddress());
				System.out.println("PORTA UDP: " + recPkt.getPort());
			}
			catch(Exception e) {
				System.out.println("Erro ao enviar LEAVE_OK: " + e);
			}
			
		}
	}
	
	private static void enviarCallbackUDP(DatagramSocket serverSocket, DatagramPacket recPkt, String mensagem) throws Exception {
		// Declaração e preenchimento do buffer de envio
		byte[] sendBuffer = new byte[1024];
		
		String[] argumentos = {
				mensagem
		};
		
		Mensagem msg = new Mensagem(argumentos);
		
		sendBuffer = Mensagem.codificar(msg);
		
		// Descobrir o endereço de IP e porta do cliente pelo pacote recebido
		InetAddress IPAddress = recPkt.getAddress();
		int port = recPkt.getPort();
		
		DatagramPacket sendPkt = new DatagramPacket(sendBuffer, sendBuffer.length, IPAddress, port);
		
		serverSocket.send(sendPkt);
		
	}
	

}
