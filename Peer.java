package projetosd;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import com.google.gson.Gson;

import projetosd.Servidor.JoinPeer;
import projetosd.Servidor.LeavePeer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Peer {

	private static String ip;
	private static String portaTCP;
	private static String pasta;
	
	private static String listaArquivos;
	
	private static DatagramSocket clientSocket;
	
	private static int portaServerUDP = 10098;
	private static String ipServer = "127.0.0.1";
	
	public static void main(String[] args) throws Exception {

		Boolean executando = true;
		Boolean logado = false;
		
		// Loop do menu principal
		while (executando) {
			
			System.out.println("\n Escolha um comando: JOIN, SEARCH, DOWNLOAD, LEAVE (?????)");
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			
			String[] comando = reader.readLine().split(" ");
						
			switch (comando[0]) {
			
	        	case "JOIN":
	        		
	        		if (logado) {
	        			System.out.println("J� conectado ao servidor");
	        			break;
	        		}
	        		
					lerInfos();
					lerListaArquivos();
					
					clientSocket = new DatagramSocket();
					
					requisitarJoin();
					
					// TODO: Implementar time out para enviar outra requisi��o
					Boolean joinOk = aguardarJoinOk();
					
					logado = true;
					
					System.out.println("Conectado ao servidor");
					
					break;
	        	
	        	case "LEAVE":
	    			requisitarLeave();
	    			
	    			Boolean leaveOk = aguardarLeaveOk();
	    			
	    			if (leaveOk == true) {
	    				System.out.println("Sa�da permitida");
	    				executando = false;
	    				logado = false;
	    			} else {
	    				System.out.println("Sa�da n�o permitida");
	    			}
					
					break;
					
	        	case "SEARCH":
	        		System.out.println("Informe o nome do arquivo:");
	        		String arquivo = reader.readLine();
	        		requisitarSearch(arquivo);
	    		
	        	default:
	    			System.out.println("Comando n�o identificado!");
			}	
		}
		
		// Fechar a conex�o
		clientSocket.close();
		
	}
	
	private static void lerInfos() throws IOException {
		// Ler do teclado IP, porta e pasta
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// Ler IP
		// D�VIDA: Estou assumindo que se for sempre feita uma execu��o local, o IP ser� 127.0.0.1
		// Sendo assim, esse endere�o fica salvo para uma poss�vel implementa��o com v�rias m�quinas
		// Mas no projeto, s� ser� usado o 127.0.0.1
		// Estou correto?
		System.out.println("Insira o IP: ");
		ip = reader.readLine();
		
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// Ler porta
		// D�VIDA: que porta � essa? � mesmo para receber TCP?
		// Deve ser TCP. n�o �? No UDP a porta � aleat�ria...
		System.out.println("Insira a porta TCP: ");
		portaTCP = reader.readLine();
		
		// Ler pasta
		//TODO Essa � uma pasta de debug:
		System.out.println("Insira a pasta: ");
		//pasta = reader.readLine();
		pasta = "C:/temp/peer1";
		
	}
	
	// M�todo que l� os arquivos na pasta e atualiza a lista de arquivos que o peer possui
	private static void lerListaArquivos() {
		
        File f = new File(pasta);
        
        String[] flist = f.list();
        
        for (String nome : flist){
        	listaArquivos = listaArquivos + nome + "/";
    	}

	}
	
	
	private static void requisitarJoin() throws Exception {
		
		String[] argumentos = {
				"JOIN",
				ip,
				portaTCP,
				listaArquivos
		};
		
		enviarUDP(argumentos);
		
		System.out.println("JOIN enviado");
	}
	
	private static boolean aguardarJoinOk() throws Exception {		
		
		System.out.println("Aguardando LEAVE_OK...");
		return aguardarCallbackUDP("JOIN_OK");
		
	}
	
	
	private static void requisitarLeave() throws Exception {
		
		String[] argumentos = {
				"LEAVE",
				ip,
				portaTCP
		};
		
		enviarUDP(argumentos);
		
		System.out.println("LEAVE enviado");
	}
	
	private static boolean aguardarLeaveOk() throws Exception {		
		
		System.out.println("Aguardando LEAVE_OK...");
		return aguardarCallbackUDP("LEAVE_OK");
	}
	
	
	private static void requisitarSearch(String arquivo) throws Exception {
		
		String[] argumentos = {
				"SEARCH"
		};
		
		enviarUDP(argumentos);
		
		System.out.println("SEARCH enviado");
	}
	
	private static boolean aguardarSearch() throws Exception {		
		
		System.out.println("Aguardando LEAVE_OK...");
		return aguardarCallbackUDP("LEAVE_OK");
	}
	
	
	private static void enviarUDP(String[] argumentos) throws Exception {
		// Endere�o de IP do servidor que receber� o datagram
		InetAddress IPAddress = InetAddress.getByName(ipServer);
		
		//Array de bytes a ser enviada
		byte[] sendData = new byte[1024];
		
		Mensagem msg = new Mensagem(argumentos);
		
		sendData = Mensagem.codificar(msg);
		
		// Cria��o de um datagrama com endere�o e porta do host remoto 10098
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, portaServerUDP);
				
		// Enviar o datagrama
		clientSocket.send(sendPacket);
		
	}
	
	private static boolean aguardarCallbackUDP(String callback) throws Exception {
		// Receber datagrama do servidor
		
		// Buffer de recebimento
		byte[] recBuffer = new byte[1024];
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		// Esperar at� receber um pacote
		// Recebe o pacote de qualquer um que envie um send para a porta!
		clientSocket.receive(recPkt); //BLOCKING
		
		// Transformar informa��o do pacote recebido
		Mensagem msg = Mensagem.decodificar(recPkt);
		
		if (msg.getTipo().equals(callback)) {
			return true;
		}
		
		return false;
	}
	

}
