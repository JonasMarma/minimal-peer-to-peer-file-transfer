package projetosd;

import java.net.DatagramPacket;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;

public class Mensagem {
	// Tipo da mensagem (JOIN, LEAVE, DOWNLOAD, SEARCH...)
	private String tipo;
	
	// Relacionados à requisição JOIN
	private String ip;
	private String portaTCP;
	private String listaArquivos;
	
	// Relacionados à requisição SEARCH
	private String arquivoProcurado;
	private List<String> resultadoSearch;
	
	// Relacionado à requisição de Download
	private String arquivoDownload;
	private long tamanhoArquivo;
	private byte[] parteArquivo;
	
	public Mensagem(String[] argumentos) {
		
		tipo = argumentos[0];
		
		switch (tipo) {
	    	case "JOIN":
	    		ip = argumentos[1];
	    		portaTCP = argumentos[2];
	    		listaArquivos = argumentos[3];
	    		break;
	    		
	    	case "LEAVE":
	    		ip = argumentos[1];
	    		portaTCP = argumentos[2];
	    		break;
	    		
	    	case "SEARCH":
	    		arquivoProcurado = argumentos[1];
	    		break;
	    		
	    	case "SEARCH_OK":
	    		tipo = null;
	    		resultadoSearch = Arrays.asList(argumentos[1].split("/"));
	    		break;
	    		
	    	case "DOWNLOAD":
	    		arquivoDownload = argumentos[1];
	    		break;
	    		
	    	case "DOWNLOAD_ACEITO":
	    		tamanhoArquivo = Long.parseLong(argumentos[1]);
				break;
	    		
	    	case "DADO":
	    		parteArquivo = argumentos[1].getBytes();
	    		break;
	    	
    		// Mensagens só com o tipo (exemplos: JOIN_OK, LEAVE_OK, etc...)
	    	default:
	    		;
		}
    		
		//tipo = tp;
		//ip = end_ip;
		//portaTCP = Integer.parseInt(port);
		//listaArquivos = lista;
	}
	
	public static byte[] codificarUDP(Mensagem msg) {
		Gson gson = new Gson();
		String json = gson.toJson(msg);
		return json.getBytes();
	}
	
	public static String codificarTCP(Mensagem msg) {
		Gson gson = new Gson();
		return gson.toJson(msg);
	}
	
	public static Mensagem decodificarUDP(DatagramPacket pkt) {
		String dados = new String(pkt.getData(),
							      pkt.getOffset(),
								  pkt.getLength());
		
		Gson gson = new Gson();
		
		Mensagem msg = gson.fromJson(dados, Mensagem.class);
		
		return msg;
	}
	
	public static Mensagem decodificarTCP(String dados) {
		
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
	
	public String getArquivoProcurado() {
		return arquivoProcurado;
	}
	
	public List<String> getResultadoSearch() {
		return resultadoSearch;
	}
	
	public String getArquivoDownload() {
		return arquivoDownload;
	}
	
	public byte[] getParteArquivo() {
		return parteArquivo;
	}
	
	public long getTamanhoArquivo() {
		return tamanhoArquivo;
	}
}
