import rf.RF;

/**
 * A simple test class showing the creation of an RF instance and
 * the sending of three bytes of data.
 */
public class RF_Test 
{
    public static void main(String[] args)
    {
        RF theRF = new RF(null, null);  // Create an instance of the RF layer
        
        // Put together an array of bytes to do a test sent
        byte[] buf = new byte[3];
        buf[0] = 10;
        buf[1] = 20;
        buf[2] = 5;
        
        // Try to send it and see if it went out.
        int bytesSent = theRF.transmit(buf);
        if (bytesSent != buf.length)
            System.err.println("Only sent "+bytesSent+" bytes of data!");
        else
            System.out.println("Yay!  We sent the entire packet!");
            
        System.exit(0);  // Make sure all threads die
    }
}