package com.issac.ch3.demo;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GetMessage {
    private static final String URI = "amqp://isaac:123456@192.168.1.100:5672";

    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(URI);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.basicQos(64);
        channel.basicConsume("testQueue",false, "testConsumerTag",  new DefaultConsumer(channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                System.out.println("Get Message: " + new String(body));
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                channel.basicAck(envelope.getDeliveryTag(),false);
            }
        });

        //wait call back
        TimeUnit.SECONDS.sleep(5);
        channel.close();
        connection.close();
    }
}
