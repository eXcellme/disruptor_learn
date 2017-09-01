package com.coderjerry.disruptor;

/**
 * 缓存行测试，源自http://cenalulu.github.io/linux/all-about-cpu-cache/ <b/>
 * 第一段代码总是比第二段快
 * baofan.li
 * 2017/8/30 14:24
 */
public class CacheLineTest {

  public static void main(String[] args) {
    int n = 1000;
    int[][] arr = new int[n][n];
    long s1 = System.currentTimeMillis();
    // 缓存行友好
    for(int i = 0; i < n; i++) {
      for(int j = 0; j < n; j++) {
        arr[i][j] = 1;
      }
    }
    long s2 = System.currentTimeMillis();
    // 缓存行不友好
    for(int i = 0; i < n; i++) {
      for(int j = 0; j < n; j++) {
        arr[j][i] = 1;
      }
    }
    long s3 = System.currentTimeMillis();
    System.out.println((s2-s1)+", "+(s3-s1));

  }
}
