package josh.dnsspoof;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import burp.IBurpExtenderCallbacks;
import josh.utils.SharedBoolean;
import josh.utils.events.DNSEvent;
import josh.utils.events.DNSTableEvent;
import josh.utils.events.DNSTableEventListener;
import josh.utils.events.UDPEventListener;
//import jpcap.JpcapCaptor;
//import jpcap.NetworkInterface;
//import jpcap.NetworkInterfaceAddress;

public class UDPListener implements Runnable{
	private DatagramSocket datagramSocket = null;
	private boolean stop = false;
	private boolean isDown = false;
	public static String[] ADDRESS;
	private static int InterfaceNumber=0;
	private int port=5351;
	public IBurpExtenderCallbacks Callbacks; 
	private SharedBoolean sb;
	

	private static void updateInterface(){
		String path = System.getProperty("user.home");
		
		File f = new File(path + "/.dnsExtender/dns.properties");
		Properties config = new Properties();
		try{
			if(f.exists()){
				config.load( new FileInputStream(f));
			}else{
				File p = new File(path + "/.dnsExtender/");
				
				if(!p.exists())
					p.mkdir();
				f.createNewFile();
				config.load(ClassLoader.getSystemResourceAsStream("dns.properties"));
				config.store(new FileOutputStream(f), null);
			}
			
			InterfaceNumber=Integer.parseInt(config.getProperty("interface", "0"));
		}catch(Exception EX){
			//Callbacks.printError(EX.getMessage());
			EX.printStackTrace();
		}
	}
	private static void updateInterface(String iface){
		String path = System.getProperty("user.home");
		File f = new File(path + "/.dnsExtender/dns.properties");
		Properties config = new Properties();
		try{
			if(f.exists()){
				config.load( new FileInputStream(f));
			}else{
				File p = new File(path + "/.dnsExtender/");
				
				if(!p.exists())
					p.mkdir();
				f.createNewFile();
				config.load(ClassLoader.getSystemResourceAsStream("dns.properties"));
				config.store(new FileOutputStream(f), null);
			}
			config.setProperty("interface", iface);
			config.store(new FileOutputStream(f), null);
			//InterfaceNumber=Integer.parseInt(config.getProperty("interface", "0"));
		}catch(Exception EX){
			//Callbacks.printError(EX.getMessage());
			EX.printStackTrace();
		}
	}
	
	public UDPListener(int port, SharedBoolean sb){
		this.port = port;
		this.sb = sb;
		
		/*try 
        {
            datagramSocket = new DatagramSocket(port);
        } catch (SocketException e) { e.printStackTrace(); }  */
		
	}
	public void StopServer(){
		stop = true;
	}
	public boolean isStopped(){
		return isDown;
	}

	@Override
	public void run() {

		stop = false;
		isDown = false;
		updateInterface();
		if(ADDRESS != null && !ADDRESS[0].equals("---"))
			Callbacks.printOutput("DNSMiTM: Responding IP Address is " + ADDRESS[0] + "." + ADDRESS[1] + "." +ADDRESS[2] + "." +ADDRESS[3]  );
		else{ 
			System.out.println("Could not start dns");
			this.fireEvent();
			return;
		}
		try 
        {
			Callbacks.printOutput("Using port: " + this.port);
            datagramSocket = new DatagramSocket(this.port);
           
            
        } catch (SocketException e) { 
        	Callbacks.printError(e.getMessage());
        	e.printStackTrace(); 
        } 
		
        udpWhile : while(!stop)
        {
        	byte[] buffer = new byte[1024];
        	DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            // Receive the packet.
            try {
            	datagramSocket.setSoTimeout(100);
                datagramSocket.receive(packet);
            } catch(SocketTimeoutException ex){
            	continue udpWhile;
            }catch (IOException e) {Callbacks.printError(e.getMessage()); e.printStackTrace(); }
            
           
            buffer = packet.getData();  
            int NN = packet.getLength();
            byte []copy = new byte [NN];
            for(int j =0; j < NN; j++){
            	copy[j] = buffer[j];
            }
            // Print the data:
            //System.out.println(new String(copy));
            String hostname = "";
            for(int k=12; k < copy.length;){
            	if(copy[k] == 0){
            		hostname = hostname.substring(0, hostname.length()-1); //removes the last period
            		break;
            	}
            	int limit = k+copy[k++] +1;
            	for(;k < limit && k < copy.length; k++)
            		hostname += "" + (char)copy[k];
            	hostname += ".";

            }
            
            String ip = packet.getAddress().getHostAddress();
            String HostName="";

        	java.net.InetAddress inetAdd;
			try {
				inetAdd = java.net.InetAddress.getByName(ip);
			
				HostName = inetAdd.getHostName();
			} catch (UnknownHostException e1) {
				Callbacks.printError(e1.getMessage());
				e1.printStackTrace();
			} 
           
            
            
           


            int N = 1024;
            byte [] dnsResp = new byte[N];
            int i=0;
            //dns transaction ID
            dnsResp[i++] = copy[0]; 
            dnsResp[i++] = copy[1];
            //OpCodes
            dnsResp[i++] = (byte)0x81;
            dnsResp[i++] = (byte)0x80;
            
            dnsResp[i++] = copy[4];
            dnsResp[i++] = copy[5];
            dnsResp[i++] = copy[4];
            dnsResp[i++] = copy[5];
            
            dnsResp[i++] = 0;
            dnsResp[i++] = 0;
            dnsResp[i++] = 0;
            dnsResp[i++] = 0;
            
            for(int j=12; j< copy.length; j++){
            	dnsResp[i++] = copy[j];
            }
            dnsResp[i++] = (byte) 0xc0;
            dnsResp[i++] = (byte) 0x0c;
            dnsResp[i++] = 0;
            dnsResp[i++] = 1;
            dnsResp[i++] = 0;
            dnsResp[i++] = 1;
            dnsResp[i++] = 0;
            dnsResp[i++] = 0;
            dnsResp[i++] = 0;
            dnsResp[i++] = (byte) 0x3c;
            dnsResp[i++] = 0;
            dnsResp[i++] = 4;
            
            List<String>hosts=this.readHosts();
            Boolean override=false;
            String returnIP = this.ADDRESS[0] + "." + this.ADDRESS[1] + "." +this.ADDRESS[2] + "." +this.ADDRESS[3];
            for(String line : hosts){
            	if(line.contains(hostname) && !line.startsWith("#")){
            		String hostIP = line.split(" ")[0];
            		if(hostIP.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")){
            			returnIP = hostIP;
            			String [] hostIPOcts = hostIP.split("\\.");
            			dnsResp[i++] = (byte)Long.parseLong(hostIPOcts[0]);
        				dnsResp[i++] = (byte)Long.parseLong(hostIPOcts[1]);
        				dnsResp[i++] = (byte)Long.parseLong(hostIPOcts[2]);
        				dnsResp[i++] = (byte)Long.parseLong(hostIPOcts[3]);
        				override=true;
        				break;
            		}
            	}
            }
            
            
            if(this.ADDRESS != null && !override && sb.getDefault()){
				//System.out.println("DNS Request for: " + hostname + " from " + ip + " set to " + this.ADDRESS[0] +"."+this.ADDRESS[1]+"."+this.ADDRESS[2]+"."+this.ADDRESS[3] );
				dnsResp[i++] = (byte)Long.parseLong(this.ADDRESS[0]);
				dnsResp[i++] = (byte)Long.parseLong(this.ADDRESS[1]);
				dnsResp[i++] = (byte)Long.parseLong(this.ADDRESS[2]);
				dnsResp[i++] = (byte)Long.parseLong(this.ADDRESS[3]);	
			}else{
				try {
					InetAddress address = InetAddress.getByName(hostname);
					byte[] octs = address.getAddress();
					dnsResp[i++] = octs[0];
					dnsResp[i++] = octs[1];
					dnsResp[i++] = octs[2];
					dnsResp[i++] = octs[3];
					returnIP = octs[0] +"." + octs[1] +"." + octs[2] +"." + octs[3];
					
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
//				NetworkInterface[] devices = JpcapCaptor.getDeviceList();
//				NetworkInterfaceAddress[] nia = devices[InterfaceNumber].addresses;
//				InetAddress addr=null;
//				for(NetworkInterfaceAddress inet : nia){
//					addr = inet.address;
//					if(addr.getAddress().length == 4)
//						break;
//				}
//				if(addr.getAddress()[0]==127 && addr.getAddress()[1]==0){
//					System.out.println("Local host addresses not allowed.");
//					System.exit(0);
//				}
//				//System.out.println("DNS Request for: " + hostname + " from " + ip + " set to " + addr.getHostAddress() );
//				dnsResp[i++] = addr.getAddress()[0];
//				dnsResp[i++] = addr.getAddress()[1];
//				dnsResp[i++] = addr.getAddress()[2];
//				dnsResp[i++] = addr.getAddress()[3];
			}
         /*dnsResp[i++] = (byte)192;
         dnsResp[i++] = (byte)168;
         dnsResp[i++] = (byte)1;
         dnsResp[i++] = (byte)132;*/
            this.fireTableEvent(hostname, ip, HostName, returnIP);
            
            N = i;
            byte [] ans = new byte[N];
            for(int j=0; j<N; j++){
            	ans[j] = dnsResp[j];
            }
            
            
            try {
            	InetAddress addr = packet.getAddress();
            	int port = packet.getPort();
            	//System.out.println(addr.getHostAddress() + ":" + port);
            	DatagramPacket updResp = new DatagramPacket(ans, ans.length, addr, port);
				datagramSocket.send(updResp);
				
				
			} catch (IOException e) {
				Callbacks.printError(e.getMessage());
				e.printStackTrace();
			} catch (Exception ex){
				Callbacks.printError(ex.getMessage());
				ex.printStackTrace();
			}
            
            
        }
        this.fireEvent();
        datagramSocket.close();		
		
	}
	
	private List<String> readHosts(){
		
		String fs =  System.getProperty("file.separator");
		String file = System.getProperty("user.dir") + fs + "hosts.txt";
		File f = new File(file);
		if(!f.exists()){
			return new ArrayList<String>();
		}
		Path p = Paths.get(file);
		List<String>lines = new ArrayList<String>();
		BufferedReader reader;
		try {
			reader = Files.newBufferedReader(p);
			
			
			String line = "";
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return new ArrayList<String>();
		}
		
		return lines;
	}
	
	// Event Stuff here
		private List<UDPEventListener> _udplisteners = new ArrayList<UDPEventListener>();
		private List<DNSTableEventListener> _dnslisteners = new ArrayList<DNSTableEventListener>();
		
		public synchronized void addEventListener(UDPEventListener listener)	{
			_udplisteners.add(listener);
		}
		public synchronized void removeEventListener(UDPEventListener listener)	{
			_udplisteners.remove(listener);
		}
		public synchronized void addTableEventListener(DNSTableEventListener listener)	{
			_dnslisteners.add(listener);
		}
		public synchronized void removeTableEventListener(DNSTableEventListener listener)	{
			_dnslisteners.remove(listener);
		}

		private synchronized void fireEvent()	{
			DNSEvent event = new DNSEvent(this);
			Iterator<UDPEventListener> i = _udplisteners.iterator();
			while(i.hasNext())	{
				i.next().UDPDown(event);
			}
		}
		
		
		
		private synchronized void fireTableEvent(String Domain, String ClientIP, String HostName, String ResponseIp)	{
			DNSTableEvent event = new DNSTableEvent(this);
			event.setClientIP(ClientIP);
			event.setDomain(Domain);
			event.setHostName(HostName);
			event.setResponseIp(ResponseIp);
			Iterator<DNSTableEventListener> i = _dnslisteners.iterator();
			while(i.hasNext())	{
				i.next().NewDomainRequest(event);
			}
		}
		public void setPort(int port){
			this.port = port;
		}

}
