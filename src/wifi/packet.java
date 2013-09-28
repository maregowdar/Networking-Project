package wifi;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

import rf.RF;

public class packet {
	private short ourMAC;	//our MAC to be used in creating packets.
	private RF theRF;
	private Map<Short, Short> OsequenceNumbers = new HashMap<Short, Short>(200);		//hash Map to be used in sequencing. Random choosen size?
	private Map<Short, Short> IsequenceNumbers = new HashMap<Short, Short>(200);		//hash Map to be used in sequencing. Random choosen size?
	private PrintWriter output;
	private CRC32 crc;
	
	/**
	 * Create our packet class declaring it with a passed in MAC address.
	 * 
	 * 
	 * @param ourMAC
	 */
	public packet(short ourMAC, PrintWriter output, RF theRF){
		this.ourMAC = ourMAC;
		this.output=output;	
		crc = new CRC32();
		this.theRF = theRF;
	}
	
	
	/**
	 * Normal data packet formating class that creates our normal packets with a data frame type, the correct sequence number
	 * and fills in the rest of the information with the passed in variables.
	 * 
	 * @param dest
	 * @param data
	 * @param len
	 * @return
	 */
	public byte[] normPack(short dest, byte[] data, int len){
			crc.reset();
			ByteBuffer temp = ByteBuffer.allocate(10+len); 	//create a bytebuffer to put together the packet.
			short sequenceN;								//sequence number to be used in updating the hash map
			short sequenceUse = 0;							//sequence number to actually be used in making the packet
			if(OsequenceNumbers.containsKey(dest)){			//if a sequence number already exist
				sequenceUse = OsequenceNumbers.get(dest);	//then get that sequence number into both shorts.
				sequenceN=sequenceUse;
				if((int)sequenceN==2048){					//if we hit the limit reset sequence number for next expected.
					sequenceN = 0;
					OsequenceNumbers.put(dest, sequenceN);
				}
				else{
					int hold = (int)sequenceN+1;			//if we haven't hit limit then increase sequence number by 1
					sequenceN = (short)hold;				//and update the hashmap with it.
					OsequenceNumbers.put(dest, sequenceN);
				}
			}
			else{
				sequenceN = 1;								//if it wasn't already in the hashmap then create a new sequence number
				OsequenceNumbers.put(dest, sequenceN);		//set it to 1 because we're about to send 0.
			}
			if(dest==-1){									//if this is a b-cast then ignore the whole sequencing thing and 
				sequenceUse = 0;							//use all 0's
				OsequenceNumbers.put(dest, (short)0);
			}
			temp.putShort(sequenceUse);		//put the command into the byte buffer first.
			temp.putShort(dest);			//put the dest short into the byte buffer.
			temp.putShort(ourMAC); 			//put my mac address into the byte buffer. 
			for(int i = 0; i<len; i++){		//while there are still bytes in the data put them into our buffer starting at a 6 offset.
				temp.put(data[i]);
			}
			crc.update(temp.array(), 0, temp.array().length-4);			//create the CRC for the message we are sending
			temp.putInt((int)crc.getValue()); 	//put the CRC at the end to fill those 4 bytes. 
			return temp.array(); 				//return the bytebuffer as its byte array.
		}

	/**
	 * Takes in the destination and the old message. Gets the sequence number from the old message, adds an acknowledgment frame 
	 * type, and then adds the sender and receiver address and returns the new formated packet
	 * 
	 * @param dest
	 * @param original
	 * @return
	 */
	public byte[] ackPack(short dest, byte[] original){
		crc.reset();
		ByteBuffer temp1 = ByteBuffer.wrap(original);	//take the original received message and put it into a bytebuffer
		short command = temp1.getShort(0);			//get the command from the orignal message
		ByteBuffer temp = ByteBuffer.allocate(10); 	//create a bytebuffer to put together the packet.
		int hold = (int)command + 8192; 			//set the command = to the sequence + a 1 into the ack frame type.
		command = (short)hold;
		temp.putShort(command);						// put the command into the byte buffer first.
		temp.putShort(dest); 						//put the dest short into the byte buffer.
		temp.putShort(ourMAC); 						//put my mac address into the byte buffer. 
		crc.update(temp.array(),0 , 6);
		temp.putInt((int)crc.getValue());					
		return temp.array();
	}
	
	/**
	 * Beacon forming class that creates a packet with the data being our estimated clock time
	 * 
	 * The clock time is compiled by takin the rf clock adding it with out clock offset and then adding our estimate of how long it takes to 
	 * build and send the packet
	 * 
	 * @param dest
	 * @param offSet
	 * @param sendOffset
	 * @return
	 */
	 public byte[] beaconPack(short dest, long offSet, long sendOffset){
		crc.reset();
		ByteBuffer temp = ByteBuffer.allocate(18); //create a bytebuffer to put together the packet.
		short command = (short)16384; //set the command to be beacon packet
		temp.putShort(command); // put the command into the byte buffer first.
		temp.putShort(dest); //put the dest short into the byte buffer.
		temp.putShort(ourMAC); //put my mac address into the byte buffer. 
		temp.putLong((theRF.clock()+ offSet + sendOffset));			//get the clock time into the packet by takin RF clock, our offset, and the sending offset together
		crc.update(temp.array(), 0, temp.array().length-4);
		temp.putInt((int)crc.getValue());	//put the crc into the packet
		return temp.array();
		
	}
	
	/**
	 * A retransmission formatter that takes a packet and adds the re-transmission bit onto it and then returns the packet.
	 * 
	 * @param message
	 * @return
	 */
	public byte[] retranPack(byte[] message){
		ByteBuffer original = ByteBuffer.wrap(message,0, message.length-4); //create a bytebuffer to put together the packet.
		ByteBuffer newOne = ByteBuffer.allocate(message.length);
		short command = original.getShort(0); 			//get the command from it
		command = (short)((int)command + 4096);  		//update the command with a retransmission bit onto it.
		newOne.putShort(command);						//copy everything over, create a new CRC and then return the updated re-transmission packet
		command = original.getShort(2);
		newOne.putShort(command);
		command = original.getShort(4);
		newOne.putShort(command);
		for(int i = 6; i<message.length-4; i++){
			newOne.put(original.array()[i]);
		}
		crc.update(newOne.array(), 0, newOne.array().length-4);
		newOne.putInt((int)crc.getValue());
		return newOne.array(); 						//return the bytebuffer as its byte array.
		
	}
	
	/**
	 * Class the works with the receiver thread to check sequence numbers.
	 * If the sequence number is not the one expected return -1
	 * If we have already received the sequence number return 0
	 * If it is correct then update the hashmap with the next expected sequence number
	 * 	
	 * @param message
	 * @return
	 */
	public int recievedP(byte[] message){
		ByteBuffer temp = ByteBuffer.wrap(message); //byte buffer that wraps the message to process it.
		short hold = temp.getShort(0);				//holder that we will put the messages sequence number into.
		short sender = temp.getShort(4);			//get who sent it.
		hold = (short)(hold<<4);					//bit wise manipulation to get rid of any re-transmission or frame type bit.
		hold = (short)(hold>>>4);
		if(IsequenceNumbers.containsKey(sender)){		//check to see if we have already talked with this person
			if(IsequenceNumbers.get(sender)==(short)((int)hold-1)){	//if the sequence number is the last one we talked about then return 0;
				return 0;								
			}
			else{
				if(IsequenceNumbers.get(sender)==hold){					//if the sequence number is what we expect then update the 
					IsequenceNumbers.put(sender, (short)((int)hold+1));	//sequence map and return 0.
					return 1;
				}
			}
			return -1;									//if it failed these then it's unexpected... return -1
		}
		IsequenceNumbers.put(sender, (short)((int)hold+1));				//if we don't have it yet then put it into the hashmap and return 1
		return 1;
	}
	
}
