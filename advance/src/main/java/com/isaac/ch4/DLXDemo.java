package com.isaac.ch4;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 死信队列演示
 */
public class DLXDemo {
    private static final String URI = "amqp://isaac:123456@127.0.0.1:5672";

    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(URI);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        //DLX
        channel.exchangeDeclare("dlxExchange", "direct", true, false, null);
        //bind DLQ
        channel.queueDeclare("dlxQueue", true, false, false, null);
        channel.queueBind("dlxQueue", "dlxExchange", "routingKey1");

        //ttl queue
        Map<String, Object> argss = new HashMap<>();
        argss.put("x-message-ttl", 60000);
        argss.put("x-dead-letter-exchange", "dlxExchange");
        argss.put("x-dead-letter-routing-key", "routingKey1");
        channel.queueDeclare("ttlQueue", true, false, false, argss);
        channel.queueBind("ttlQueue", "testExchange", "routingKey2");

        //send message to ttlQueue
        channel.basicPublish("testExchange", "routingKey2", MessageProperties.PERSISTENT_TEXT_PLAIN, "this message should go to dlxQueue final.".getBytes());

        connection.close();
        System.out.println("end...");
    }
}
