package com.isaac.ch1;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 消费者客户端代码示例
 */
public class RabbitConsumer {
    private static final String QUEUE_NAME = "queue_demo";
    private static final String IP_ADDRESS = "127.0.0.1";
    private static final int PORT = 5672;
    private static final String USER_NAME = "guest";
    private static final String PASSWORD = "guest";

    public static void main(String[] args) throws Exception{
        Address address = new Address(IP_ADDRESS, PORT);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(USER_NAME);
        factory.setPassword(PASSWORD);
        //这里连接与生产者是有差异的
        //创建连接
        Connection connection = factory.newConnection(new Address[]{address});
        //创建信道
        Channel channel = connection.createChannel();
        //设置客户端最多接收未被ack的消息个数
        channel.basicQos(64);
        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                System.out.println("receive message: " + new String(body));

                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                channel.basicAck(envelope.getDeliveryTag(), false);
            }
        };
        channel.basicConsume(QUEUE_NAME, consumer);

        //等待回调函数执行完毕后，关闭资源
        TimeUnit.SECONDS.sleep(5);
        channel.close();
        connection.close();

    }
}
