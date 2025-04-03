package no.hvl.dat110.util;


/**
 * dat110
 * @author tdoy
 */

import java.math.BigInteger;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.hvl.dat110.middleware.Node;
import no.hvl.dat110.rpc.interfaces.NodeInterface;

public class Util {

    public static String activeIP = null;
    public static int numReplicas = 4;

    public static boolean checkInterval(BigInteger id, BigInteger lower, BigInteger upper) {
		if (lower.compareTo(upper) < 0) {	
			return id.compareTo(lower) >= 0 && id.compareTo(upper) <= 0;
		} else {
			return id.compareTo(lower) >= 0 || id.compareTo(upper) <= 0;
		}
	}

    public static List<String> toString(List<NodeInterface> list) throws RemoteException {
        List<String> nodestr = new ArrayList<>();
        for (NodeInterface node : list) {
            nodestr.add(((Node) node).getNodeName());
        }
        return nodestr;
    }

    public static NodeInterface getProcessStub(String name, int port) {
        try {
            Registry registry = LocateRegistry.getRegistry(port);
            return (NodeInterface) registry.lookup(name);
        } catch (NotBoundException | RemoteException e) {
            return null;
        }
    }

    public static Registry tryIPSingleMachine(String nodeip) throws NumberFormatException, RemoteException {
        String[] ips = StaticTracker.ACTIVENODES;
        List<String> iplist = Arrays.asList(ips);
        Collections.shuffle(iplist);

        for (String ip : iplist) {
            String[] parts = ip.split(":");
            if (parts.length != 2) continue;
            String ipaddress = parts[0].trim();
            int port;
            try {
                port = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            if (nodeip.equals(ipaddress)) continue;

            Registry registry = LocateRegistry.getRegistry(port);
            if (registry != null) {
                activeIP = ipaddress;
                return registry;
            }
        }
        return null;
    }

    public static Map<String, Integer> getProcesses() {
        Map<String, Integer> processes = new HashMap<>();
        processes.put("process1", 9091);
        processes.put("process2", 9092);
        processes.put("process3", 9093);
        processes.put("process4", 9094);
        processes.put("process5", 9095);
        return processes;
    }
}

