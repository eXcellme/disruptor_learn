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


/**
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy. <br>
 * 处理序号屏障，记录了一组消费者（EventHandlerGroup）处理数据的位置以及生产者的游标位置。
 * 被EventProcessor持有，用于消费者了解生产者当前位置
 */
final class ProcessingSequenceBarrier implements SequenceBarrier
{
    private final WaitStrategy waitStrategy; // 等待可用消费时，指定的等待策略
    private final Sequence dependentSequence; // 依赖的上组消费者的序号，如果当前为第一组则为cursorSequence（即生产者发布游标序列），否则使用FixedSequenceGroup封装上组消费者序列
    private volatile boolean alerted = false; // 当触发halt时，将标记alerted为true
    private final Sequence cursorSequence; // AbstractSequencer中的cursor引用，记录当前发布者发布的最新位置
    private final Sequencer sequencer; // MultiProducerSequencer 或 SingleProducerSequencer

    public ProcessingSequenceBarrier(
        final Sequencer sequencer,
        final WaitStrategy waitStrategy,
        final Sequence cursorSequence,
        final Sequence[] dependentSequences)
    {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        if (0 == dependentSequences.length) // 依赖的上一组序列长度，第一次是0
        {
            dependentSequence = cursorSequence;
        }
        else // 将上一组序列数组复制成新数组保存，引用不变
        {
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException, TimeoutException
    {
        checkAlert();
        // 获取最大可用序号 sequence为给定序号，一般为当前序号+1，cursorSequence记录生产者最新位置，
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);

        if (availableSequence < sequence)
        {
            return availableSequence;
        }
        // 返回已发布最高的序列值，将对每个序号进行校验
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert()
    {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException
    {
        if (alerted)
        {
            throw AlertException.INSTANCE;
        }
    }
}