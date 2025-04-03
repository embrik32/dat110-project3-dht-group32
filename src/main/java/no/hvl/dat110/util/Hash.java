package no.hvl.dat110.util;

/**
 * exercise/demo purpose in dat110
 * @author tdoy
 *
 */

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash { 
    
    public static BigInteger hashOf(String entity) {    
        BigInteger hashint = null;
        
        try {
            // we use MD5 with 128 bits digest
            MessageDigest md = MessageDigest.getInstance("MD5");
            
            // compute the hash of the input 'entity'
            byte[] digest = md.digest(entity.getBytes("UTF-8"));
            
            // convert the hash into hex format
            hashint = new BigInteger(1, digest);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        
        return hashint;
    }
    
    public static BigInteger addressSize() {
        // compute the number of bits = bitSize()
        int bitSize = bitSize();
        
        // compute the address size = 2 ^ number of bits
        return BigInteger.valueOf(2).pow(bitSize);
    }
    
    public static int bitSize() {
        int digestlen = 0;
        
        try {
            // find the digest length
            MessageDigest md = MessageDigest.getInstance("MD5");
            digestlen = md.getDigestLength();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        
        return digestlen * 8;
    }
}

