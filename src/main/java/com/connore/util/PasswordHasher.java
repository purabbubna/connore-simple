package com.connore.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;

public final class PasswordHasher {
    private static final int ITERATIONS=210000;
    public String hash(String password){
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        return "pbkdf2-sha256$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS));
    }
    public boolean verify(String password,String encoded){
        try{String[] p=encoded.split("\\$");if(p.length!=4||!p[0].equals("pbkdf2-sha256"))return false;int iterations=Integer.parseInt(p[1]);if(iterations<10000||iterations>1000000)return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(p[3]),derive(password,Base64.getDecoder().decode(p[2]),iterations));
        }catch(RuntimeException e){return false;}
    }
    private byte[] derive(String password,byte[] salt,int iterations){
        var spec=new PBEKeySpec(password.toCharArray(),salt,iterations,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}finally{spec.clearPassword();}
    }
}
