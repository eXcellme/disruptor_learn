package com.coderjerry.disruptor;

import com.lmax.disruptor.util.Util;
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
    p(unsafe.arrayIndexScale(int[].class));
    p(unsafe.arrayIndexScale(char[].class));
    p(unsafe.arrayIndexScale(float[].class));
    p(unsafe.arrayIndexScale(double[].class));
    p(unsafe.arrayBaseOffset(int[].class));


  }

  public static void p(Object o){
    System.out.println(o);
  }

}
