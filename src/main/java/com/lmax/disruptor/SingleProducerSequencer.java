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

import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.util.Util;
// SingleProducerSequencerPad和SingleProducerSequencer中的p1~p7都是为了做缓存行填充，
// 实际数据保存在SingleProducerSequencerFields
// 数据的首尾都进行填充，保证了不管怎样从内存中加载数据到缓存行，都会只缓存有效数据
abstract class SingleProducerSequencerPad extends AbstractSequencer
{
    protected long p1, p2, p3, p4, p5, p6, p7;

    public SingleProducerSequencerPad(int bufferSize, WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }
}

abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad
{
    public SingleProducerSequencerFields(int bufferSize, WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    /**
     * Set to -1 as sequence starting point
     */
    protected long nextValue = Sequence.INITIAL_VALUE; // 生产者申请的下一个序列值
    protected long cachedValue = Sequence.INITIAL_VALUE; // 缓存上一次比较的门控序列组和next的较小值（最慢消费者序列值）
}

/**
 * <p>Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Not safe for use from multiple threads as it does not implement any barriers.</p>
 * <p>
 * <p>Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#publish(long)} is made.
 */

public final class SingleProducerSequencer extends SingleProducerSequencerFields
{
    protected long p1, p2, p3, p4, p5, p6, p7;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public SingleProducerSequencer(int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(requiredCapacity, false);
    }

    private boolean hasAvailableCapacity(int requiredCapacity, boolean doStore)
    {
        long nextValue = this.nextValue;

        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
        long cachedGatingSequence = this.cachedValue;

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
            if (doStore)
            {
                cursor.setVolatile(nextValue);  // StoreLoad fence
            }

            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            this.cachedValue = minSequence;

            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#next()
     */
    @Override
    public long next()
    {
        return next(1);
    }

    /**
     * @see Sequencer#next(int)
     */
    @Override
    public long next(int n)
    {
        if (n < 1)
        {
            throw new IllegalArgumentException("n must be > 0");
        }
        // 复制上次申请完毕的序列值
        long nextValue = this.nextValue;
        // 加n，得到本次需要申请的序列值，单个发送n为1
        long nextSequence = nextValue + n; // 本次要验证的值
        // 可能发生绕环的点，本次申请值 - 一圈长度
        long wrapPoint = nextSequence - bufferSize; // 400米跑到，小明跑了599米，小红跑了200米。小红不动，小明再跑一米就撞翻小红的那个点，叫做绕环点wrapPoint。
        long cachedGatingSequence = this.cachedValue; // 数值最小的序列值，也就是最慢消费者
        // wrapPoint 等于 cachedGatingSequence 将发生绕环行为，生产者将在环上，从后方覆盖未消费的事件。
        // 没有空坑位，将进入循环等待。如果即将生产者超一圈从后方追消费者尾（要申请的序号落了最慢消费者一圈）或 消费者追生产者尾，将进行等待。后边这种情况应该不会发生吧？
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
            cursor.setVolatile(nextValue);  // StoreLoad fence

            long minSequence;
            // 无可用坑位，只有当消费者消费，向前移动后，才能跳出循环
            // 由于外层判断使用的是缓存的消费者序列最小值，这里使用真实的消费者序列进行判断，并将最新结果在跳出while循环之后进行缓存
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue)))
            {   // 唤醒等待的消费者
//                System.out.println(Thread.currentThread().getName() + "生产者无可用坑位...notify...");
                waitStrategy.signalAllWhenBlocking();
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }
            // 当消费者向前消费后，更新缓存的最小序号
            this.cachedValue = minSequence;
        }
        // 将成功申请的序号赋值给对象实例变量
        this.nextValue = nextSequence;

        return nextSequence;
    }

    /**
     * @see Sequencer#tryNext()
     */
    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        return tryNext(1);
    }

    /**
     * @see Sequencer#tryNext(int)
     */
    @Override
    public long tryNext(int n) throws InsufficientCapacityException
    {
        if (n < 1)
        {
            throw new IllegalArgumentException("n must be > 0");
        }

        if (!hasAvailableCapacity(n, true))
        {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = this.nextValue += n;

        return nextSequence;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity()
    {
        long nextValue = this.nextValue;

        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        long produced = nextValue;
        return getBufferSize() - (produced - consumed);
    }

    /**
     * @see Sequencer#claim(long)
     */
    @Override
    public void claim(long sequence)
    {
        this.nextValue = sequence;
    }

    /**
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(long sequence)
    {   // 在发布此位置可用时，需要更新Sequencer内部游标值，并在使用阻塞等待策略时，通知等待可用事件的消费者进行继续消费
        cursor.set(sequence);
        // 除signalAllWhenBlocking外都是空实现
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(long lo, long hi)
    {
        publish(hi);
    }

    /**
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(long sequence)
    {
        return sequence <= cursor.get();
    }

    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence)
    {
        return availableSequence;
    }
}
