/**
 * Author : Rishi Gupta
 * Email  : gupt21@gmail.com
 * 
 * This file is part of 'serial communication manager' library.
 *
 * The 'serial communication manager' is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * The 'serial communication manager' is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with serial communication manager. If not, see <http://www.gnu.org/licenses/>.
 */

package com.embeddedunveiled.serial;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>This class runs in as a different thread context and keep looping over data/event queue, 
 * delivering data/events to the intended registered listener (data/event handler) one by one. 
 * The rate of delivery of data/events are directly proportional to how fast listener finishes
 * his job and let us return.</p>
 */
public final class SerialComLooper {
	
	private final boolean DEBUG = true;
	private final int MAX_NUM_EVENTS = 5000;
	
	private BlockingQueue<SerialComDataEvent> mDataQueue = new ArrayBlockingQueue<SerialComDataEvent>(MAX_NUM_EVENTS);
	private ISerialComDataListener mDataListener = null;
	private Object mDataLock = new Object();
	private Thread mDataLooperThread = null;
	private AtomicBoolean deliverDataEvent = new AtomicBoolean(true);
	
	private BlockingQueue<SerialComLineEvent> mEventQueue = new ArrayBlockingQueue<SerialComLineEvent>(MAX_NUM_EVENTS);
	private ISerialComEventListener mEventListener = null;
	private Object mEventLock = new Object();
	private Thread mEventLooperThread = null;
	
	private int appliedMask = SerialComManager.CTS | SerialComManager.DSR | SerialComManager.DCD | SerialComManager.RI;
	private int oldLineState = 0;
	private int newLineState = 0;
	
	private SerialComJNINativeInterface mNativeInterface = null;
	private SerialComErrorMapper mErrMapper = null;
	
	/**
	 * <p>This class runs in as a different thread context and keep looping over data queue, delivering 
	 * data to the intended registered listener (data handler) one by one. The rate of delivery of
	 * new data is directly proportional to how fast listener finishes his job and let us return.</p>
	 */
	class DataLooper implements Runnable {
		@Override
		public void run() {
			/* take() method blocks if there is no event to deliver. So we don't keep wasting 
			 * CPU cycle in case queue is empty. */
			while(true) {
				synchronized(mDataLock) {
					try {
						mDataListener.onNewSerialDataAvailable(mDataQueue.take());
						if(deliverDataEvent.get() == false) {
							mDataLock.wait();
						}
					} catch (InterruptedException e) {
						if(DEBUG) e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * <p>This class runs in as a different thread context and keep looping over event queue, delivering 
	 * events to the intended registered listener (event handler) one by one. The rate of delivery of
	 * events are directly proportional to how fast listener finishes his job and let us return.</p>
	 */
	class EventLooper implements Runnable {
		@Override
		public void run() {
			while(true) {
				synchronized(mEventLock) {
					try {
						mEventListener.onNewSerialEvent(mEventQueue.take());
					} catch (InterruptedException e) {
						if(DEBUG) e.printStackTrace();
					}
				}
			}
		}
	}
	
	/* Constructor */
	public SerialComLooper(SerialComJNINativeInterface nativeInterface, SerialComErrorMapper errMapper) { 
		mNativeInterface = nativeInterface;
		mErrMapper = errMapper;
	}
	
	/**
	 * <p>This method is called from native code to pass data bytes.</p>
	 */
	public void insertInDataQueue(byte[] newData) {
        if(mDataQueue.remainingCapacity() == 0) {
        	mDataQueue.poll();
        }
        try {
			mDataQueue.offer(new SerialComDataEvent(newData));
		} catch (Exception e) {
			if(DEBUG) e.printStackTrace();
		}
	}
	
	/**
	 * <p>Native side detects the change in status of lines, get the new line status and call this method. Based on the
	 * mask this method determines whether this event should be sent to application or not.</p>
	 */
	public void insertInEventQueue(int newEvent) {
		newLineState = newEvent & appliedMask;
	        if(mEventQueue.remainingCapacity() == 0) {
	        	mEventQueue.poll();
	        }
	        try {
				mEventQueue.offer(new SerialComLineEvent(oldLineState, newLineState));
			} catch (Exception e) {
				if(DEBUG) e.printStackTrace();
			}
		oldLineState = newLineState;
	}

	/**
	 * <p>Start the thread to loop over data queue. </p>
	 */
	public void startDataLooper(long handle, ISerialComDataListener dataListener, String portName) {
		try {
			mDataListener = dataListener;
			mDataLooperThread = new Thread(new DataLooper(), "DataLooper for handle " + handle + " and port " + portName);
			mDataLooperThread.start();
		} catch (Exception e) {
			if(DEBUG) e.printStackTrace();
		}
	}

	/**
	 * <p>Get initial status of control lines and start thread.</p>
	 */
	public void startEventLooper(long handle, ISerialComEventListener eventListener, String portName) throws SerialComException {
		int state = 0;
		int[] linestate = new int[8];

		try {
			linestate = mNativeInterface.getLinesStatus(handle);
			if (linestate[0] < 0) {
				throw new SerialComException("getLinesStatus()", mErrMapper.getMappedError(linestate[0]));
			}
			// Bit mask CTS | DSR | DCD | RI
			state = linestate[1] | linestate[2] | linestate[3] | linestate[4];
			oldLineState = state & appliedMask;
			
			mEventListener = eventListener;
			mEventLooperThread = new Thread(new EventLooper(), "EventLooper for handle " + handle + " and port " + portName);
			mEventLooperThread.start();
		} catch (Exception e) {
			if(DEBUG) e.printStackTrace();
		}
	}
	
	/**
	 * 
	 */
	public void stopDataLooper(long handle, ISerialComDataListener dataListener, String portName) {
		try {
			//mDataLooperThread.stop(); TODO
		} catch (Exception e) {
			if(DEBUG) e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	public void stopEventLooper(long handle, ISerialComEventListener eventListener, String portName) throws SerialComException {
		try {
			//mEventLooperThread.stop(); TODO
		} catch (Exception e) {
			if(DEBUG) e.printStackTrace();
		}
	}
	
	/**
	 * </p>Looper thread refrains from sending new data to the data listener.</p>
	 */
	public void pause() {
		deliverDataEvent.set(false);
	}
	
	/**
	 * <p>Looper starts sending new data again to the data listener.</p>
	 */
	public void resume() {
		deliverDataEvent.set(true);
		mDataLock.notify();
	}

	/**
	 * <p>In future we may shift modifying mask in the native code itself, so as to prevent JNI transitions.
	 * This filters what events should be sent to application. Note that, although we sent only those event
	 * for which user has set mask, however native code send all the events to java layer as of now.</p>
	 */
	public void setEventsMask(int newMask) {
		appliedMask = newMask;
	}

	/**
	 * <p>Gives the event mask currently active.</p>
	 */
	public int getEventsMask() {
		return appliedMask;
	}
}