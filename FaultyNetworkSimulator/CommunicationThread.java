package FaultyNetworkSimulator;


import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
 
public class CommunicationThread extends Thread {
	private int serverPort;
	private int clientPort;
	private int chanceToDrop=75;
    protected DatagramSocket socket = null;
    protected BufferedReader in = null;
 
    public CommunicationThread(int inPort, int servPort, int percent) throws IOException {
    	System.out.println("STARTED THREAD");
    	serverPort=servPort;
    	socket = new DatagramSocket(inPort);
    	chanceToDrop=percent;
    }
 
    public void run() {
    	Random rand=new Random();
    	int randomNum;
    	int dstPort;
    	boolean firstPacket=true;;
    	while(true){
    		try {
    			ByteBuffer buff = ByteBuffer.allocate(1024);
                // receive request
                DatagramPacket packet = new DatagramPacket(buff.array(),buff.array().length);
                socket.receive(packet);   
               
                InetAddress address = packet.getAddress();
                if(firstPacket){
                	clientPort=packet.getPort();
                	firstPacket=false;
                }
                if(packet.getPort()==clientPort){
                	dstPort=serverPort;
                }else{
                	dstPort=clientPort;
                }
                randomNum=rand.nextInt(100);
                if(randomNum>=(chanceToDrop))
                {
                	//System.out.println("got packet : " + packet.getLength() +" from " + packet.getPort() + " sending to " + dstPort);
                	packet = new DatagramPacket(buff.array(), packet.getLength(), address, dstPort);
                	socket.send(packet);     
                }
    		}catch(Exception e){
    			System.err.println("ERROR : " + e);
    			
    		}
    	}
    }
 
}