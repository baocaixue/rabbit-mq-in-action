package com.issac.ch3.demo;

import com.rabbitmq.client.*;

public class CloseDemo {
    private static final String URI = "amqp://isaac:123456@192.168.1.101:5672";

    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(URI);
        Connection connection = factory.newConnection();

        connection.addShutdownListener(cause -> System.out.println("close reason: " + cause.getReason()));

//        Channel channel = connection.createChannel();
//        GetResponse response = channel.basicGet("testQueue", false);
//        System.out.println(new String(response.getBody()));
//        channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
        System.out.println("now close connection.");
        connection.close();
    }
}
