package com.isaac.ch4;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 发送方确认机制
 *
 * 需要确保交换器绑定了队列，可以使用alternate或mandatory确保可靠性
 */
public class PublisherConfirmDemo {
    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RabbitMqInfo.URI);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        //将信道设置为publisher confirm模式
        channel.confirmSelect();
//        syncPublishConfirm(channel);
//        batchPublishConfirm(channel);
        asyncPublishConfirm(channel);
        connection.close();
    }

    private static void syncPublishConfirm(Channel channel) throws Exception {
        channel.basicPublish("", "testQueue", null, "publisher confirm test".getBytes());
        if(channel.waitForConfirms()){
            System.out.println("send successful!");
        } else {
            System.out.println("send failed!");
        }
    }

    private static void batchPublishConfirm(Channel channel) throws Exception{
        final int batchCount = 10;
        final List<String> cache = new ArrayList<>();
        int msgCount = 0;

        while (true){
            String msg = "batch confirm test" + msgCount;
            cache.add(msg);
            channel.basicPublish("", "testQueue", null, msg.getBytes());
            if(++msgCount >= batchCount) {
                msgCount = 0;
                if(channel.waitForConfirms()) {
                    System.out.println("current batch already send!");
                    cache.clear();
                } else {
                    System.out.println("current batch send failed!");
                }
                break;
            }
        }
    }

    private static void asyncPublishConfirm(Channel channel) throws Exception {
        final SortedSet<Long> confirmSet = new ConcurrentSkipListSet<>();
        channel.addConfirmListener(new ConfirmListener() {
            @Override
            public void handleAck(long deliveryTag, boolean multiple) throws IOException {
                System.out.println("Ack, SeqNo: " + deliveryTag + ", multiple: " + multiple);
                if(multiple) {
                    confirmSet.headSet(deliveryTag - 1).clear();
                } else {
                    confirmSet.remove(deliveryTag);
                }
            }

            @Override
            public void handleNack(long deliveryTag, boolean multiple) throws IOException {
                System.out.println("Nack, SeqNo: " + deliveryTag + ", multiple: " + multiple);
                if (multiple) {
                    confirmSet.headSet(deliveryTag -1).clear();
                } else {
                    confirmSet.remove(deliveryTag);
                }
            }
        });

        //演示一直发送消息
        while (true) {
            long nextSeqNo = channel.getNextPublishSeqNo();
            String msg = "async message test" + nextSeqNo;
            channel.basicPublish("", "testQueue", null, msg.getBytes());
            confirmSet.add(nextSeqNo);
            if(nextSeqNo >= 1000)
                break;
        }
    }
}
