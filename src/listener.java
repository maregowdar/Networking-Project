import java.nio.ByteBuffer;
import rf.RF;


/*
 * Listener method that takes in the RF to be used and the waits to 
 * receive packets from other programs on the network and then 
 * prints them out.
 * 
 */
public class listener implements Runnable {
	private RF theRF;
	
	public listener(RF theRF){
		this.theRF = theRF; //set this RF layer equal to the one passed in.
	}
	
	public void run(){
		byte[] holder = new byte[10]; //create a byte-array of 10 in order to be used for the receive
		Short tempShort; // short to be used
		Long tempLong; // long to be used
		int tempI; // a temp to be used for converting the MAC address
		ByteBuffer temp = ByteBuffer.allocate(10); // a byte buffer to be used in getting out the short MAC and clock time.
		//run the loop infinitely
		while(true){
			holder = theRF.receive(); // receive a packet on the network and set the holder array to it.
			if(holder.length != 10){ //make sure the holder is 10 bytes or print an error
				System.out.println("Error: recieved packet not correct size");
			}
			temp = temp.wrap(holder);//put the holder into a bytebuffer so we get get the short and long from it.
			System.out.print("Recieved: [ "); //print out the bits of what we got.
			for(int i=0; i<=temp.array().length-1; i++){
		        System.out.print(temp.get(i) + " ");
		    }
			System.out.println(" ]");
			tempShort = temp.getShort(0); // get the MAC address into a short.
			tempI = tempShort & 0xffff; // convert the MAC address into a positive integer
			tempLong = temp.getLong(2); //get the clock time in a long
			System.out.println("Host " + tempI + " says time is " + tempLong); // print out the converted saying of what
																				//was sent in the packet.
		}	
	}
	

}