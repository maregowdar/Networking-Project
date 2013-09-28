package wifi;

import java.awt.Frame;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

import rf.RF;

public class sender implements Runnable {
	private RF theRF;							//the rf layer
	private PrintWriter output;					//output to print stuff
	private BlockingQueue<byte[]> sendQueue;	//our queue of things to send
	private BlockingQueue<byte[]> ack;			//our queue of acknowledgments 
	private BlockingQueue<Integer> statusCode;
	private BlockingQueue<Long> clockOffset;
	private packet p;							//packet to format messages
	private short dest;							//dest to be set in thread
	private boolean sent;						//boolean to be used to check to see if we have gotten the acknowledgment
	private byte[] message;						//message to be used in classes
	private short sequenceN;					//the sequence number to be used in checking ack packets.
	private short MAC;							//our mac address
	private Random r;							//random to be used in selection slots for contention window
	
	
	/**
	 * Our sender class that runs ifinitely sending packets in the send queue and then either waiting for acks or not.
	 * 
	 * @param theRF
	 * @param output
	 * @param sendQueue
	 * @param p
	 * @param ack
	 */
	public sender(RF theRF, PrintWriter output,  BlockingQueue<byte[]> sendQueue, packet p, BlockingQueue<byte[]> ack, short MAC, BlockingQueue<Integer> statusCode, BlockingQueue<Long> clockOffset){
		this.theRF = theRF;
		this.output = output;
		this.sendQueue = sendQueue;
		this.p=p;
		this.ack=ack;
		this.MAC = MAC;
		r = new Random();
		this.statusCode=statusCode;
		this.clockOffset = clockOffset;
	}
	
	/**
	 * Our run method that checks the address of the message to decide what we do with it and then does a 3 switch case
	 * for bcast, data, and acks.
	 * 
	 */
	public void run(){
		while(true){											//run infinitely
			int whatPath=0;										//the int to be set for our path
			try{ message = sendQueue.take(); }					//get the next packet from our queue, blocking until we get it
			catch(Exception e){}								//catch in case the take method crashes.
					ByteBuffer temp = ByteBuffer.wrap(message);	//bytebuffer for our message in order to get the dest from it.
					dest = temp.getShort(2);
					sequenceN = temp.getShort(0);				//while we're at it get the sequence number from the packet.
					sequenceN = (short)(sequenceN<<4);
					sequenceN = (short)(sequenceN>>>4);
					if(dest==-1){								//if this is a bcast message
						whatPath=3;
					}else{ if(dest==MAC){			//if this is supposed to be an acknowledgment
						whatPath=2;
					}else{										//otherwise normal packet
						whatPath=1; }	
					}
					switch(whatPath){
					case 1: sendNormal();
							break;
					case 2: dest = temp.getShort(4);
							sendAck();
							break;
					case 3: sendBcast();
							break;
						
					}
			}
	}
	
	/**
	 * Our send normal class that waits until the RF layer is free following MAC specification on waiting. It then waits for an 
	 * acknowledgment, resending if necessary and timing out after a certain amount of attempts.
	 * 
	 */
	private void sendNormal(){
		send(message);										//call the send class
		sent = false;										//set send to false until we get an ack
		int counter =0;										//start a counter of attempts
		message=p.retranPack(message);						//set message into a retransmission packet incase
		waitforAck();										//wait for acknowledgment class call
		while(!sent){										//if we didn't get one send the packet again and add 1 to the counter
			counter++;		
			output.println("retrying send");
			send(message);
			if(counter<RF.dot11RetryLimit){					//while we still have retry's then wait and retest.
				waitforAck();
			}
			else{ output.println("failure to send at" + (theRF.clock() +  clockOffset.peek()));		//if we run out of attempts then quit and print error
			 if(statusCode.peek()!=null){
		    	  try { statusCode.take(); }catch(InterruptedException e1){} }		//transmission failed... update the status code 
		    	  try { statusCode.put(5); }catch(InterruptedException e1){}
				return;
			}
		}
		output.println("Ack recieved, transmission successful at " + (theRF.clock() +  clockOffset.peek()));	//otherwise it worked!
		 if(statusCode.peek()!=null){
	    	  try { statusCode.take(); }catch(InterruptedException e1){} }		//Successful transmission! update the status code 
	    	  try { statusCode.put(4); }catch(InterruptedException e1){}
	}
	
	/**
	 * Formats an acknowledgment packet and sends it.
	 */
	private void sendAck(){
		message = p.ackPack(dest, message);	//call packet ack former to create message.
		send(message);						//call send class
	}
	
	/**
	 * Sends a bcast message without worrying about acks or anything.
	 * 
	 */
	private void sendBcast(){				
		send(message); //call the send method
	}
	
	/**
	 * Send class which takes in the message to send, checks the RF layer and follows MAC procedure.
	 * 
	 * @param message
	 */
	private void send(byte[] message){
		int slotTime = RF.aSlotTime; 				//get the slots RF layer
		int difs = (slotTime*2) + RF.aSIFSTime;		//creates difs by taking sifs time and adding 2*slot time.
		int contentionNumber = r.nextInt(RF.aCWmax);//create contention number.
		if(theRF.inUse()){							//if the RF is in use then use normal wait and then transmit the message once 
				waits(contentionNumber);			//it's not idle
				output.println("sending");			//once we are done waiting with MAC rules and backoff then send
				int x = theRF.transmit(message);
				output.println("LinkLayer: Sent "+ x +" bytes to "+dest + " at " + (theRF.clock() +  clockOffset.peek()));
			}
		else{
			try {
				Thread.sleep(difs);					//if it's not in use wait difs
			} catch (InterruptedException e) {}
			if(!theRF.inUse()){						//if it's still not in use then send the message
				output.println("sending");
				int x = theRF.transmit(message);
				output.println("LinkLayer: Sent "+ x +" bytes to "+dest + " at " + (theRF.clock() +  clockOffset.peek()));
			}
			else{
				waits(contentionNumber);							//then wait like normal until it is idle and send
				output.println("sending");
				int x = theRF.transmit(message);
				output.println("LinkLayer: Sent "+ x +" bytes to "+dest + " at " + (theRF.clock() +  clockOffset.peek()));
			}
		}
	}
	
	/**
	 * Wait method that follows MAC rules to wait until the channel is idle.
	 * 
	 */
	private void waits(int contentionNumber){
		int slotTime = RF.aSlotTime;							//slot time
		int difs = (slotTime*2) + RF.aSIFSTime;					//difs = sifs +slot time *2
		while(contentionNumber>0){								//keep waiting until our contention window ends
			if(!theRF.inUse()){									//if the RF is idle then wait slot time and subtract one from contention window
				try {										
						Thread.sleep(slotTime);					//sleep for slot time
						contentionNumber--;						//subtract one from contention window
				} 	catch(InterruptedException ex) {
				//catch it if the sleep doesn't work.
		    	Thread.currentThread().interrupt();
				}
			}
			else{												//if the RF is in use then lets wait until its not and then go back to subtracting
				try{											//the contention window and waiting slot time
					Thread.sleep(difs);
				}catch(InterruptedException ex){
					Thread.currentThread().interrupt();
				}
			}	
		}
		if(theRF.inUse()){									//if at the end of our contention window the RF is in use then do it again
			contentionNumber = r.nextInt(RF.aCWmax);		//create contention number.
			waits(contentionNumber);						//call recursively to do the backoff again
		}	
	}
	
	/**
	 * Wait for ack class that checks to see if we have an ack, else sleeps, and then checks again. If
	 * still no ack then the boolean remains false.
	 * 
	 */
	private void waitforAck(){
		byte[] ackR = ack.peek();
		if(ackR!=null){								//if there is something in the ack queue
		ByteBuffer temp = ByteBuffer.wrap(ackR);	//wrap the ack so we can process it
		short sequenceNTemp = temp.getShort(0);		//get the sequence from the ack
		sequenceNTemp = (short)(sequenceNTemp<<4);
		sequenceNTemp = (short)(sequenceNTemp>>>4);
		short sender = temp.getShort(4);
			if(sender==dest && sequenceN == sequenceNTemp){		//if this is from the right sender and the right sequence
				sent=true;							//then set sent to true
				try{ack.take();}					//remove the ack from the queue
				catch(Exception e){}
				return;								//end
			}
		}
		acksSleep();								//otherwise call acksSleep to wait.
		ackR=ack.peek();
		if(ackR!=null){								//run the same test as earlier
			ByteBuffer temp = ByteBuffer.wrap(ackR);
			short sequenceNTemp = temp.getShort(0);
			sequenceNTemp = (short)(sequenceNTemp<<4);
			sequenceNTemp = (short)(sequenceNTemp>>>4);
			short sender = temp.getShort(4);
				if(sender==dest && sequenceN == sequenceNTemp){
					sent=true;
					try{ack.take();}
					catch(Exception e){}
				}
		}
	}
	/**
	 * Sleep while waiting for ack for a set final time
	 * 
	 */
	private void acksSleep(){
		try{
			int ACKSLEEP = 807;				//after a few tests this was what I came up with for my average on acknowledgments
			Thread.sleep(ACKSLEEP);		
		}catch(InterruptedException ex) {
			//catch it if the sleep doesn't work.
	    	Thread.currentThread().interrupt();
			}
	}
	
	
	
	
}
