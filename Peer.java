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
	        			System.out.println("Já conectado ao servidor");
	        			break;
	        		}
	        		
					lerInfos();
					lerListaArquivos();
					
					clientSocket = new DatagramSocket();
					
					requisitarJoin();
					
					// TODO: Implementar time out para enviar outra requisição
					Boolean joinOk = aguardarJoinOk();
					
					logado = true;
					
					System.out.println("Conectado ao servidor");
					
					break;
	        	
	        	case "LEAVE":
	    			requisitarLeave();
	    			
	    			Boolean leaveOk = aguardarLeaveOk();
	    			
	    			if (leaveOk == true) {
	    				System.out.println("Saída permitida");
	    				executando = false;
	    				logado = false;
	    			} else {
	    				System.out.println("Saída não permitida");
	    			}
					
					break;
					
	        	case "SEARCH":
	        		System.out.println("Informe o nome do arquivo:");
	        		String arquivo = reader.readLine();
	        		requisitarSearch(arquivo);
	    		
	        	default:
	    			System.out.println("Comando não identificado!");
			}	
		}
		
		// Fechar a conexão
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
		// DÚVIDA: Estou assumindo que se for sempre feita uma execução local, o IP será 127.0.0.1
		// Sendo assim, esse endereço fica salvo para uma possível implementação com várias máquinas
		// Mas no projeto, só será usado o 127.0.0.1
		// Estou correto?
		System.out.println("Insira o IP: ");
		ip = reader.readLine();
		
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// #############################################################################
		// Ler porta
		// DÚVIDA: que porta é essa? É mesmo para receber TCP?
		// Deve ser TCP. não é? No UDP a porta é aleatória...
		System.out.println("Insira a porta TCP: ");
		portaTCP = reader.readLine();
		
		// Ler pasta
		//TODO Essa é uma pasta de debug:
		System.out.println("Insira a pasta: ");
		//pasta = reader.readLine();
		pasta = "C:/temp/peer1";
		
	}
	
	// Método que lê os arquivos na pasta e atualiza a lista de arquivos que o peer possui
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
		// Endereço de IP do servidor que receberá o datagram
		InetAddress IPAddress = InetAddress.getByName(ipServer);
		
		//Array de bytes a ser enviada
		byte[] sendData = new byte[1024];
		
		Mensagem msg = new Mensagem(argumentos);
		
		sendData = Mensagem.codificar(msg);
		
		// Criação de um datagrama com endereço e porta do host remoto 10098
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, portaServerUDP);
				
		// Enviar o datagrama
		clientSocket.send(sendPacket);
		
	}
	
	private static boolean aguardarCallbackUDP(String callback) throws Exception {
		// Receber datagrama do servidor
		
		// Buffer de recebimento
		byte[] recBuffer = new byte[1024];
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		// Esperar até receber um pacote
		// Recebe o pacote de qualquer um que envie um send para a porta!
		clientSocket.receive(recPkt); //BLOCKING
		
		// Transformar informação do pacote recebido
		Mensagem msg = Mensagem.decodificar(recPkt);
		
		if (msg.getTipo().equals(callback)) {
			return true;
		}
		
		return false;
	}
	

}
