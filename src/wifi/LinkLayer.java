package wifi;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.CRC32;

import rf.RF;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author tdetweiler
 */
public class LinkLayer implements Dot11Interface {
   private RF theRF;          				// You'll need one of these eventually
   private short ourMAC;      				// Our MAC address
   private PrintWriter output; 				// The output stream we'll write to
   private BlockingQueue<byte[]> received; 	//blocking queue to store all packets of data receive gets.
   private BlockingQueue<byte[]> acks; 		//blocking queue to store all acknowledgement's we get 
   private BlockingQueue<byte[]> sendQueue;	//blocking queue that we put packets in to send with the sender.
   private BlockingQueue<Integer> statusCode;//blocking queue that we store the latest value of the status
   private BlockingQueue<Long> clockOffset;//blocking queue that we will store the clock offset into
   private BlockingQueue<Integer> beaconWait;//blocking queue to store how long we will wait in sending beacons
   private BlockingQueue<Long> beaconOutset;	//our forecast on how long sending beacons takes
   private packet p; 						//packet class we will use in methods to create our packets.
   private boolean diagnosticOn = false;	//a boolean to be set on our off by control to show diagnostics
   private boolean contentionW = false;		//whether contention window selection is fixed or random. False = random
   private int beaconInterval = -1;				//seconds for beacon interval
   private long beaconOset = 0; 				//offset for how long it takes to build and send beacons
   
   /**
    * Constructor takes a MAC address and the PrintWriter to which our output will
    * be written. It creates the sender and receiver threads to start running in the background
    * and a packet class that we will use to pass information to in order to format our 
    * packets. We also initialize the blockqueues that
    * 
    * @param ourMAC  MAC address
    * @param output  Output stream associated with GUI
    */
   public LinkLayer(short ourMAC, PrintWriter output) {
	  this.ourMAC = ourMAC;
      statusCode = new LinkedBlockingQueue<Integer>();
      this.output = output;
      try{
      theRF = new RF(null, null); }
      catch(Exception e){ 
    	  if(statusCode.peek()!=null){
    	  try { statusCode.take(); }catch(InterruptedException e1){} }		//if the RF failed then update the status code 
    	  try { statusCode.put(3); }catch(InterruptedException e1){}
      }
      output.println("LinkLayer: Constructor ran.");
      beaconOutset = new LinkedBlockingQueue<Long>();						//implement and add to the blocking Queues for beacons.
      beaconWait = new LinkedBlockingQueue<Integer>();
      beaconOutset.add(beaconOset);
      beaconWait.add(beaconInterval);
      received = new LinkedBlockingQueue<byte[]>(); //initialize the blocking queues in the next 3 lines
      acks = new LinkedBlockingQueue<byte[]>(); 	
      sendQueue = new LinkedBlockingQueue<byte[]>();
      clockOffset = new LinkedBlockingQueue<Long>();
      long placer = 0;
      clockOffset.add(placer);
      this.p = new packet(ourMAC, output, theRF);			//initialize our packet class and give it the mac address we will use.
      beacon b = new beacon(beaconOutset, beaconWait, sendQueue, p, clockOffset, theRF);
	  reciever r = new reciever(theRF , output, received, acks, sendQueue, p, ourMAC, clockOffset); //create the receiver class
	  (new Thread(r)).start(); 						//start receiver in a thread.
	  sender s = new sender(theRF, output, sendQueue, p, acks, ourMAC, statusCode, clockOffset); //launch the sender class.
  	  (new Thread(s)).start(); 						//start the sender thread.
  	  (new Thread(b)).start();						//start the beacon thread.
  	  if(statusCode.peek()!=null){
   	  try { statusCode.take(); }catch(InterruptedException e1){} }		//if everything went right then update the status code 
   	  try { statusCode.put(1); }catch(InterruptedException e1){}
   }

   /**
    * Send method takes a destination, a buffer (array) of data, and the number
    * of bytes to send.  See docs for full description.
    */
   public int send(short dest, byte[] data, int len) {
	   byte[] frame; //create a frame for what we're going to to pass to the send thread
	   if(!(dest>=-1)){															//check to make sure MAC address is valid
		   if(statusCode.peek()!=null){
		      try { statusCode.take(); }catch(InterruptedException e1){} }		//if the MAC address is invalid then update the status code 
		      try { statusCode.put(8); }catch(InterruptedException e1){}
		      return 0;
	   }
	   if(len>2037){ //check to see if the data is more then we can handle and if so shorten it and let them know.
		  output.println("To many data bytes being sent, only sending 2038 bytes of data to " + dest);
		  len = 2038;
		  System.arraycopy(data, 0, data, 0, 2038);
	  }
	   	if(sendQueue.size()>4){													//limit outgoing packets to 4
	   		if(statusCode.peek()!=null){
	    	  try { statusCode.take(); }catch(InterruptedException e1){} }		//if not enough buffer space for outgoing packets then update the status code 
	    	  try { statusCode.put(10); }catch(InterruptedException e1){}
	   		return 0;
	   	}
	   	output.println("LinkLayer: Sending "+len+" bytes of data to "+dest + " at " + (theRF.clock() +  clockOffset.peek())); //what we're going to send
      	frame = p.normPack(dest, data, len); //create the packet that we will send by calling the normPack class from packet.
     	sendQueue.add(frame); //put the packet that we want to send into the send queue.
		return len; 			//return how many bytes we're sending
   }

   /**
    * Recv method blocks until data arrives, then writes it an address info into
    * the Transmission object.  See docs for full description.
    */
   public int recv(Transmission t){
	   byte[] message = new byte[2048];
	   while(true){
		   try{
			    message = received.take(); 		// block until there is a packet that we want to pass up.
			    output.println("Received message at " + (theRF.clock()+clockOffset.peek()));
				short destAdr; 					// shorts to be used in decoding the packet
				short senderAdr;
				ByteBuffer temp = ByteBuffer.wrap(message); //bytebuffer to be used in decoding the packet.
				destAdr = temp.getShort(2); 	//get the destination address from the packet.
				senderAdr = temp.getShort(4); 	//get the source address from the packet.
				byte[] data=new byte[message.length-10];		//create a byte array called data that we will move all the data into.
				int a =0;
				for(int i = 6; i<message.length-4; i++){ //while there is still data in the message put it into data.
					data[a] = message[i];
					a++;
				}
				t.setBuf(data); 				//give the transport layer the data
				t.setDestAddr(destAdr);			//give the transport layer the destination address
				t.setSourceAddr(senderAdr); 	//give the transport layer the source address			
				return data.length;			 //return the amount of data received	
	   		}
		   catch(Exception e){output.println("error here"); } //catch in case the take call crashes.
	   }
   }
   
   

   /**
    * Returns a current status code.  See docs for full description.
    */
   public int status() {
      int status = 0;
	try {
		status = statusCode.take();
	} catch (InterruptedException e){}
      return status;
   }

   /**
    * Passes command info to your link layer.  
	* Only command 0 and command 3 & 4 actually do anything
	* I didn't get time to make the diagnostic and contention window to actually work.
    */
   public int command(int cmd, int val) {
      if(cmd==0){																	//if it is a 0 command then lets print out the current settings and the
    	  output.println("Current settings:");										//commands they can call to change them.
    	  if(diagnosticOn){
    		  output.println("Diagnostic is on");
    	  }
    	  else{ output.println("Diagnostic is off"); }
    	  if(contentionW){
    		  output.println("Contention window slot selection is fixed");
    	  }
    	  else{ output.println("Contention window slot selection is random"); }
    	  output.println("The beacon interval is " + beaconInterval + " seconds");
    	  output.println("Control options:");
    	  output.println("Enter 0 for current settings and a list of commands");
    	  output.println("Enter 1 to turn diagnostic output on or off");
    	  output.println("Enter 2 to change from fixed or random slot selection of the contention window");
    	  output.println("Enter 3 followed by a number to set the ammount of seconds to wait before sending beacons");
      }
      if(cmd==1){
    	  if(diagnosticOn){
    		  diagnosticOn=false;
    		  output.println("Diagnostic is now off");
    	  }else{
    		  diagnosticOn=true;
    		  output.println("Diagnostic is now on");
    	  }
      }
      if(cmd==2){
    	  if(contentionW){
    		  contentionW=false;
    		  output.println("Contention window selection is now random");
    	  }else{
    		  contentionW=true;
    		  output.println("Contention window selection is now fixed");
    	  }
      }
      if(cmd==3){
    	  beaconInterval=val;
    	  try {
			beaconWait.take();
		} catch (InterruptedException e) {}
    	  beaconWait.add(beaconInterval);
    	  output.println("Time between beacons is now " + val + " seconds");
      }
      if(cmd==4){										//my own command to run a test in order to get an average on how long it takes to send beacons
    	  bOutgoingTest();
      }
      return 0;
   }
   
   /*
    * How I would like to send beacons is bellow... However, running an infinite loop as a void in the main
    * method crashes everything. Arghhhafuhhhdsaiufdsh
    * 
    */
   private void sendBeacons(){
	   short dest = -1;
	   byte[] data;
	   long showTime = theRF.clock()+ (beaconInterval*1000);	//when we should be sending next beacon		
	   while(true){											//infinite loop
		   if(beaconInterval>-1){						//as long as we should be sending beacons
			   if(showTime<=theRF.clock()){				//if we are supposed to be sending the beacon then do it
			   data = p.beaconPack(dest, clockOffset.peek(), beaconOset);	//build our beacon packet
			   if(sendQueue.size()<5){					//assuming there is room in buffer then add in order to send beacon
				   sendQueue.add(data);
				   }
			   showTime = theRF.clock()+ (beaconInterval*1000);	//update the next time to send at
			   }
		   }
   		}
   }
   
   /**
    * My testing method that when called builds and sends 10 beacons and then gets the average time as to how long it takes to send them.
    * 
    */
   private void bOutgoingTest(){
	   long clockNow =  theRF.clock();
	   short dest = -1;
	   int i = 10;
	   int slotTime = RF.aSlotTime;							//slot time
	   int difs = (slotTime*2) + RF.aSIFSTime;					//difs = sifs +slot time *2
	   byte[] data;
	   while(i>0){
		   data = p.beaconPack(dest, clockOffset.peek(), beaconOset);
		   theRF.transmit(data);
		   i--;
	   }
	   long clockEnd = theRF.clock() + (difs*10);			//waiting the minimum difs 10 times + the ending RF time.
	   beaconOset = (clockEnd-clockNow)/10;
	   try {
		beaconOutset.take();
	} catch (InterruptedException e) {}
	   beaconOutset.add(beaconOset);
   }
}

