package com.coderjerry.disruptor;

/**
 * @author baofan.li
 * @Description TODO
 * @Copyright 2017 © anzhi.com
 * @Created 2017/8/15 13:42
 */
public class IntegerBigCountTest {

  public static void main(String[] args) {
    p(1000);
    p(128);
    p(1);
    p(10);
  }

  public static void p(Integer o){
    System.out.println(Integer.bitCount(o) + ", " + Integer.toBinaryString(o));

  }
}
