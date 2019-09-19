package com.isaac.ch4;

import com.rabbitmq.client.*;

import java.util.HashMap;
import java.util.Map;

public class TTLDemo {
    private static final String URI = "amqp://isaac:123456@192.168.1.103";

    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(URI);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

//        ttlQueueMessages(channel);

//        ttlMessage(channel);

        ttlQueue(channel);

        connection.close();
    }

    //two messages: one of is ttl, another is not
    private static void ttlMessage(Channel channel) throws Exception {
        AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder();
        AMQP.BasicProperties properties = builder.deliveryMode(2).expiration("6000").build();

        channel.basicPublish("testExchange","testRoutingKey", properties, "this is a ddl message!".getBytes());
        channel.basicPublish("testExchange","testRoutingKey", MessageProperties.PERSISTENT_TEXT_PLAIN, "this is not a ddl message!".getBytes());

    }

    //ttl queue all messages
    private static void ttlQueueMessages(Channel channel) throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", 6000);
        channel.queueDeclare("ttlMsgQueue", true, false, false, arguments);
        channel.queueBind("ttlMsgQueue", "testExchange", "toTtlQueueRoutingKey");

        for (int i = 0; i < 9; i++) {
            channel.basicPublish("testExchange", "toTtlQueueRoutingKey", MessageProperties.PERSISTENT_TEXT_PLAIN, "test message".getBytes());
        }
    }

    //ttl queue
    private static void ttlQueue(Channel channel) throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-expires", 30000);
        channel.queueDeclare("ttlQueue", true, false, false, arguments);
    }
}
