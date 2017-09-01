package com.coderjerry.disruptor;

import com.lmax.disruptor.util.Util;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import sun.misc.Unsafe;

/**
 * @author baofan.li
 * @Description TODO
 * @Copyright 2017 © anzhi.com
 * @Created 2017/8/15 14:05
 */
public class UnsafeTest {

  public static void main(String[] args) {

    Unsafe unsafe = Util.getUnsafe();
    Object[] arr = new Object[1024];
    int base = unsafe.arrayBaseOffset(Object[].class);
    int scale = unsafe.arrayIndexScale(Object[].class);
    int warmCount = 1000;
    int count = 10000;
    // warm up
    for(int l=0;l<warmCount;l++){
      for(int i=0;i<arr.length;i++){
        arr[i] = i;
        unsafe.putInt(arr, (long)(base + scale*i) ,i);
      }
    }
    long s1 = System.currentTimeMillis();
    for(int l=0;l<count;l++){
      for(int i=0;i<arr.length;i++){
        arr[i] = i;
      }
    }
    System.out.println("arr[] "+ Arrays.toString(arr));
    long s2 = System.currentTimeMillis();

    for(int l=0;l<count;l++){
      for(int i=0;i<arr.length;i++){
        unsafe.putInt(arr, (long)(base + scale*i) ,i*2);
      }
    }
    System.out.println("unsafe : ");
    for(int i=0;i<arr.length;i++){
      System.out.print(unsafe.getInt(arr, (long)(base + scale*i))+", ");
    }

    System.out.println();
    long s3 = System.currentTimeMillis();
    for(int l=0;l<count;l++){
      for(int i=0;i<arr.length;i++){
        unsafe.putInt(arr, (long)(base + scale*i) ,i);
      }
    }
    long s4 = System.currentTimeMillis();
    System.out.println((s2-s1) + ", "+(s3-s2) + ", "+(s4-s3));
//    printArrayScale();


  }

  private static void printArrayScale() {
    Unsafe unsafe = Util.getUnsafe();
    p(unsafe.arrayIndexScale(int[].class)); // 4
    p(unsafe.arrayIndexScale(char[].class)); // 2
    p(unsafe.arrayIndexScale(float[].class)); // 4
    p(unsafe.arrayIndexScale(double[].class)); // 8
    p(unsafe.arrayBaseOffset(int[].class)); // 16
  }

  public static void p(Object o){
    System.out.println(o);
  }

}
