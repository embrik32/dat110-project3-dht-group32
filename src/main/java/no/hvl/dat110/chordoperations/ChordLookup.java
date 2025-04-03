/**
 * 
 */
package no.hvl.dat110.chordoperations;

import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.middleware.Node;
import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.Util;

/**
 * ChordLookup class handles Chord's finger table, successor and predecessor lookup logic.
 */
public class ChordLookup {

    private static final Logger logger = LogManager.getLogger(ChordLookup.class);
    private Node node;

    public ChordLookup(Node node) {
        this.node = node;
    }

    /**
     * This method finds the successor of a key in the Chord ring.
     * It will recursively find the successor if needed.
     * @param key the key to look up
     * @return the NodeInterface of the successor
     * @throws RemoteException if a remote communication error occurs
     */
    public NodeInterface findSuccessor(BigInteger key) throws RemoteException {
        // Hent successor for denne noden
        NodeInterface successor = node.getSuccessor();
        
        // Sjekk om nøkkelen ligger innenfor intervallet
        if (Util.checkInterval(key, node.getNodeID().add(BigInteger.ONE), successor.getNodeID())) {
            return successor; // Returner successor hvis nøkkelen er i intervallet
        } else {
            // Hvis ikke, kall findHighestPredecessor for å finne riktig node
            NodeInterface highestPred = findHighestPredecessor(key);
            return highestPred.findSuccessor(key); // Rekursiv kallet til successor
        }
    }

    /**
     * This method finds the highest predecessor node for the given key.
     * @param ID the key to find the highest predecessor for
     * @return the NodeInterface of the highest predecessor
     * @throws RemoteException if a remote communication error occurs
     */
    private NodeInterface findHighestPredecessor(BigInteger ID) throws RemoteException {
        // Hent finger tabellen for denne noden
        List<NodeInterface> fingerTable = node.getFingerTable();
        
        // Start fra siste oppføring i finger tabellen og iterer bakover
        for (int i = fingerTable.size() - 1; i >= 0; i--) {
            NodeInterface finger = fingerTable.get(i);
            BigInteger fingerID = finger.getNodeID();
            
            // Sjekk at fingerID er i intervallet {nodeID + 1, ..., ID - 1}
            if (Util.checkInterval(fingerID, node.getNodeID().add(BigInteger.ONE), ID.subtract(BigInteger.ONE))) {
                return finger; // Returner fingeren som er nærmest ID
            }
        }
        
        // Hvis ingen finger passer, returner denne noden som en fallback
        return node;
    }

    /**
     * This method copies the file keys from the successor to the current node.
     * It ensures that keys <= node ID are copied.
     * @param succ the successor node from which keys will be copied
     */
    public void copyKeysFromSuccessor(NodeInterface succ) {
        Set<BigInteger> filekeys;
        try {
            // Hvis noden og successor er de samme, gjør ingenting
            if (succ.getNodeName().equals(node.getNodeName())) {
                return;
            }
            
            logger.info("Copying file keys that are <= " + node.getNodeName() + " from successor " + succ.getNodeName() + " to " + node.getNodeName());
            
            filekeys = new HashSet<>(succ.getNodeKeys()); // Kopier filnøkler fra successor
            BigInteger nodeID = node.getNodeID();
            
            for (BigInteger fileID : filekeys) {
                if (fileID.compareTo(nodeID) <= 0) {
                    logger.info("fileID=" + fileID + " | nodeID= " + nodeID);
                    node.addKey(fileID);  // Tildel filen til denne noden
                    
                    Message msg = succ.getFilesMetadata().get(fileID); // Hent filmetadata fra successor
                    node.saveFileContent(msg.getNameOfFile(), fileID, msg.getBytesOfFile(), msg.isPrimaryServer()); // Lagre filinnholdet
                    succ.removeKey(fileID); // Fjern filnøkkelen fra successor
                    succ.getFilesMetadata().remove(fileID); // Fjern filen fra metadataene
                }
            }
            
            logger.info("Finished copying file keys from successor " + succ.getNodeName() + " to " + node.getNodeName());
        } catch (RemoteException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * This method handles the notification of a new predecessor.
     * It verifies whether the new predecessor is valid and accepts it if so.
     * @param pred_new the new predecessor node
     * @throws RemoteException if a remote communication error occurs
     */
    public void notify(NodeInterface pred_new) throws RemoteException {
        NodeInterface pred_old = node.getPredecessor();
        
        // Hvis den gamle predecessor er null, godta den nye som predecessor
        if (pred_old == null) {
            node.setPredecessor(pred_new);
            return;
        } 
        // Hvis den nye predecessor er den samme som denne noden, fjern predecessor
        else if (pred_new.getNodeName().equals(node.getNodeName())) {
            node.setPredecessor(null);
            return;
        } else {
            BigInteger nodeID = node.getNodeID();
            BigInteger pred_oldID = pred_old.getNodeID();
            BigInteger pred_newID = pred_new.getNodeID();
            
            // Sjekk at pred_new er mellom pred_old og denne noden, og godta den som den nye predecessor
            boolean cond = Util.checkInterval(pred_newID, pred_oldID.add(BigInteger.ONE), nodeID.add(BigInteger.ONE));
            if (cond) {
                node.setPredecessor(pred_new);
            }
        }
    }
}
