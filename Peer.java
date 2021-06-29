package projetosd;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.google.gson.Gson;

import projetosd.Servidor.JoinPeer;
import projetosd.Servidor.LeavePeer;
import projetosd.Servidor.SearchPeer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Peer {

	private static String ipLocal;
	private static String portLocal;
	private static String pasta;
	
	private static String listaArquivos;
	
	private static DatagramSocket clientSocket;
	
	private static int portaServerUDP = 10098;
	private static String ipServer = "127.0.0.1";
	
	private static String arquivoDownload;
	private static String ipDownload;
	private static String portDownload;
	
	private static Boolean executando = true;
	private static Boolean logado = false;
	
	public static void main(String[] args) throws Exception {
		
		// Peer possui as seguintes threads:
		
		// Thread principal, para processar o menu
		// Threads de recebimento de pacotes UDP
		// Thread que l� pacotes TCP
			// Subthreads de envio e recebimento
		
		while (executando) {
			
			System.out.println("\nEscolha um comando: JOIN, SEARCH, DOWNLOAD, LEAVE");
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
					
					// TODO: Timer!
					Boolean joinOk = aguardarJoinOk();
					
					if (joinOk) {
						logado = true;
						
						// Abrir a porta TCP informada ao servidor para enviar downloads
						OuvirTCP threadTCP = new OuvirTCP();
						threadTCP.start();
						
						System.out.println("Conectado ao servidor");
					} else {
						System.out.println("N�o foi poss�vel se conectar");
					}
					
					break;
	        	
	        	case "LEAVE":
	        		
	        		if (!logado) {
	        			System.out.println("N�o conectado!");
	        			break;
	        		}
	        		
	    			requisitarLeave();
	    			
	    			// TODO: Timer!
	    			Boolean leaveOk = aguardarLeaveOk();
	    			
	    			if (leaveOk == true) {
	    				System.out.println("Sa�da permitida");
	    				logado = false;
	    			} else {
	    				System.out.println("Sa�da n�o permitida");
	    			}
	    			break;
				
	        	case "SEARCH":
	        		
	        		if (!logado) {
	        			System.out.println("N�o conectado!");
	        			break;
	        		}
	        		
	        		System.out.println("Informe o nome do arquivo:");
	        		arquivoDownload = reader.readLine();
	        		requisitarSearch();
	        		
	        		// TODO: Timer
	        		List<String> resultadoSearch = aguardarResultadoSearch();
	        		
	        		System.out.println("Peers com arquivo solicitado:");
	        		System.out.println(resultadoSearch);
	        		break;
	        	
	        	case "DOWNLOAD":
	        		System.out.println("Informe IP do peer:");
	        		ipDownload = reader.readLine();
	        		
	        		System.out.println("Informe a porta do peer:");
	        		portDownload = reader.readLine();
	        		
	        		requisitarDownload();
	    		
	        	default:
	    			System.out.println("Comando n�o identificado!");
			}
		}
		
		// Fechar a conex�o
		clientSocket.close();
		
	}
	
	// Thread para aguardar entradas de pacotes TCP
	static class OuvirTCP extends Thread{
		
		public void run() {
			
			ServerSocket serverSocket;
			try {
				
				// Iniciar a porta especificada para receber pacotes TCP
				serverSocket = new ServerSocket(Integer.parseInt(portLocal));
				
				while (logado) {
					// Aguardar uma conex�o
					System.out.println("Aguardando conex�o TCP");
					Socket welcomeSocket = serverSocket.accept(); // BLOCKING
					
					// Criar thread para leitura e escrita
					System.out.println("Conex�o TCP recebida!");
					
					ThreadAtendimento thread = new ThreadAtendimento(welcomeSocket);
					thread.start();
				}
				
				serverSocket.close();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	// Atender a uma requisi��o TCP espec�fica
	static class ThreadAtendimento extends Thread {

		private Socket no;
		
		public ThreadAtendimento(Socket welcomeSocket) {
			no = welcomeSocket;
		}
		
		public void run() {
			try {
			// Cria a cadeia de entrada de informa��es do socket
			InputStreamReader is = new InputStreamReader(no.getInputStream());
			BufferedReader reader = new BufferedReader(is);

			// Cria cadeia de sa�da
			OutputStream os = no.getOutputStream();
			DataOutputStream writer = new DataOutputStream(os);
			
			// Ler do socket (cliente envia informa��o primeiro)
			String texto = reader.readLine();
			
			// Processar a informa��o
			System.out.println("Arquivo requisitado: " + texto);
			
			// Enviar arquivo solicitado para o cliente
			writer.writeBytes("DOWNLOAD_NEGADO");
			
			} catch (Exception e) {
				
			}
		}
	}
	
	private static void lerInfos() throws IOException {
		// Ler do teclado IP, porta e pasta
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		
		// Ler IP
		System.out.println("Insira o IP: ");
		ipLocal = reader.readLine(); //BLOCKING!
		
		// Ler porta
		System.out.println("Insira a porta TCP: ");
		portLocal = reader.readLine(); //BLOCKING!
		
		// Ler pasta
		//TODO Essa � uma pasta de debug:
		System.out.println("Insira a pasta: ");
		String dir = reader.readLine(); //BLOCKING!
		pasta = "C:/temp/" + dir;
		
	}
	
	// M�todo que l� os arquivos na pasta e atualiza a lista de arquivos que o peer possui
	private static void lerListaArquivos() {
		
		listaArquivos = "";
		
        File f = new File(pasta);
        
        String[] flist = f.list();
        
        if (flist == null) {
        	return;
        }
        
        for (String nome : flist){
        	listaArquivos = listaArquivos + nome + "/";
    	}

	}
	
	
	private static void requisitarJoin() throws Exception {
		
		String[] argumentos = {
				"JOIN",
				ipLocal,
				portLocal,
				listaArquivos
		};
		
		enviarUDP(argumentos);
		
		System.out.println("JOIN enviado");
	}
	
	private static boolean aguardarJoinOk() throws Exception {		
		
		System.out.println("Aguardando JOIN_OK...");
		
		Mensagem msgResposta = aguardarCallbackUDP();
		
		if (msgResposta.getTipo().equals("JOIN_OK")) {
			return true;
		}
		return false;
		
	}
	
	
	private static void requisitarLeave() throws Exception {
		
		String[] argumentos = {
				"LEAVE",
				ipLocal,
				portLocal
		};
		
		enviarUDP(argumentos);
		
		System.out.println("LEAVE enviado");
	}
	
	private static boolean aguardarLeaveOk() throws Exception {		
		
		System.out.println("Aguardando LEAVE_OK...");
		Mensagem msgResposta = aguardarCallbackUDP();
		
		if (msgResposta.getTipo().equals("LEAVE_OK")) {
			return true;
		}
		return false;
	}
	
	private static void requisitarSearch() throws Exception {
		
		String[] argumentos = {
				"SEARCH",
				arquivoDownload
		};
		
		enviarUDP(argumentos);
		
		System.out.println("SEARCH enviado");
	}
	
	private static List<String> aguardarResultadoSearch() throws Exception {		
		System.out.println("Aguardando resultado do SEARCH...");
		
		Mensagem msgResposta = aguardarCallbackUDP();
		
		return msgResposta.getResultadoSearch();
	}
	
	private static void requisitarDownload() throws Exception {
		
		String[] argumentos = {
				"DOWNLOAD",
				arquivoDownload
		};
		
		enviarTCP(argumentos);
		
		System.out.println("Requisi��o DOWNLOAD enviado");
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
	
	// TODO: terminar aqui
	private static void enviarTCP(String[] argumentos) throws Exception {
		// Tenta criar uma conex�o com o host "remoto" 127.0.0.1 na porta 9000
		// O socket s tem uma porta designada pelo sistema operacional entre 1024 e 65535
		Socket s = new Socket(ipDownload, Integer.parseInt(portDownload));
		
		// Cria a cadeia de sa�da (escrita) de informa��es para o socket
		OutputStream os = s.getOutputStream();
		DataOutputStream writer = new DataOutputStream(os);
	
		// Cria a cadeia de entrada (leitura) de informa��es do socket
		InputStreamReader is = new InputStreamReader(s.getInputStream());
		BufferedReader reader = new BufferedReader(is);
		
		// TODO: passar a utilizar a classe Mensagem para o envio TCP
		String texto = argumentos[1];
		
		// Oq acontece se tirar o \n ?
		//writer.writeBytes(texto + '\n');
		writer.writeBytes(texto);
		
		String response = reader.readLine(); // BLOCKING!
		System.out.println("Recebido do Servidor: " + response);
		// Finalizar comex�o
		s.close();
	}
	
	private static Mensagem aguardarCallbackUDP() throws Exception {
		// Receber datagrama do servidor
		
		// Buffer de recebimento
		byte[] recBuffer = new byte[1024];
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		// Esperar at� receber um pacote
		// Recebe o pacote de qualquer um que envie um send para a porta!
		clientSocket.receive(recPkt); //BLOCKING
		
		// Transformar informa��o do pacote recebido
		return Mensagem.decodificar(recPkt);
		
	}
	

}
