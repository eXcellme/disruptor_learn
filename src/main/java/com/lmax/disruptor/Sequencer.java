/*
 * Copyright 2012 LMAX Ltd.
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
 * Coordinates claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
public interface Sequencer extends Cursored, Sequenced
{
    /**
     * Set to -1 as sequence starting point
     * 序号开始位置
     */
    long INITIAL_CURSOR_VALUE = -1L;

    /**
     * Claim a specific sequence.  Only used if initialising the ring buffer to
     * a specific value.
     *
     * @param sequence The sequence to initialise too.
     * 声明指定序号，只用在初始化RingBuffer到指定值，基本上不用了
     */
    void claim(long sequence);

    /**
     * Confirms if a sequence is published and the event is available for use; non-blocking.
     *
     * @param sequence of the buffer to check
     * @return true if the sequence is available for use, false if not
     * 用非阻塞方式，确认某个序号是否已经发布且事件可用。
     */
    boolean isAvailable(long sequence);

    /**
     * Add the specified gating sequences to this instance of the Disruptor.  They will
     * safely and atomically added to the list of gating sequences.
     *
     * @param gatingSequences The sequences to add.
     * 增加门控序列（消费者序列），用于生产者在生产时避免追尾消费者
     */
    void addGatingSequences(Sequence... gatingSequences);

    /**
     * Remove the specified sequence from this sequencer.
     *
     * @param sequence to be removed.
     * @return <tt>true</tt> if this sequence was found, <tt>false</tt> otherwise.
     * 从门控序列中移除指定序列
     */
    boolean removeGatingSequence(Sequence sequence);

    /**
     * Create a new SequenceBarrier to be used by an EventProcessor to track which messages
     * are available to be read from the ring buffer given a list of sequences to track.
     *
     * @param sequencesToTrack
     * @return A sequence barrier that will track the specified sequences.
     * @see SequenceBarrier
     * 消费者使用，用于追踪指定序列（通常是上一组消费者的序列）
     */
    SequenceBarrier newBarrier(Sequence... sequencesToTrack);

    /**
     * Get the minimum sequence value from all of the gating sequences
     * added to this ringBuffer.
     *
     * @return The minimum gating sequence or the cursor sequence if
     * no sequences have been added.
     * 获取追踪序列中最小的序列
     */
    long getMinimumSequence();

    /**
     * Get the highest sequence number that can be safely read from the ring buffer.  Depending
     * on the implementation of the Sequencer this call may need to scan a number of values
     * in the Sequencer.  The scan will range from nextSequence to availableSequence.  If
     * there are no available values <code>&gt;= nextSequence</code> the return value will be
     * <code>nextSequence - 1</code>.  To work correctly a consumer should pass a value that
     * is 1 higher than the last sequence that was successfully processed.
     *
     * @param nextSequence      The sequence to start scanning from.
     * @param availableSequence The sequence to scan to.
     * @return The highest value that can be safely read, will be at least <code>nextSequence - 1</code>.
     *
     * 获取能够从环形缓冲读取的最高的序列号。依赖Sequencer的实现，可能会扫描Sequencer的一些值。扫描从nextSequence
     * 到availableSequence。如果没有大于等于nextSequence的可用值，返回值将为nextSequence-1。为了工作正常，消费者
     * 应该传递一个比最后成功处理的序列值大1的值。
     */
    long getHighestPublishedSequence(long nextSequence, long availableSequence);

    <T> EventPoller<T> newPoller(DataProvider<T> provider, Sequence... gatingSequences);
}