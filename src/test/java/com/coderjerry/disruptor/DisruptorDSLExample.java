package com.coderjerry.disruptor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;

/**
 * Disruptor例子
 * jerry li
 */
public class DisruptorDSLExample {

  /**
   * 用户自定义事件
   */
  class ExampleEvent{
    Object data ;
    Object ext;
    @Override
    public String toString() {
      return "DisruptorDSLExample[data:"+this.data+",ext:"+ext+"]";
    }
  }

  /**
   * 用户事件工厂，实现EventFactory接口，用于初始化事件对象
   */
  class ExampleEventFactory implements EventFactory<ExampleEvent>{

    @Override
    public ExampleEvent newInstance() {
      return new ExampleEvent();
    }
  }

  /**
   * 生产者在发布事件时，使用翻译器将原始对象设置到RingBuffer的对象中
   */
  static class IntToExampleEventTranslator implements EventTranslatorOneArg<ExampleEvent, Integer>{

    static final IntToExampleEventTranslator INSTANCE = new IntToExampleEventTranslator();

    @Override
    public void translateTo(ExampleEvent event, long sequence, Integer arg0) {
      event.data = arg0 ;
      System.err.println("put data "+sequence+", "+event+", "+arg0);
    }
  }

  // 用于事件处理(EventProcessor)的线程工厂
  ThreadFactory threadFactory =
      new ThreadFactoryBuilder()
          .setNameFormat("disruptor-executor-%d")
          .setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
              System.out.println("Thread " + t + "throw " + e);
              e.printStackTrace();
            }
          })

          .build();
  Disruptor disruptor = null;

  // 初始化Disruptor
  public void createDisruptor(final CountDownLatch latch){

    disruptor = new Disruptor<ExampleEvent>(
        new ExampleEventFactory(),  // 用于创建环形缓冲中对象的工厂
        8,  // 环形缓冲的大小
        threadFactory,  // 用于事件处理的线程工厂
        ProducerType.MULTI, // 生产者类型，单vs多生产者
        new BlockingWaitStrategy()); // 等待环形缓冲游标的等待策略，这里使用阻塞模式，也是Disruptor中唯一有锁的地方

    // 消费者模拟-日志处理
    EventHandler journalHandler = new EventHandler() {
      @Override
      public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
        Thread.sleep(8);
        System.out.println(Thread.currentThread().getId() + " process journal " + event + ", seq: " + sequence);
      }
    };

    // 消费者模拟-复制处理
    EventHandler replicateHandler = new EventHandler() {
      @Override
      public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
        Thread.sleep(10);
        System.out.println(Thread.currentThread().getId() + " process replication " + event + ", seq: " + sequence);
      }
    };

    // 消费者模拟-解码处理
    EventHandler unmarshallHandler = new EventHandler() { // 最慢
      @Override
      public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
        Thread.sleep(1*1000);
        if(event instanceof ExampleEvent){
          ((ExampleEvent)event).ext = "unmarshalled ";
        }
        System.out.println(Thread.currentThread().getId() + " process unmarshall " + event + ", seq: " + sequence);

      }
    };

    // 消费者处理-结果上报，只有执行完以上三种后才能执行此消费者
    EventHandler resultHandler = new EventHandler() {
      @Override
      public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
        System.out.println(Thread.currentThread().getId() + " =========== result " + event + ", seq: " + sequence);
        latch.countDown();
      }
    };
    // 定义消费链，先并行处理日志、解码和复制，再处理结果上报
    disruptor
        .handleEventsWith(
          new EventHandler[]{
              journalHandler,
              unmarshallHandler,
              replicateHandler
          }
        )
        .then(resultHandler);
    // 启动Disruptor
    disruptor.start();

  }

  public void shutdown(){
    disruptor.shutdown();
  }

  public Disruptor getDisruptor(){
    return disruptor;
  }

  public static void main(String[] args) {
    final int events = 20; // 必须为偶数
    DisruptorDSLExample disruptorDSLExample = new DisruptorDSLExample();
    final CountDownLatch latch = new CountDownLatch(events);

    disruptorDSLExample.createDisruptor(latch);

    final Disruptor disruptor = disruptorDSLExample.getDisruptor();
    // 生产线程0
    Thread produceThread0 = new Thread(new Runnable() {
      @Override
      public void run() {
        int x = 0;
        while(x++ < events / 2){
          disruptor.publishEvent(IntToExampleEventTranslator.INSTANCE, x);
        }
      }
    });
    // 生产线程1
    Thread produceThread1 = new Thread(new Runnable() {
      @Override
      public void run() {
        int x = 0;
        while(x++ < events / 2){
          disruptor.publishEvent(IntToExampleEventTranslator.INSTANCE, x);

        }
      }
    });

    produceThread0.start();
    produceThread1.start();
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    disruptorDSLExample.shutdown();

//    try {
//      latch.await();
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//    }


  }

}