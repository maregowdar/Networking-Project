package wifi;

import java.awt.Frame;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.zip.CRC32;

import rf.RF;

public class reciever implements Runnable{
	private RF theRF; 							//rf layer we use
	private PrintWriter output;					//output to print stuff
	private BlockingQueue<byte[]> received;		//the received blocking queue we will put data meant for us into
	private BlockingQueue<byte[]> acks;			//the acks blocking queue we will put any acknowledgments for us into.
	private BlockingQueue<byte[]> sendQueue;	//the send queue that we will put messages we need to respond to in.
	private BlockingQueue<Long> clockOffset;
	private packet p;							//the packet class that has our macAddress to help process packets
	private short MAC;							//our MAC address
	
	/**
	 * Our receiver class that is implemented with the link layer. It runs continuously receiving packets and processing them
	 * passing on all relevant ones through the Blocking Queues. 
	 * 
	 * @param theRF
	 * @param output
	 * @param recieved
	 * @param acks
	 * @param sendQueue
	 * @param p
	 */
	public reciever(RF theRF, PrintWriter output, BlockingQueue<byte[]> received, BlockingQueue<byte[]> acks, BlockingQueue<byte[]> sendQueue, packet p, short MAC, BlockingQueue<Long> clockOffset){
		this.theRF = theRF;
		this.output = output;
		this.acks = acks;
		this.received = received;
		this.sendQueue=sendQueue;
		this.p=p;
		this.MAC = MAC;
		this.clockOffset = clockOffset;
	}
	
	/**
	 * My run method that will block until it receives something, let it's caller it know it received something and how many bytes.
	 * And then parses the received packet by calling the packet reader class and passes it to the transmission layer.
	 */
	public void run(){
		while(true){ 					//run continuously 
		byte[] holder = new byte[2048]; //a holder to accept the incoming packets.
		holder = theRF.receive(); 		//block until we receive a packet.
		readPacket(holder); 			//pass the received packet to the readPacket class to parse and pass it on.
		}
	}
		
	/**
	 * A class that takes the inputed packet, parses it for the sender address, dest address, and the data. 
	 * It then takes these and passes them up to the transport layer.
	 * It calls the packet class in order to manage sequences.
	 * 
	 * @param message
	 */
	private void readPacket(byte[] message){
		short destAdr;
		CRC32 crc = new CRC32();
		ByteBuffer temp = ByteBuffer.wrap(message);		//wrap the imputed message into a bytebuffer to process it.
		byte packetType;								//byte to hold the first byte of the message to check its type.
		packetType = message[0];					//get the first byte to see what type of packet it is.
		packetType = (byte)(packetType>>>5);		//bitwise manipulation to only see the frame type
		destAdr = temp.getShort(2); 					//get the destination address from the packet.
		crc.update(message,0,message.length-4); 		//update the CRC based on the message
		int sentCRC = temp.getInt(message.length-4);
		if((int)crc.getValue()==sentCRC){				//make sure it is a valid CRC on received packets
		if(destAdr == -1){								//check to see if it was a -1 address
			if(packetType==2){							//if it is a beacon frame
				long clockTime = temp.getLong(6);		//get the clock time from it
					if(clockTime-theRF.clock()>clockOffset.peek()){							//check to see if the beacons clock time was faster
						try { clockOffset.take(); }catch(InterruptedException e1){} 		//if the beacons clock set is faster then replace our offset with it 
			    	  	try { clockOffset.put(clockTime-theRF.clock()); }catch(InterruptedException e1){} 
			    	output.println("Received clock time is faster, advancing clock by " + (clockTime-theRF.clock()));  	
					}else{
						output.println("Received beacon was slower, ignored it");
				}				
		}else{												//it is a broadcast otherwise
			output.println("Recieved Bcast at " + (theRF.clock() +  clockOffset.peek()));
			received.add(message); }
		}
		else{ if(destAdr==MAC){							//check to see if the packet was meant for us. If not ignore
			if(packetType==1){							//if frame type of an acknowledgment, add packet to ack queue and end.
				acks.add(message);
			}
			else{
				if(packetType==2){						//if it is a beacon frame
					long clockTime = temp.getLong(6);
					if(clockOffset.peek()!=null){
						if(clockTime-theRF.clock()>clockOffset.peek()){
							try { clockOffset.take(); }catch(InterruptedException e1){} 		//if the beacons clock set is faster then replace our offset with it 
				    	  	try { clockOffset.put(clockTime-theRF.clock()); }catch(InterruptedException e1){} 
				    	output.println("Received clock time is faster, advancing clock by " + (clockTime-theRF.clock()));  	
						}else{
							output.println("Received beacon was slower, ignored it");
					}	
							
				}
			}
				else{ 
					if(received.size()>4){
						return;
					}
					int checker = p.recievedP(message);	//if its for us and not an ack then send it to packet to run the sequences. 
					if(packetType==0){					//if it is a data packet
						if(checker==-1){				//if its the wrong sequence number then expected.
							output.println("warning wrong sequence number then expected was recieved");
						}
						sendQueue.add(message);			//pass packet on to be acknowledged.
					}
					if(checker!=0){						//if the packet was not a retransmission then pass it to be receive to be passed up.
					received.add(message);
					}
				}
			}
			}
		}
	}
		else{	output.println("BAD CRC MESSAGE"); }
	}
}
