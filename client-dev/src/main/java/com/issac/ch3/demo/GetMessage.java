package com.issac.ch3.demo;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GetMessage {
    private static final String URI = "amqp://isaac:127.0.0.1:5672";

    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(URI);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.basicQos(64);
//        pushMessage(channel);
        pullMessage(channel);

        //wait call back
        TimeUnit.SECONDS.sleep(5);
        channel.close();
        connection.close();
    }

    private static void pullMessage(Channel channel) throws Exception{
        GetResponse response = channel.basicGet("testQueue", false);
        System.out.println(new String(response.getBody()));
        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
    }

    private static void pushMessage(Channel channel) throws Exception {
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
    }
}
