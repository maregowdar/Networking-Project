import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import rf.RF;
import java.util.Random;
import java.lang.Short;




/**
 * My main method called runner that creates the RF layer, loads or creates a MAC address
 * and then creates threads for the runnable methods sender and listener.
 * 
 * @author tdetweiler
 *
 */
public class Runner {

	/**
	 * The main method that calls sender and listener as threads
	 * @param args
	 */
	public static void main(String[] args)
    {
        RF theRF = new RF(null, null);  // Create an instance of the RF layer
        ByteBuffer temp = ByteBuffer.allocate(10); //create a bytebuffer with 10 bytes to store the MAC address and clock time.
        temp.order(ByteOrder.BIG_ENDIAN); //set the order in the bytebuffer to bigEndian to follow network byte order.
        Random R = new Random();  // create a random that we will use to create random bytes for a MAC address if none inputed.
        
        //If nothing was imputed with running the program, create a random MAC address.
        if(args.length==0){
        	byte[] buf = new byte[2];//byte array to put the random bits into
        	R.nextBytes(buf); //random method that stuffs the array with random bits.
        	temp.put(buf); //set the first part of the bytebuffer equal to the byte array.
        	int tempi = temp.getShort(0) & 0xffff; //int equal to the short x 111111 in order to make positive 
        	System.out.println("Using a random MAC address: " + tempi); //print out the created MAC address to use.
        }
        else{ //if something was inputed into the program
        	String tempS = args[1]; // create a sting of the first full input
        	Short holder = new Short(tempS); // create a short holding that data
        	if(holder.intValue()<0 || holder.intValue() >65535){ //run an if statement that makes sure it is an int value in the
        														//short that is within acceptable range.
        		System.out.println("Not a valid MAC address imported.... Please try again with an actual one!"); 
        		System.exit(0);
        		// if it isn't acceptable let the user know and exit.
        	}
        	temp.putShort(holder); //otherwise set the MAC address in the bytebuffer equal to the short.
        }
        
        listener a = new listener(theRF); //create a listener 
        sender b = new sender(theRF, temp); //create a sender
        
        (new Thread(a)).start(); //create the threads to run both the sender and listener and start them 
        (new Thread(b)).start();
         
    }
	
}