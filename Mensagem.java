package projetosd;

import java.net.DatagramPacket;

import com.google.gson.Gson;

public class Mensagem {
	// Tipo da mensagem (JOIN, LEAVE, DOWNLOAD, SEARCH...)
	private String tipo;
	
	// Relacionados � requisi��o JOIN
	private String ip;
	private String portaTCP;
	private String listaArquivos;
	
	// Relacionados � requisi��o 
	//private 
	
	// #############################################################################
	// #############################################################################
	// #############################################################################
	// #############################################################################
	// #############################################################################
	// OBSERVA��ES:
	// Quando criei a classe, n�o pensei que haveriam tantos argumentos
	// Minha ideia � posteriormente modificar o construtor para algo como:
	// Mensagem(String[] argumentos)
	// as informa��es ser�o preenchidas din�micamente no construtor, eventualmente deixando algumas com null
	// Creio que essa classe tamb�m ser� utilizada para TCP
	
	//public Mensagem(String tp, String end_ip, String port, String[] lista) {
	public Mensagem(String[] argumentos) {
		
		tipo = argumentos[0];
		
		switch (tipo) {
	    	case "JOIN":
	    		ip = argumentos[1];
	    		portaTCP = argumentos[2];
	    		listaArquivos = argumentos[3];
	    		
	    	case "LEAVE":
	    		ip = argumentos[1];
	    		portaTCP = argumentos[2];
	    	
    		// Mensagens s� com o tipo (exemplos: JOIN_OK, LEAVE_OK, etc...)
	    	default:
	    		;
		}
    		
		//tipo = tp;
		//ip = end_ip;
		//portaTCP = Integer.parseInt(port);
		//listaArquivos = lista;
	}
	
	public Mensagem() {
		
	}
	
	public static byte[] codificar(Mensagem msg) {
		Gson gson = new Gson();
		String json = gson.toJson(msg);
		return json.getBytes();
	}
	
	public static Mensagem decodificar(DatagramPacket pkt) {
		String dados = new String(pkt.getData(),
							      pkt.getOffset(),
								  pkt.getLength());
		
		Gson gson = new Gson();
		
		Mensagem msg = gson.fromJson(dados, Mensagem.class);
		
		return msg;
	}
	
	public String getTipo() {
		return tipo;
	}
	
	public String getListaArquivos() {
		return listaArquivos;
	}
	
	public String getIp() {
		return ip;
	}
	
	public String getPortaTCP() {
		return String.valueOf(portaTCP);
	}
}
