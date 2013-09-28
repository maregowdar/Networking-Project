import java.nio.ByteBuffer;
import rf.RF;
import java.util.Random;



/*
 * Sender is a class the takes in the RF and the bytebuffer with the MAC address
 * and then continuously sends them out on the network after a random wait time. 
 * 
 */
public class sender implements Runnable {
	private RF theRF;
	private ByteBuffer message;
	private Random R;
	
	public sender(RF theRF, ByteBuffer message){
		this.theRF = theRF; //set the RF layer equal to the run past in in to use it/
		this.message = message; // Set the bytebuffer that will be used to the one passed in so we know the MAC address.
		this.R = new Random(); // create a new random to be used for the wait times.
	}
	
	public void run(){
		Short tempShort = new Short(message.getShort(0)); // get the MAC address into a short.
		int MAC = tempShort & 0xffff; // convert the MAC address into its positive integer format
		Long tempLong; //a long to be used for the clock values. 
		double tempD; // a double to be used for the random wait times
		int tempI, sentB; // temp stores to be used for wait time and the ammount of bytes sent
		//run the loop infinitely
		while(true){
			tempD = R.nextDouble()*7000;//get a random double between 0 and 1.0 and then times it by 7000 to get a 
										//millisecond time between 0 and 7 seconds(inclusive).
			tempI = (int)tempD; //set the integer equal to the double for the sleep
			try {
				//try to sleep the thread for the random time
			    Thread.sleep(tempI);
			} catch(InterruptedException ex) {
				//catch it if the sleep doesn't work.
			    Thread.currentThread().interrupt();
			}
			tempLong = theRF.clock(); //get the clock time and put it in the long.
			message.putLong(2,tempLong); //add the long into the bytebuffer after the MAC address.
			System.out.print("Sent Packet: " + MAC + " " + tempLong + "    [ "); //print out what the converted value of
																				//what we're sending.
			for(int i=0; i<=message.array().length-1; i++){ //a for loop to print out the bit values of what we're sending.
		        System.out.print(message.get(i) + " ");
		    }
			System.out.println(" ]");
			sentB=theRF.transmit(message.array()); //actually transmit the bytearray and store how many bytes were sent.
			if(sentB<10){ //if ten bytes weren't sent then print an error message letting us know. 
				System.out.println("Error: only printed out " + sentB + " Bytes");
			}
		}
	}
}
