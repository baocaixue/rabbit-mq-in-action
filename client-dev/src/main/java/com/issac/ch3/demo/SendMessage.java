package com.issac.ch3.demo;

import com.rabbitmq.client.*;

import java.util.HashMap;

public class SendMessage {
    private static final String URI = "amqp://isaac:123456@192.168.1.100:5672";

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(URI);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare("testExchange", "direct", true, false, false, null);
        channel.queueDeclare("testQueue", true, false, false, null);
        channel.queueBind("testQueue", "testExchange", "testRoutingKey");

//        publishSetProperties(channel);
//        publishSetHeaders(channel);
        publishSetExpiration(channel);
//        channel.basicPublish("testExchange", "testRoutingKey", MessageProperties.TEXT_PLAIN, "this is test message".getBytes());

        System.out.println("Send Successful!");
        channel.close();
        connection.close();
    }

    private static void publishSetExpiration(Channel channel) throws Exception {
        channel.basicPublish("testExchange", "testRoutingKey",
                new AMQP.BasicProperties().builder()
                .expiration("60000")//过期时间
                .build(), "this is test message".getBytes());

    }

    private static void publishSetHeaders(Channel channel) throws Exception {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("location", "here");
        headers.put("time", "today");
        channel.basicPublish("testExchange", "testRoutingKey",
                new AMQP.BasicProperties().builder()
                .headers(headers)
                .build(), "this is test message".getBytes());
    }

    private static void publishSetProperties(Channel channel) throws Exception{
        channel.basicPublish("testExchange", "testRoutingKey",
                new AMQP.BasicProperties().builder()
                .contentType("text/plain")
                .deliveryMode(2)//投递模式2，消息会被持久化
                .priority(1)//优先级
                .userId("hidden")
                .build(),
                "this is test message".getBytes());

    }
}
