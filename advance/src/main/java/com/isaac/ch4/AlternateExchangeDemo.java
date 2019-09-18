package com.isaac.ch4;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 备份交换器
 */
public class AlternateExchangeDemo {
    private static final String URI = "amqp://isaac:123456@192.168.1.103";

    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(URI);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        Map<String, Object> args_ = new HashMap<>();
        args_.put("alternate-exchange", "backUpExchange");

        //备份的交换器和队列定义
        channel.exchangeDeclare("backUpExchange", "fanout", true, false, null);
        channel.queueDeclare("backUpQueue", true, false, false, null);
        channel.queueBind("backUpQueue", "backUpExchange", "backUpRoutingKey");

        //这个队列没有绑定任何队列
        channel.exchangeDeclare("noQueueExchange", "direct", true, false, false, args_);

        //send message
        channel.basicPublish("noQueueExchange", "", MessageProperties.PERSISTENT_TEXT_PLAIN, "i should go to backUpQueue".getBytes());

        connection.close();
    }
}
