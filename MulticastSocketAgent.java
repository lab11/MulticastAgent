import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Hashtable;
import javax.xml.bind.DatatypeConverter;

//import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * This class sends and receives multicast messages, offering
 * all-to-all communications between this and other active agents on a
 * multicast network (that could span anything from a single broadcast
 * domain to an Internet-wide network of hosts).
 *
 * Messages are transmitted between agents using multicast UDP
 * datagrams.  The contents of a UDP datagram are the SHA-256 hash of
 * the topic name (32 octets) followed by the contents of the message.
 *
 * To use this class to implement your own application: (1) extend it
 * (e.g. <code>MyAgent extends MulticastSocketAgent {...}</code>) and
 * override (i.e. implement) the <code>recv</code> method to do
 * whatever you would like your app to do when it receives data.
 *
 * @author Prabal Dutta <prabal@eecs.umich.edu>
 */
public class MulticastSocketAgent implements Runnable {
    DatagramSocket sock;
    Hashtable<String, String> topics;
    InetAddress addr;
    int port;
    boolean debug = false;

    /**
     * Creates a new multicast agent on the local host.  Note that
     * multiple instances of this class can coexist on a single host
     * since the underlying socket is a multicast socket that can be
     * shared by multiple clients.
     * @param addr The multicast address.
     * @param port The multicast port.
     */
    public MulticastSocketAgent(String addr, int port) {
        try {
            this.addr = InetAddress.getByName(addr);
	    this.port = port;
            this.sock = new DatagramSocket();
	    this.topics = new Hashtable<String,String>();
	    this.debug = true;
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }

    /**
     * Implements the Runnable interface and consumes a thread in a
     * while(true) loop that blocks on a datagram packet reception.  A
     * packet reception causes this method to call <code>recv</code>
     * with the appropriate parameters.
     */
   public void run() {
	System.out.println("**Starting listener thread**");
	byte[] buf = new byte[1024];
	try {
	    MulticastSocket ms = new MulticastSocket(this.port);
	    ms.joinGroup(this.addr);
	    while (true) {
		DatagramPacket pkt = new DatagramPacket(buf, buf.length);
		ms.receive(pkt);
		if (pkt.getLength() > 32) {
		    byte[] mbuf = pkt.getData();
		    byte[] hash = new byte[32];
		    byte[] data = new byte[pkt.getLength()-32];
		    System.arraycopy(mbuf, 0, hash, 0, 32);
		    System.arraycopy(mbuf, 32, data, 0, pkt.getLength()-32);
		    String msg = new String(mbuf, 32, pkt.getLength()-32);
		    recv(this.topics.get(toHexString(hash)), data, data.length);

		}
		else {
		    // Received a runt packet
		}
	    }
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }

    /**
     * This should 
     */
    public void stop() {
    }


    /**
     * Returns the SHA-256 hash of the topic name as a byte array of size 32.
     * 
     * @param topic the topic name.
     */
    public byte[] hashTopic(String topic) {
	byte[] hash = new byte[0];
	try {
	    MessageDigest md = MessageDigest.getInstance("SHA-256");
	    md.update(topic.getBytes("UTF-8"));
	    hash = md.digest();
	}
	catch(Exception e) {
	    e.printStackTrace();
	}
	return hash;
    }

    /**
     * Utility function for converting a byte array to a hex string
     * with all-caps and no spaces.
     *
     * @param bytes the byte array to convert into a hex string.
     */
    public String toHexString(byte[] bytes) {
	return javax.xml.bind.DatatypeConverter.printHexBinary(bytes);
     }

    /**
     * Registers interest in a topic with the local agent.
     * Registration is required to receive messages sent to this
     * topic.
     *
     * @param topic the string name of a topic to which this agent
     *              should listen.
     */
    public void register(String topic) {
	try {
	    byte[] digest = hashTopic(topic);
	    this.topics.put(topic, toHexString(digest));
	    this.topics.put(toHexString(digest), topic);
	    if (this.debug) {
		System.out.println("REG:" + toHexString(digest) + ":" + topic);
	    }
	}
	catch(Exception e) {
	    e.printStackTrace();
	}
    }

    /**
     * Call to unregister a topic.  Unregistration is required to stop
     * receiving messages sent to the this topic.
     *
     * @param topic the topic to unregister.
     */
    public void unregister(String topic) {
	byte[] digest = hashTopic(topic);
	this.topics.remove(topic);
	this.topics.remove(toHexString(digest));
    }

    /**
     * Call to send the data to all agents interested in the topic.
     *
     * @param topic the topic to which this data will be sent.
     * @param data the message to send.
     */
    public void send(String topic, String data) {
	try {
	    byte[] hash = hashTopic(topic);
	    byte[] mbuf = new byte[hash.length+data.getBytes().length];
	    System.arraycopy(hash, 0, mbuf, 0, hash.length);
	    System.arraycopy(data.getBytes(), 0, mbuf, hash.length, 
			     data.getBytes().length);
	    DatagramPacket pkt = new DatagramPacket(mbuf, mbuf.length,
						    this.addr, this.port);
	    this.sock.send(pkt);

	    if (this.debug) {
		String sent = "";
		sent += "TXM : ";
	        //sent += javax.xml.bind.DatatypeConverter.printHexBinary(hash)+" : ";
		sent += topic + " : " + data;
		System.out.println(sent);
		//System.out.println("RAW:"+javax.xml.bind.DatatypeConverter.printHexBinary(mbuf));
	    }
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }

    /**
     * This method is called whenever data are received by this
     * multicast address and port.  Extend this class and override
     * this method to implement your own application logic.  The
     * default implementation prints out a String representation of
     * the data, even if the data are binary.
     *
     * @param topic the topic that was registered by the client 
     *              (e.g. "/home/bathroom")
     * @param data the binary data that was received in the message.
     * @param length the size in octets of the binary data.
     */
    public void recv(String topic, byte[] data, int length) {
	if (length <= data.length) {
	    if (this.debug) {
		String msg = new String(data, 0, length);
		String recd = "";
		recd += "RXM : ";
		//recd += topics.get(topic) + ":";
		recd += topic + " : ";
		recd += msg;
		System.out.println(recd);
	    }
	}
    }    

    /**
     * Shows example usage 
     */
    public static void main(String[] args) throws SocketException {
	String id = "1";
	String topic = "/topic/a";
	String addr = "224.0.0.3";
	int port = 8888;

	//System.out.println(java.net.NetworkInterface.getDefault());

	Enumeration<NetworkInterface> inters = NetworkInterface.getNetworkInterfaces();
	for (NetworkInterface iface : Collections.list(inters)) {
	    System.out.println("IFC : " + iface.getDisplayName());
	}

	if (args.length > 0) {
	    id = args[0];
	}

	try{
	    MulticastSocketAgent msa = new MulticastSocketAgent(addr, port);
	    new Thread(msa).start();
	    msa.register(topic);
	    msa.send(topic, id+".1");
	    Thread.sleep(1000);
	    msa.send(topic, id+".2");
	    Thread.sleep(1000);
	    msa.send(topic, id+".3");
	    Thread.sleep(1000);
	    msa.send(topic, id+".4");
	    Thread.sleep(1000);
	}
	catch (InterruptedException ie) {
	    ie.printStackTrace();
	}
    }
}
