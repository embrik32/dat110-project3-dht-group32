/**
 * 
 */
package no.hvl.dat110.middleware;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

/**
 * @author tdoy
 *
 */
public class MutualExclusion {

    private static final Logger logger = LogManager.getLogger(MutualExclusion.class);
    private boolean CS_BUSY = false;                        // Indicate to be in critical section (accessing a shared resource)
    private boolean WANTS_TO_ENTER_CS = false;               // Indicate wanting to enter CS
    private List<Message> queueack;                          // Queue for acknowledged messages
    private List<Message> mutexqueue;                        // Queue for storing processes that are denied permission.

    private LamportClock clock;                              // Lamport clock
    private Node node;

    public MutualExclusion(Node node) throws RemoteException {
        this.node = node;
        clock = new LamportClock();
        queueack = new ArrayList<Message>();
        mutexqueue = new ArrayList<Message>();
    }

    public synchronized void acquireLock() {
        // Ensure only one process can acquire the lock at a time
        while (CS_BUSY) {
            try {
                wait(); // Wait if the critical section is already occupied
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        CS_BUSY = true;
    }

    public synchronized void releaseLocks() {
        CS_BUSY = false;
        notifyAll(); // Notify all waiting threads that the critical section is now free
    }

    public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {
        logger.info(node.nodename + " wants to access CS");

        // Clear the acknowledgment queue and mutex queue before starting
        queueack.clear();
        mutexqueue.clear();

        // Increment the clock and set the message's clock
        clock.increment();
        message.setClock(clock.getClock());

        // Set the flag that we want to enter the critical section
        WANTS_TO_ENTER_CS = true;

        // Get the list of active nodes for voting
        List<Message> activenodes = removeDuplicatePeersBeforeVoting();

        // Send the mutex request message to all active nodes
        multicastMessage(message, activenodes);

        // Wait for all responses and ensure mutual exclusion
        if (areAllMessagesReturned(activenodes.size())) {
            // If all acknowledgments are received, acquire the lock
            acquireLock();

            // Broadcast the updates to all peers
            node.broadcastUpdatetoPeers(updates);

            // Clear the mutex queue after completing the operation
            mutexqueue.clear();

            // Release the lock after completing the critical section operation
            releaseLocks();

            // Return true indicating the process acquired the lock
            return true;
        }

        // If not all acknowledgments were received, return false
        return false;
    }

    private void multicastMessage(Message message, List<Message> activenodes) throws RemoteException {
        logger.info("Number of peers to vote = " + activenodes.size());
        for (Message peer : activenodes) {
            NodeInterface stub = Util.getProcessStub(peer.getNodeName(), peer.getPort());
            if (stub != null) {
                stub.onMutexRequestReceived(message);
            }
        }
    }

    public void onMutexRequestReceived(Message message) throws RemoteException {
		// Increment the local clock
		clock.increment();
	
		// If the message is from the same node, acknowledge and call onMutexAcknowledgementReceived()
		if (message.getNodeName().equals(node.getNodeName())) {
			onMutexAcknowledgementReceived(message);
			return;
		}
	
		// Decide which case to use for processing the request
		int caseid = -1;
	
		// Transition to the correct caseid based on the conditions
		if (!CS_BUSY && !WANTS_TO_ENTER_CS) {
			// caseid=0: Receiver is not accessing the shared resource and does not want to
			caseid = 0; // Send OK to sender
		} else if (CS_BUSY) {
			// caseid=1: Receiver already has access to the resource (don't reply but queue the request)
			caseid = 1; // Queue the request
		} else if (WANTS_TO_ENTER_CS) {
			// caseid=2: Receiver wants to access the resource but is yet to - compare clocks
			if (message.getClock() < clock.getClock() || 
				(message.getClock() == clock.getClock() && message.getNodeID().compareTo(node.getNodeID()) < 0)) {
				// If the message has a lower clock or the same clock but lower node ID, grant permission
				caseid = 0; // Send OK to sender
			} else {
				// Otherwise, queue the request
				caseid = 1; // Queue the request
			}
		}
	
		// Call the decision algorithm with the calculated caseid
		doDecisionAlgorithm(message, mutexqueue, caseid);
	}
	

    public void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {
        NodeInterface stub = Util.getProcessStub(message.getNodeName(), message.getPort());

        switch (condition) {
            case 0:  // Case 0: Receiver is not accessing the shared resource and doesn't want to
                // Acknowledge the message and respond with permission (OK)
                if (stub != null) {
                    stub.onMutexAcknowledgementReceived(message);
                }
                break;

            case 1:  // Case 1: Receiver already has access to the resource
                // Queue the request, as the receiver is not replying at this moment
                queue.add(message);
                break;

            case 2:  // Case 2: Receiver wants to access the resource but is not yet in the critical section
                // Compare the clocks of the message and the receiver to determine which one should proceed
                if (message.getClock() < clock.getClock() || 
                    (message.getClock() == clock.getClock() && message.getNodeID().compareTo(node.getNodeID()) < 0)) {
                    // If the message has a lower clock or the same clock with a lower node ID, grant permission
                    if (stub != null) {
                        stub.onMutexAcknowledgementReceived(message);
                    }
                } else {
                    // Otherwise, queue the request
                    queue.add(message);
                }
                break;
        }
    }

    public void onMutexAcknowledgementReceived(Message message) throws RemoteException {
        queueack.add(message);
    }

    public void multicastReleaseLocks(Set<Message> activenodes) {
        logger.info("Releasing locks from = " + activenodes.size());
        for (Message peer : activenodes) {
            NodeInterface stub = Util.getProcessStub(peer.getNodeName(), peer.getPort());
            if (stub != null) {
                try {
                    stub.releaseLocks();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean areAllMessagesReturned(int numvoters) throws RemoteException {
        logger.info(node.getNodeName() + ": size of queueack = " + queueack.size());
        boolean allReturned = queueack.size() == numvoters;
        if (allReturned) {
            logger.info("All messages acknowledged for " + node.getNodeName());
        }
        return allReturned;
    }

    private List<Message> removeDuplicatePeersBeforeVoting() {
        List<Message> uniquepeer = new ArrayList<Message>();
        for (Message p : node.activenodesforfile) {
            boolean found = false;
            for (Message p1 : uniquepeer) {
                if (p.getNodeName().equals(p1.getNodeName())) {
                    found = true;
                    break;
                }
            }
            if (!found)
                uniquepeer.add(p);
        }
        return uniquepeer;
    }
}
