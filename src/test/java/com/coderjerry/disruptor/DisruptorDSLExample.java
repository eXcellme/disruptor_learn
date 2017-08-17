package com.coderjerry.disruptor;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * @author baofan.li
 * @Description TODO
 * @Copyright 2017 © anzhi.com
 * @Created 2017/8/14 13:12
 */
public class DisruptorDSLExample {
  class ExampleEvent{
    Object data ;
    Object ext;
    @Override
    public String toString() {
      return "DisruptorDSLExample[data:"+this.data+",ext:"+ext+"]";
    }
  }



  class ExampleEventFactory implements EventFactory<ExampleEvent>{

    @Override
    public ExampleEvent newInstance() {
      return new ExampleEvent();
    }
  }

  static class IntToExampleEventTranslator implements EventTranslatorOneArg<ExampleEvent, Integer>{

    static final IntToExampleEventTranslator INSTANCE = new IntToExampleEventTranslator();

    @Override
    public void translateTo(ExampleEvent event, long sequence, Integer arg0) {
      event.data = arg0 ;
      System.err.println("put data "+sequence+", "+event+", "+arg0);
    }
  }

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

  public void createDisruptor(final CountDownLatch latch){
    disruptor = new Disruptor<ExampleEvent>(
        new ExampleEventFactory(), 8, threadFactory, ProducerType.MULTI,
        new BlockingWaitStrategy());
    EventHandler journalHandler = new EventHandler() {
      @Override
      public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
        Thread.sleep(8);
        System.out.println(Thread.currentThread().getId() + " process journal " + event + ", seq: " + sequence);
      }
    };

    EventHandler replicateHandler = new EventHandler() {
      @Override
      public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
        Thread.sleep(10);
        System.out.println(Thread.currentThread().getId() + " process replication " + event + ", seq: " + sequence);
      }
    };

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

    EventHandler resultHandler = new EventHandler() {
      @Override
      public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
        System.out.println(Thread.currentThread().getId() + " =========== result " + event + ", seq: " + sequence);
        latch.countDown();
      }
    };

    disruptor
        .handleEventsWith(
          new EventHandler[]{
//              journalHandler,
              unmarshallHandler,
//              replicateHandler
          }
        )
        .then(resultHandler);

    disruptor.start();

  }

  public void shutdown(){
    disruptor.shutdown();
  }

  public Disruptor getDisruptor(){
    return disruptor;
  }


  public static void main(String[] args) {
    DisruptorDSLExample disruptorDSLExample = new DisruptorDSLExample();
    final CountDownLatch latch = new CountDownLatch(20);
    disruptorDSLExample.createDisruptor(latch);

    final Disruptor disruptor = disruptorDSLExample.getDisruptor();

    Thread produceThread0 = new Thread(new Runnable() {
      @Override
      public void run() {
        int x = 0;
        while(x++ < 10){
          disruptor.publishEvent(IntToExampleEventTranslator.INSTANCE, x);
        }
      }
    });

    Thread produceThread1 = new Thread(new Runnable() {
      @Override
      public void run() {
        int x = 0;
        while(x++ < 10){
          disruptor.publishEvent(IntToExampleEventTranslator.INSTANCE, x);

        }
      }
    });


    produceThread0.start();
    produceThread1.start();

    try {
      latch.await();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    disruptorDSLExample.shutdown();
  }

}