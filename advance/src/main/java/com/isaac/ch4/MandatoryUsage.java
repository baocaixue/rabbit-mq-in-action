package com.isaac.ch4;

import com.rabbitmq.client.*;

import java.util.concurrent.TimeUnit;

public class MandatoryUsage {
    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(RabbitMqInfo.URI);

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, body) -> {
            String message = new String(body);
            System.out.println("message can't routing correct queue, so return : " + message);
        });

        channel.basicPublish("testExchange", "noSuchRoutingKey", true, MessageProperties.PERSISTENT_TEXT_PLAIN, "this body should return".getBytes());

        TimeUnit.SECONDS.sleep(2);//wait for fetch return
        connection.close();

    }
}
