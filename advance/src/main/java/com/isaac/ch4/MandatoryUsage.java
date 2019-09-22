package com.isaac.ch4;

import com.rabbitmq.client.*;

import java.util.concurrent.TimeUnit;

public class MandatoryUsage {
    private static final String URI = "amqp://isaac:123456@127.0.0.1:5672";

    public static void main(String[] args) throws Exception{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(URI);

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
