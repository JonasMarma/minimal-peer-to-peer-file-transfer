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
import java.util.concurrent.TimeUnit;

public class Peer {
	
	private static boolean debug = false;
	
	private static String ipLocal;
	private static String portLocal;
	private static String pasta;
	
	private static String listaArquivos;
	
	private static DatagramSocket aliveSocket;
	private static DatagramSocket UDPsocket;
	
	private static int portaServerUDP = 10098;
	private static String ipServer = "127.0.0.1";
	
	private static List<String> resultadoSearch;
	private static String arquivoDownload;
	
	private static Boolean executando = true;
	private static Boolean logado = false;
	
	private static int TAM_PACOTE = 100000;
	private static int TIMEOUT = 5000;
	
	public static void main(String[] args) throws Exception {
		
		// Peer possui as seguintes threads:
		
		// Thread principal, para processar o menu
		// Threads de recebimento de pacotes UDP
		// Thread que lê pacotes TCP
		// Thread para administrar downloads
		// Subthreads de envio e recebimento
		
		while (executando) {
			
			if (!logado) {
				// Fechar a conexão
				if (aliveSocket != null && !aliveSocket.isClosed()) {
					printDebug("Fechando aliveSocket");
					aliveSocket.close();
				}
				if (UDPsocket != null && !UDPsocket.isClosed()) {
					printDebug("Fechando UDPsocket");
					UDPsocket.close();
				}
			}
			
			System.out.println("Escolha um comando: JOIN, SEARCH, DOWNLOAD, LEAVE");
			BufferedReader keyboardReader = new BufferedReader(new InputStreamReader(System.in));
			
			String[] comando = keyboardReader.readLine().split(" ");
			
			switch (comando[0]) {
			
	        	case "JOIN":
	        		
	        		if (logado) {
	        			printDebug("Já conectado ao servidor!");
	        			break;
	        		}
	        		
					lerInfos();
					lerListaArquivos();
					
					// Socket UDP para receber ALIVE
					aliveSocket = new DatagramSocket();
					
					Boolean joinOk = false;
					
					while (joinOk == false) {
						
						requisitarJoin();
						
						joinOk = aguardarJoinOk();
						
						if (joinOk == false) {
							printDebug("ERRO DE CONEXÃO - REENVIANDO JOIN");
						}
					}
					
					AliveOk aliveOk = new AliveOk();
					aliveOk.start();
					
					logado = true;
					
					// Abrir a porta TCP informada ao servidor para enviar downloads
					OuvirTCP threadTCP = new OuvirTCP();
					threadTCP.start();
					
					System.out.println("Sou peer " + ipLocal + ":" + portLocal + " com arquivos " + listaArquivos.replace('/',' '));
					
					break;
	        	
	        	case "LEAVE":
	        		
	        		if (!logado) {
	        			printDebug("Não conectado!");
	        			break;
	        		}
	        		
	    			requisitarLeave();
	    			
					Boolean leaveOk = false;
					
					while (leaveOk == false) {
						
						requisitarLeave();
						
						leaveOk = aguardarLeaveOk();
						
						if (leaveOk == false) {
							printDebug("ERRO DE CONEXÃO - REENVIANDO LEAVE");
						}
					}
	    			
    				printDebug("Saída permitida");
    				logado = false;
    				
	    			break;
				
	        	case "SEARCH":
	        		
	        		if (!logado) {
	        			printDebug("Não conectado, envie um JOIN");
	        			break;
	        		}
	        		
	        		System.out.println("Informe o nome do arquivo:");
	        		arquivoDownload = keyboardReader.readLine();
	        		requisitarSearch();
	        		
	        		resultadoSearch = null;
					
					while (resultadoSearch == null) {
						
						resultadoSearch = aguardarResultadoSearch();
						
						if (resultadoSearch == null) {
							printDebug("ERRO DE CONEXÃO - REENVIANDO SEARCH");
						}
					}
					
	        		System.out.println("Peers com arquivo solicitado:");
	        		for (String peer : resultadoSearch) {
	        			System.out.println(peer);
	        		}
	        		
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
	        		printDebug("Comando não identificado: " + comando[0]);
			}
		}
		
	}
	
	static class AliveOk extends Thread{
		public void run() {
			
			while (logado) {
				printDebug("Começando a ouvir ALIVE");
			
				// Receber datagrama do servidor
				// Buffer de recebimento
				byte[] recBuffer = new byte[1024];
				DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
				
		        try {
		        	
		        	if (!aliveSocket.isClosed()) {
		        		aliveSocket.receive(recPkt); //BLOCKING
		        		
			    		Mensagem msg = Mensagem.decodificarUDP(recPkt);
			    		
			    		if (msg.getTipo().equals("ALIVE")) {
			    			printDebug("ALIVE RECEBIDO!");
			    			
			    			String[] argumentos = {"ALIVE_OK",
							    					ipLocal,
							    					portLocal};
			    			
			    			enviarUDP(aliveSocket, argumentos);
			    			
			    		}
			    		else if (msg.getTipo().equals("LEAVE_OK")) {
			    			printDebug("Recebido LEAVE_OK dentro da THREAD Alive. Encerrando a thread!");
			    			break;
			    		}
		        	}
		        }
		        catch (Exception e) {
		        	printDebug(e);
		        }
		        
			}
			printDebug("Parando de ouvir ALIVE");
		}
	}
	
	static class FazerDownload extends Thread{
		
		String ipDownload;
		String portDownload;
		String arquivoDownload;
		
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
					printDebug("Não foi possível acessar o peer");
				}
				
				/* Lógica para controlar o envio de novas tentativas de download
				 * A cada iteração do while são enviadas requisições para todos os peers na lista, exceto o último solicitado
				 * Ao final, caso nenhum peet aceite o download, espera-se 5 segundos
				 * */
				else if (msg.getTipo().equals("DOWNLOAD_NEGADO")) {
					
					String ipDownloadAntigo = ipDownload;
					String portDownloadAntigo = portDownload;
					
					while (msg.getTipo().equals("DOWNLOAD_NEGADO")) {
						
		        		for (String peer : resultadoSearch) {
		        			String[] ipPort = peer.split(":");
		        			ipDownload = ipPort[0];
		        			portDownload = ipPort[1];
		        			
		        			if (!peer.equals(ipDownloadAntigo + ":" + portDownloadAntigo)) {
								System.out.println("peer " + ipDownloadAntigo + ":" +portDownloadAntigo + " negou o download, pedindo agora para o peer " + ipDownload + ":" + portDownload);
								s = new Socket(ipDownload, Integer.parseInt(portDownload));
								requisitarDownload(ipDownload, portDownload, s);
								msg = receberTCP(s);
								
								ipDownloadAntigo = ipDownload;
								portDownloadAntigo = portDownload;
								
								if (!msg.getTipo().equals("DOWNLOAD_NEGADO")) {
									break;
								}
		        			}
		        		}
		        		
		        		TimeUnit.SECONDS.sleep(5);

					}
					
				}
				
				if (msg.getTipo().equals("DOWNLOAD_ACEITO")) {
					
					byte[] buffer = new byte[TAM_PACOTE];
					
					long tamArquivo;
					long restante;
					printDebug("O peer aceitou transferir o arquivo");
					tamArquivo = msg.getTamanhoArquivo();
					
					// TODO: e quando está continuando um download já iniciado?
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
							printDebug("ERRO DE CONEXÃO - REENVIANDO UPDATE");
						}
					}
					
					printDebug("UPDATE_OK recebido!");
					
					System.out.println("Arquivo " + arquivoDownload + " baixado com sucesso na pasta " + pasta);
					
				}
				else {
					printDebug("Mensagem inesperada:");
					printDebug(msg.getTipo());
				}
				
				// Finalizar conexão
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
					// Aguardar uma conexão
					printDebug("Aguardando conexão TCP");
					Socket welcomeSocket = serverSocket.accept(); // BLOCKING
					
					// Criar thread para leitura e escrita
					printDebug("Conexão TCP recebida! Criando thread de atendimento:");
					ThreadAtendimento thread = new ThreadAtendimento(welcomeSocket);
					thread.start();
				}
				
				// TODO: Ao fazer LEAVE, parece que não está chegando aqui!
				serverSocket.close();
				
			} catch (Exception e) {
				printDebug(e);
			}
			
		}
	}
	
	
	// Atender a uma requisição TCP específica
	static class ThreadAtendimento extends Thread {

		private Socket no;
		
		public ThreadAtendimento(Socket welcomeSocket) {
			no = welcomeSocket;
		}
		
		public void run() {
			try {
				printDebug("Iniciando thread de atendimento ao TCP");
				// Cria a cadeia de entrada (leitura) de informações do socket
				InputStreamReader is = new InputStreamReader(no.getInputStream());
				BufferedReader readerTCP = new BufferedReader(is);
				
				printDebug("Iniciando a leitura do pacote TCP");
				// Ler do socket (cliente envia informação primeiro)
				String dados = readerTCP.readLine();
				
				Mensagem msg = Mensagem.decodificarTCP(dados);
				
				switch (msg.getTipo()) {
					case "DOWNLOAD":
						
						printDebug("\nMensagem recebida por TCP: \ntipo: " + msg.getTipo() + "\nArquivo: " + msg.getArquivoDownload() + "\n");
						
						// Cria cadeia de saída
						OutputStream os = no.getOutputStream();
						DataOutputStream writer = new DataOutputStream(os);
						
						// Permitir ou negar o download aleatóriamente
						double x = Math.random();
						boolean permitir;
						
						String path = pasta + "/" + msg.getArquivoDownload();
						
				        File f = new File(path);
				        
				        boolean existe = f.exists();
						
						if (x > 0.5 && existe) {
							permitir = true;
						}
						else {
							permitir = false;
						}
						
						if (permitir) {
							
							long fileSize = f.length();
														
							String[] args = {"DOWNLOAD_ACEITO",
											String.valueOf(fileSize)};
					        
							Mensagem resposta = new Mensagem(args);
							
							printDebug("Enviando pacote TCP\nTipo: " + resposta.getTipo());
							
							String sendData = Mensagem.codificarTCP(resposta);
							
							writer.writeBytes(sendData + "\n");
							
							FileInputStream fis = new FileInputStream(path);
							
							byte[] buffer = new byte[TAM_PACOTE];
							
							// Stream para enviar o arquivo
							DataOutputStream dos = new DataOutputStream(os);
							
							// LOOP DE ENVIO
							while (fis.read(buffer) > 0) {
								dos.write(buffer);
							}
							
							printDebug("ENVIO FINALIZADO!");
							
							fis.close();
						}
						else {
							
							String[] argumentos = {"DOWNLOAD_NEGADO"};
							Mensagem resposta = new Mensagem(argumentos);
							
							String sendData = Mensagem.codificarTCP(resposta);
							
							printDebug("Enviando requisição por TCP");
							writer.writeBytes(sendData + "\n");
							
							
							os.close();
							is.close();
							no.close();
							
							printDebug("DOWNLOAD_NEGADO enviado");
						}
						
						break;
						
					default:
						printDebug("Não foi possível identificar o pacote TCP!");
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
		System.out.println("Insira a pasta: ");
		pasta = reader.readLine(); //BLOCKING!
		
	}
	
	// Método que lê os arquivos na pasta e atualiza a lista de arquivos que o peer possui
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
				listaArquivos,
		};
		
		aliveSocket = new DatagramSocket();
		enviarUDP(aliveSocket, argumentos);
		
		printDebug("JOIN enviado");
	}
	
	private static boolean aguardarJoinOk() throws Exception {		
		
		printDebug("Aguardando JOIN_OK...");
		
		Mensagem msgResposta = aguardarCallbackUDP(aliveSocket);
		
		// Remover o timeout para não ter timeout esperando o alive
		aliveSocket.setSoTimeout(0);
		
		if (msgResposta != null) {
			if (msgResposta.getTipo().equals("JOIN_OK")) {
				printDebug("JOIN_OK recebido");
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
		
		enviarUDP(aliveSocket, argumentos);
		
		printDebug("LEAVE enviado");
	}
	
	private static boolean aguardarLeaveOk() throws Exception {		
		
		printDebug("Aguardando LEAVE_OK...");
		Mensagem msgResposta = aguardarCallbackUDP(aliveSocket);
		
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
				arquivoDownload,
				ipLocal,
				portLocal
		};
		
		UDPsocket = new DatagramSocket();
		enviarUDP(UDPsocket, argumentos);
		
		printDebug("SEARCH enviado");
	}
	
	// TODO: Poderia ser melhor se mandasse logo todos os arquivos que tem
	// Só para manter o sistema mais robusto....
	private static void requisitarUpdate(String arquivo) throws Exception {		
		String[] argumentos = {
				"UPDATE",
				ipLocal,
				portLocal,
				arquivo,
		};
		
		UDPsocket = new DatagramSocket();
		enviarUDP(UDPsocket, argumentos);
		
		printDebug("UPDATE enviado");
	}
	
	private static boolean aguardarUpdateOk() throws Exception {
		
		printDebug("Aguardando UPDATE_OK...");
		
		//UDPsocket = new DatagramSocket();
		Mensagem msgResposta = aguardarCallbackUDP(UDPsocket);
		
		if (msgResposta != null) {
			if (msgResposta.getTipo().equals("UPDATE_OK")) {
				UDPsocket.close();
				return true;
			}
		}
		UDPsocket.close();
		return false;
		
	}
	
	private static List<String> aguardarResultadoSearch() throws Exception {
		printDebug("Aguardando resultado do SEARCH...");
		
		//UDPsocket = new DatagramSocket();
		Mensagem msgResposta = aguardarCallbackUDP(UDPsocket);
		
		if (msgResposta != null) {
			UDPsocket.close();
			return msgResposta.getResultadoSearch();
		}
		UDPsocket.close();
		return null;
	}
	
	private static void requisitarDownload(String ipDestino, String portDestino, Socket s) throws Exception {
		
		String[] argumentos = {
				"DOWNLOAD",
				arquivoDownload
		};
		
		enviarTCP(ipDestino, portDestino, s, argumentos);
		
		printDebug("Requisição DOWNLOAD enviado");
	}
	
	private static void enviarUDP(DatagramSocket ds, String[] argumentos) throws Exception {
		// Endereço de IP do servidor que receberá o datagram
		InetAddress IPAddress = InetAddress.getByName(ipServer);
		
		// Array de bytes a ser enviada
		byte[] sendData = new byte[1024];
		
		Mensagem msg = new Mensagem(argumentos);
		
		sendData = Mensagem.codificarUDP(msg);
		
		// Criação de um datagrama com endereço e porta do host remoto 10098
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, portaServerUDP);
				
		// Enviar o datagrama
		ds.send(sendPacket);
	}
	
	private static void enviarTCP(String ipDestino, String portDestino, Socket s, String[] argumentos) throws Exception {
		// Tenta criar uma conexão com o host "remoto" 127.0.0.1 na porta 9000
		// O socket s tem uma porta designada pelo sistema operacional entre 1024 e 65535
		
		// Cria a cadeia de saída (escrita) de informações para o socket
		OutputStream os = s.getOutputStream();
		DataOutputStream writer = new DataOutputStream(os);
		
		Mensagem msg = new Mensagem(argumentos);
		String sendData = Mensagem.codificarTCP(msg);
		
		printDebug("Enviando requisição por TCP");
		writer.writeBytes(sendData + "\n");	
	}
	
	public static Mensagem receberTCP(Socket s) throws Exception {
		InputStreamReader is = new InputStreamReader(s.getInputStream());
		BufferedReader readerTCP = new BufferedReader(is);
		
		// Esperar até receber um pacote ou timeout
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
	 * Aguardar uma resposta UDP. Se a resposta não vir dentro do tempo de timeout, retornar null
	 */
	private static Mensagem aguardarCallbackUDP(DatagramSocket ds) throws Exception {
		// Receber datagrama do servidor
		
		// Buffer de recebimento
		byte[] recBuffer = new byte[1024];
		DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
		
		// Esperar até receber um pacote ou timeout
		ds.setSoTimeout(TIMEOUT);
		
        try {
        	ds.receive(recPkt); //BLOCKING
        	printDebug("Callback UPD recebido");
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
