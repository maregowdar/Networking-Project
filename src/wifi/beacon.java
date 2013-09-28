package wifi;

import java.util.concurrent.BlockingQueue;

import rf.RF;

public class beacon implements Runnable  {
	private BlockingQueue<Long> clockOffset;	//blocking queue that we will store the clock offset into
	private BlockingQueue<Integer> beaconWait;	//blocking queue to store how long we will wait in sending beacons
	private BlockingQueue<Long> beaconOutset;	//our forecast on how long sending beacons takes
	private BlockingQueue<byte[]> sendQueue;	//blocking queue that we put packets in to send with the sender.
	private RF theRF;
	private packet p;
	
	public beacon(BlockingQueue<Long> beaconOutset,BlockingQueue<Integer> beaconWait, BlockingQueue<byte[]> sendQueue, packet p, BlockingQueue<Long> clockOffset, RF theRF){
		this.beaconOutset=beaconOutset;
		this.beaconWait=beaconWait;
		this.sendQueue=sendQueue;
		this.p=p;
		this.clockOffset=clockOffset;
		this.theRF=theRF;
	}
	
	/**
	 * Pretty simple, same thing that was in link layer but actually works here.
	 * Everytime the clock
	 * 
	 */
	public void run(){
		short dest = -1;
		   byte[] data;
		   long showTime = theRF.clock()+ (beaconWait.peek()*1000);	//when we should be sending next beacon		
		   while(true){											//infinite loop
			   if(beaconWait.peek()>-1){						//as long as we should be sending beacons
				   if(showTime<=theRF.clock()){				//if we are supposed to be sending the beacon then do it
				   data = p.beaconPack(dest, clockOffset.peek(), beaconOutset.peek());	//build our beacon packet
				   if(sendQueue.size()<5){					//assuming there is room in buffer then add in order to send beacon
					   sendQueue.add(data);
					   }
				   showTime = theRF.clock()+ (beaconWait.peek()*1000);	//update the next time to send at
				   }
			   }
	   		}
	}

}
