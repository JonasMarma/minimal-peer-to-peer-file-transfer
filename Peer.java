package projetosd;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

public class Peer {
	
	private static boolean debug = true;

	private static String ipLocal;
	private static String portLocal;
	private static String pasta;
	
	private static String listaArquivos;
	
	private static DatagramSocket clientSocket;
	
	private static int portaServerUDP = 10098;
	private static String ipServer = "127.0.0.1";
	
	private static String arquivoDownload;
	
	private static Boolean executando = true;
	private static Boolean logado = false;
	
	private static int TAM_PACOTE = 100000;
	private static int TIMEOUT = 5000;
	
	public static void main(String[] args) throws Exception {
		
		// Peer possui as seguintes threads:
		
		// Thread principal, para processar o menu
		// Threads de recebimento de pacotes UDP
		// Thread que l� pacotes TCP
		// Thread para administrar downloads
		// Subthreads de envio e recebimento
		
		while (executando) {
			
			System.out.println("Escolha um comando: JOIN, SEARCH, DOWNLOAD, LEAVE");
			BufferedReader keyboardReader = new BufferedReader(new InputStreamReader(System.in));
			
			
			String[] comando = keyboardReader.readLine().split(" ");
			
						
			switch (comando[0]) {
			
	        	case "JOIN":
	        		
	        		if (logado) {
	        			printDebug("J� conectado ao servidor!");
	        			break;
	        		}
	        		
					lerInfos();
					lerListaArquivos();
					
					clientSocket = new DatagramSocket();
					
					Boolean joinOk = false;
					
					while (joinOk == false) {
						
						requisitarJoin();
						
						joinOk = aguardarJoinOk();
						
						if (joinOk == false) {
							printDebug("ERRO DE CONEX�O - REENVIANDO JOIN");
						}
					}
					
					logado = true;
					
					// Abrir a porta TCP informada ao servidor para enviar downloads
					OuvirTCP threadTCP = new OuvirTCP();
					threadTCP.start();
					
					System.out.println("Sou peer " + "[IP]" + ":" + "[porta]" + " com arquivos " + listaArquivos.replace('/',' '));
					
					break;
	        	
	        	case "LEAVE":
	        		
	        		if (!logado) {
	        			printDebug("N�o conectado!");
	        			break;
	        		}
	        		
	    			requisitarLeave();
	    			
	    			// TODO: Isso � bem coloc�vel dentro de um m�todo!!! S� ver o caso do search
					Boolean leaveOk = false;
					
					while (leaveOk == false) {
						
						requisitarLeave();
						
						joinOk = aguardarLeaveOk();
						
						if (joinOk == false) {
							printDebug("ERRO DE CONEX�O - REENVIANDO LEAVE");
						}
					}
	    			
    				printDebug("Sa�da permitida");
    				logado = false;
    				
	    			break;
				
	        	case "SEARCH":
	        		
	        		if (!logado) {
	        			printDebug("N�o conectado, envie um JOIN");
	        			break;
	        		}
	        		
	        		System.out.println("Informe o nome do arquivo:");
	        		arquivoDownload = keyboardReader.readLine();
	        		requisitarSearch();
	        		
	        		List<String> resultadoSearch = null;
					
					while (resultadoSearch == null) {
						
						resultadoSearch = aguardarResultadoSearch();
						
						if (resultadoSearch == null) {
							printDebug("ERRO DE CONEX�O - REENVIANDO SEARCH");
						}
					}
					
	        		System.out.println("Peers com arquivo solicitado:");
	        		System.out.println(resultadoSearch);
	        		break;
	        	
	        	case "DOWNLOAD":
	        		System.out.println("Informe IP do peer:");
	        		String ipDownload = keyboardReader.readLine();
	        		
	        		System.out.println("Informe a porta do peer:");
	        		String portDownload = keyboardReader.readLine();
	        		
	        		System.out.println("Informe o arquivo a ser baixado:");
	        		String arquivoDownload = keyboardReader.readLine();
	        		
	        		FazerDownload thread = new FazerDownload(ipDownload, portDownload, arquivoDownload);
	        		thread.run();
	        		
	        		break;
	        		
	        	default:
	        		printDebug("Comando n�o identificado: " + comando[0]);
			}
		}
		
		// Fechar a conex�o
		clientSocket.close();
		
	}
	
	static class FazerDownload extends Thread{
		
		String ipDownload;
		String portDownload;
		String arquivoDownlaod;
		
		public FazerDownload(String ip, String port, String arquivo) {
			ipDownload = ip;
			portDownload = port;
			arquivoDownload = arquivo;
		}
		
		public void run() {

			try {
				printDebug("Iniciando thread de download");
				
				Socket s = new Socket(ipDownload, Integer.parseInt(portDownload));
				
				requisitarDownload(ipDownload, portDownload, s);
				
				Mensagem msg = receberTCP(s);
				
				if (msg == null) {
					printDebug("N�o foi poss�vel acessar o peer");
				}
				
				// TODO: L�gica para pedir o download para outro peer
				else if (msg.getTipo().equals("DOWNLOAD_NEGADO")) {
					System.out.println("peer " + "[IP]:[porta]" + " negou o download, pedindo agora para o peer " + "[IP]:[porta]");
				}
				
				else if (msg.getTipo().equals("DOWNLOAD_ACEITO")) {
					
					byte[] buffer = new byte[TAM_PACOTE];
					
					long tamArquivo;
					long restante;
					
					printDebug("O peer aceitou transferir o arquivo");
					
					tamArquivo = msg.getTamanhoArquivo();
					
					// TODO: e quando est� continuando um download j� iniciado?
					restante = tamArquivo;
					
					printDebug("Tamanho do arquivo: " + String.valueOf(restante));
					
					// Stream de entrada de dados
					DataInputStream dis = new DataInputStream(s.getInputStream());
					// Stream para salvar o arquivo
					FileOutputStream fos = new FileOutputStream(pasta + "/" + arquivoDownload);
					int read = 0;
					int len = buffer.length;
					
					while (restante > 0) {
						
						printDebug("Aguardando novo pacote..");
						
						read = dis.read(buffer, 0, len);
						
						if (restante < Integer.MAX_VALUE) {
							// Atualizar o tamanho da leitura
							len = Math.min(buffer.length, (int)restante);
						}
						
						restante -= read;
						
						printDebug("Escrevendo no fos");
						fos.write(buffer, 0, read);

					}
					
					fos.close();
					
					printDebug("Fim do loop de download! Informando servidor...");
					
					Boolean updateOk = false;
					
					while (updateOk == false) {
						
						requisitarUpdate(arquivoDownload);
						
						updateOk = aguardarUpdateOk(); //BLOCKING
						
						if (updateOk == false) {
							printDebug("ERRO DE CONEX�O - REENVIANDO UPDATE");
						}
					}
					
					printDebug("UPDATE_OK recebido!");
					
					System.out.println("Arquivo " + arquivoDownload + " baixado com sucesso na pasta " + pasta);
					
				}
				else {
					printDebug("Mensagem inesperada:");
					printDebug(msg.getTipo());
				}
				
				// Finalizar conex�o
				s.close();
				
			} catch (Exception e) {
				printDebug("Erro ao baixar o arquivo:\n" + e);
			}

		}
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
					printDebug("Aguardando conex�o TCP");
					Socket welcomeSocket = serverSocket.accept(); // BLOCKING
					
					// Criar thread para leitura e escrita
					printDebug("Conex�o TCP recebida! Criando thread de atendimento:");
					ThreadAtendimento thread = new ThreadAtendimento(welcomeSocket);
					thread.start();
					
				}
				
				// TODO: Ao fazer LEAVE, parece que n�o est� chegando aqui!
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
				printDebug("Iniciando thread de atendimento ao TCP");
				// Cria a cadeia de entrada (leitura) de informa��es do socket
				InputStreamReader is = new InputStreamReader(no.getInputStream());
				BufferedReader readerTCP = new BufferedReader(is);
				
				printDebug("Iniciando a leitura do pacote TCP");
				// Ler do socket (cliente envia informa��o primeiro)
				String dados = readerTCP.readLine();
				
				Mensagem msg = Mensagem.decodificarTCP(dados);
				
				
				switch (msg.getTipo()) {
					case "DOWNLOAD":
						
						printDebug("\nMensagem recebida por TCP: \ntipo: " + msg.getTipo() + "\nArquivo: " + msg.getArquivoDownload() + "\n");
						
						// Cria cadeia de sa�da
						OutputStream os = no.getOutputStream();
						DataOutputStream writer = new DataOutputStream(os);
						
						boolean permitir = true;
						
						// TODO: Usar o m�todo enviarTCP() para fazer o envio dos dados!!!
						
						if (permitir) {
							
							String path = pasta + "/" + msg.getArquivoDownload();
							
					        File f = new File(path);
					        long fileSize = f.length();
							
							String[] args = {"DOWNLOAD_ACEITO",
											String.valueOf(fileSize)};
					        
							Mensagem resposta = new Mensagem(args);
							
							printDebug("Enviando pacote TCP\nTipo: " + resposta.getTipo());
							
							String sendData = Mensagem.codificarTCP(resposta);
							
							writer.writeBytes(sendData + "\n");
							
							FileInputStream fis = new FileInputStream(path);
							
							byte[] buffer = new byte[TAM_PACOTE];
							
							// Selecionar o outputstream do socket
							// OutputStream os = s.getOutputStream();
							// Stream para enviar o arquivo
							DataOutputStream dos = new DataOutputStream(os);
							
							while (fis.read(buffer) > 0) {
								dos.write(buffer);
							}
							
							printDebug("ENVIO FINALIZADO!");
							
							fis.close();
						}
						else {
														
							// Enviar informa��o para o cliente
							String[] argumentos = {"DOWNLOAD_NEGADO"};
							Mensagem resposta = new Mensagem(argumentos);
							
							String sendData = Mensagem.codificarTCP(resposta);
							
							printDebug("Enviando requisi��o por TCP");
							writer.writeBytes(sendData + "\n");
							
							
							os.close();
							is.close();
							no.close();
							
							printDebug("DOWNLOAD_NEGADO enviado");
						}
						
						break;
						
					default:
						printDebug("N�o foi poss�vel identificar o pacote TCP!");
				}
				
				
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
		
		printDebug("JOIN enviado");
	}
	
	private static boolean aguardarJoinOk() throws Exception {		
		
		printDebug("Aguardando JOIN_OK...");
		
		Mensagem msgResposta = aguardarCallbackUDP();
		
		if (msgResposta != null) {
			if (msgResposta.getTipo().equals("JOIN_OK")) {
				return true;
			}
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
		
		printDebug("LEAVE enviado");
	}
	
	private static boolean aguardarLeaveOk() throws Exception {		
		
		printDebug("Aguardando LEAVE_OK...");
		Mensagem msgResposta = aguardarCallbackUDP();
		
		if (msgResposta != null) {
			if (msgResposta.getTipo().equals("LEAVE_OK")) {
				return true;
			}
		}
		return false;
	}
	
	private static void requisitarSearch() throws Exception {
		
		String[] argumentos = {
				"SEARCH",
				arquivoDownload
		};
		
		enviarUDP(argumentos);
		
		printDebug("SEARCH enviado");
	}
	
	// TODO: Poderia ser melhor se mandasse logo todos os arquivos que tem
	// S� para manter o sistema mais robusto....
	private static void requisitarUpdate(String arquivo) throws Exception {		
		String[] argumentos = {
				"UPDATE",
				ipLocal,
				portLocal,
				arquivo
		};
		
		enviarUDP(argumentos);
		
		printDebug("UPDATE enviado");
	}
	
	private static boolean aguardarUpdateOk() throws Exception {
		
		printDebug("Aguardando UPDATE_OK...");
		
		Mensagem msgResposta = aguardarCallbackUDP();
		
		if (msgResposta != null) {
			if (msgResposta.getTipo().equals("UPDATE_OK")) {
				return true;
			}
		}
		return false;
		
	}
	
	private static List<String> aguardarResultadoSearch() throws Exception {
		printDebug("Aguardando resultado do SEARCH...");
		
		Mensagem msgResposta = aguardarCallbackUDP();
		
		if (msgResposta != null) {
			return msgResposta.getResultadoSearch();
		}
		return null;
	}
	
	private static void requisitarDownload(String ipDestino, String portDestino, Socket s) throws Exception {
		
		String[] argumentos = {
				"DOWNLOAD",
				arquivoDownload
		};
		
		enviarTCP(ipDestino, portDestino, s, argumentos);
		
		printDebug("Requisi��o DOWNLOAD enviado");
	}
	
	private static void enviarUDP(String[] argumentos) throws Exception {
		// Endere�o de IP do servidor que receber� o datagram
		InetAddress IPAddress = InetAddress.getByName(ipServer);
		
		// Array de bytes a ser enviada
		byte[] sendData = new byte[1024];
		
		Mensagem msg = new Mensagem(argumentos);
		
		sendData = Mensagem.codificarUDP(msg);
		
		// Cria��o de um datagrama com endere�o e porta do host remoto 10098
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, portaServerUDP);
				
		// Enviar o datagrama
		clientSocket.send(sendPacket);
	}
	
	private static void enviarTCP(String ipDestino, String portDestino, Socket s, String[] argumentos) throws Exception {
		// Tenta criar uma conex�o com o host "remoto" 127.0.0.1 na porta 9000
		// O socket s tem uma porta designada pelo sistema operacional entre 1024 e 65535
		
		// Cria a cadeia de sa�da (escrita) de informa��es para o socket
		OutputStream os = s.getOutputStream();
		DataOutputStream writer = new DataOutputStream(os);
				
		// TODO: passar a utilizar a classe Mensagem para o envio TCP
		//String texto = argumentos[1];
		
		Mensagem msg = new Mensagem(argumentos);
		String sendData = Mensagem.codificarTCP(msg);
		
		printDebug("Enviando requisi��o por TCP");
		writer.writeBytes(sendData + "\n");	
	}
	
	public static Mensagem receberTCP(Socket s) throws Exception {
		InputStreamReader is = new InputStreamReader(s.getInputStream());
		BufferedReader readerTCP = new BufferedReader(is);
		
		// Esperar at� receber um pacote ou timeout
		s.setSoTimeout(TIMEOUT);
		
		try {
			String dados = readerTCP.readLine(); // BLOCKING!
			Mensagem msg = Mensagem.decodificarTCP(dados);
			return msg;
		}
		catch (Exception e) {
			printDebug(e.toString());
		}
		
		return null;
		
	}
	
	/*
	 * Aguardar uma resposta UDP. Se a resposta n�o vir dentro do tempo de timeout, retornar null
	 */
	private static Mensagem aguardarCallbackUDP() throws Exception {
		// Receber datagrama do servidor
		
		// Buffer de recebimento
		byte[] recBuffer = new byte[1024];
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		// Esperar at� receber um pacote ou timeout
		clientSocket.setSoTimeout(TIMEOUT);
		
        try {
        	clientSocket.receive(recPkt); //BLOCKING
    		return Mensagem.decodificarUDP(recPkt);
        }
        catch (Exception e) {
        	printDebug(e);
        }
        
        return null;
		
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
