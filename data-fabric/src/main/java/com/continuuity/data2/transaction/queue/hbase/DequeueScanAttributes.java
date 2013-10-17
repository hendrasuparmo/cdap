package com.continuuity.data2.transaction.queue.hbase;

import com.continuuity.common.queue.QueueName;
import com.continuuity.data2.queue.ConsumerConfig;
import com.continuuity.data2.queue.DequeueStrategy;
import com.continuuity.data2.transaction.Transaction;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.io.WritableUtils;

import javax.annotation.Nullable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Deals with dequeue scan attributes
 */
public class DequeueScanAttributes {
  private static final String ATTR_CONSUMER_CONFIG = "continuuity.queue.dequeue.consumerConfig";
  private static final String ATTR_TX = "continuuity.queue.dequeue.transaction";
  private static final String ATTR_QUEUE_NAME = "continuuity.queue.dequeue.queueName";

  public static void set(Scan scan, QueueName queueName) {
    scan.setAttribute(ATTR_QUEUE_NAME, queueName.toBytes());
  }

  public static void set(Scan scan, ConsumerConfig consumerConfig) {
    try {
      scan.setAttribute(ATTR_CONSUMER_CONFIG, toBytes(consumerConfig));
    } catch (IOException e) {
      // SHOULD NEVER happen
      throw new RuntimeException(e);
    }
  }

  public static void set(Scan scan, Transaction transaction) {
    try {
      scan.setAttribute(ATTR_TX, toBytes(transaction));
    } catch (IOException e) {
      // SHOULD NEVER happen
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static ConsumerConfig getConsumerConfig(Scan scan) {
    byte[] consumerConfigAttr = scan.getAttribute(ATTR_CONSUMER_CONFIG);
    try {
      return consumerConfigAttr == null ? null : bytesToConsumerConfig(consumerConfigAttr);
    } catch (IOException e) {
      // SHOULD NEVER happen
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static Transaction getTx(Scan scan) {
    byte[] txAttr = scan.getAttribute(ATTR_TX);
    try {
      return txAttr == null ? null : bytesToTx(txAttr);
    } catch (IOException e) {
      // SHOULD NEVER happen
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static byte[] getQueueName(Scan scan) {
    return scan.getAttribute(ATTR_QUEUE_NAME);
  }

  private static byte[] toBytes(ConsumerConfig consumerConfig) throws IOException {
    ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
    dataOutput.writeLong(consumerConfig.getGroupId());
    dataOutput.writeInt(consumerConfig.getGroupSize());
    dataOutput.writeInt(consumerConfig.getInstanceId());
    WritableUtils.writeEnum(dataOutput, consumerConfig.getDequeueStrategy());
    WritableUtils.writeString(dataOutput, consumerConfig.getHashKey());
    return dataOutput.toByteArray();
  }

  private static ConsumerConfig bytesToConsumerConfig(byte[] bytes) throws IOException {
    ByteArrayDataInput dataInput = ByteStreams.newDataInput(bytes);
    long groupId = dataInput.readLong();
    int groupSize = dataInput.readInt();
    int instanceId = dataInput.readInt();
    DequeueStrategy strategy = WritableUtils.readEnum(dataInput, DequeueStrategy.class);
    String hashKey = WritableUtils.readString(dataInput);

    return new ConsumerConfig(groupId, instanceId, groupSize, strategy, hashKey);
  }

  private static byte[] toBytes(Transaction tx) throws IOException {
    ByteArrayDataOutput dataOutput = ByteStreams.newDataOutput();
    dataOutput.writeLong(tx.getReadPointer());
    dataOutput.writeLong(tx.getWritePointer());
    dataOutput.writeLong(tx.getFirstShortInProgress());
    write(dataOutput, tx.getInProgress());
    write(dataOutput, tx.getInvalids());
    return dataOutput.toByteArray();
  }

  private static Transaction bytesToTx(byte[] bytes) throws IOException {
    ByteArrayDataInput dataInput = ByteStreams.newDataInput(bytes);
    long readPointer = dataInput.readLong();
    long writePointer = dataInput.readLong();
    long firstShortInProgress = dataInput.readLong();
    long[] inProgress = readLongArray(dataInput);
    long[] invalids = readLongArray(dataInput);
    return new Transaction(readPointer, writePointer, invalids, inProgress, firstShortInProgress);
  }

  private static void write(DataOutput dataOutput, long[] array) throws IOException {
    dataOutput.writeInt(array.length);
    for (long val : array) {
      dataOutput.writeLong(val);
    }
  }

  private static long[] readLongArray(DataInput dataInput) throws IOException {
    int length = dataInput.readInt();
    long[] array = new long[length];
    for (int i = 0; i < array.length; i++) {
      array[i] = dataInput.readLong();
    }
    return array;
  }
}
