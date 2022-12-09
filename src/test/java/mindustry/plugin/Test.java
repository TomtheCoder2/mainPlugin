package mindustry.plugin;

import java.security.MessageDigest;
import java.util.Base64;

public class Test {
    public static void main(String[] args) {
        String[] algs = new String[]{"MD5", "SHA-1", "SHA-256"};
        for (String a : algs) {
            System.out.println(a);
            System.out.println(calculatePcalculatePhash("hello", a));
            System.out.println(calculatePcalculatePhash("hello2", a));
            System.out.println(calculatePcalculatePhash("hello3", a));
            System.out.println(calculatePcalculatePhash("adsfasdfasdf", a));
            System.out.println(calculatePcalculatePhash("V16eNGI0LWgAAAAA6+gfaA==", a));
        }
    }

    public static String calculatePcalculatePhash(String s, String alg) {
        s = s.replace("==", "").replace("AAAAA", "");
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(alg);
            messageDigest.update(s.getBytes());
            return Base64.getEncoder().encodeToString(messageDigest.digest());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
