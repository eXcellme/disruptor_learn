package com.coderjerry.disruptor;

import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * 判断key是否相等使用( k1==k2 )，一般的HashMap判断key是否相等使用  (k1==null ? k2==null : k1.equals(k2))
 */
public class IdentityHashMapTest {

  public static void main(String[] args) {
    Integer i1 = 1000;
    Integer i2 = 1000;
    System.out.println(i1 == i2);
    HashMap hm = new HashMap();
    hm.put(null, "1");
    hm.put(null, "2");
    hm.put(i1, "1000a");
    hm.put(i2, "1000b");
    System.out.println(hm);
    System.out.println("hm i1:"+hm.get(i1));
    System.out.println("hm i2:"+hm.get(i2));

    IdentityHashMap ihm = new IdentityHashMap();
    ihm.put(null, "1");
    ihm.put(null, "2");
    ihm.put(i1, "1000a");
    ihm.put(i2, "1000b");
    System.out.println(ihm);
    System.out.println("ihm i1:"+ihm.get(i1));
    System.out.println("ihm i2:"+ihm.get(i2));
  }
}
