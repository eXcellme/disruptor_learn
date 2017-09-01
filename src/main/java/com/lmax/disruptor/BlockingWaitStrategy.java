/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();

    @Override
    public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        if (cursorSequence.get() < sequence) // 生产者当前游标小于给定序号，也就是无可用事件
        {
            lock.lock();
            try
            {
                while (cursorSequence.get() < sequence)
                {
                    barrier.checkAlert();
                    // 循环等待，在Sequencer中publish进行唤醒；等待消费时也会在循环中定时唤醒。
                    System.out.println(Thread.currentThread().getName() + "消费者无可用消费事件...await...");
                    processorNotifyCondition.await();
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        // 给定序号大于上一个消费者组最慢消费者（如当前消费者为第一组则和生产者游标序号比较）序号时，需要等待。不能超前消费上一个消费者组未消费完毕的事件。
        // 那么为什么这里没有锁呢？可以想一下此时的场景，代码运行至此，已能保证生产者有新事件，如果进入循环，说明上一组消费者还未消费完毕。
        // 而通常我们的消费者都是较快完成任务的，所以这里才会考虑使用Busy Spin的方式等待上一组消费者完成消费。
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        lock.lock();
        try
        {
            processorNotifyCondition.signalAll();
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public String toString()
    {
        return "BlockingWaitStrategy{" +
            "processorNotifyCondition=" + processorNotifyCondition +
            '}';
    }
}
