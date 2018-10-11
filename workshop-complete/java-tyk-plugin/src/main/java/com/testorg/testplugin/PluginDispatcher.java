package com.testorg.testplugin;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.Queue;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.GetResponse;

import java.util.concurrent.TimeoutException;
import java.io.IOException;

import coprocess.DispatcherGrpc;
import coprocess.CoprocessObject;
import coprocess.CoprocessMiniRequestObject.MiniRequestObject;
import coprocess.CoprocessReturnOverrides.ReturnOverrides;

public class PluginDispatcher extends DispatcherGrpc.DispatcherImplBase {
    private Connection conn;

    public void setAMQPConnection(Connection conn) {
        this.conn = conn;
    };

    @Override
    public void dispatch(CoprocessObject.Object obj,
            io.grpc.stub.StreamObserver<CoprocessObject.Object> responseObserver) {
        CoprocessObject.Object modifiedRequest = null;
        MiniRequestObject req = obj.getRequest();
        String url = req.getUrl();
        String path = url.replace("/todos", "");
        String idString = path.replace("/", "");
        String alias = "5ba873e84ea4614f4c7dfe2f";
        String routingKey = "";
        switch (req.getMethod()) {
            case "GET":
                if(path.equals("/")) {
                    routingKey = "index";
                    // todo.User = obj.Session.Alias
                } else {
                    routingKey = "show";
                    // todo.ID = idString
                }
                break;
            case "POST":
                routingKey = "store";
                // _ = json.Unmarshal([]byte(obj.Request.Body), &todo)
                break;
            default:
                break;
        }

        try {
            CoprocessObject.Object newObj = this.handleRPC(routingKey, "", obj);
            responseObserver.onNext(newObj);
        } catch(Exception e) {
            System.out.println("Couldn't perform RPC call");
        }

        responseObserver.onCompleted();
    }

    private CoprocessObject.Object handleRPC(String routingKey, String body, CoprocessObject.Object obj) throws IOException, TimeoutException {
        // Initialize AMQP channel:
        Channel ch = this.conn.createChannel();
        Queue.DeclareOk result = ch.queueDeclare("", true, false, false, null);
        byte[] msg = "{\"user\":\"5ba873e84ea4614f4c7dfe2f\",\"todo\":\"\",\"complete\":false,\"created_at\":\"0001-01-01T00:00:00Z\"}".getBytes();
        BasicProperties props = new BasicProperties.Builder()
                .contentType("application/json")
                .replyTo(result.getQueue())
                .build();
        ch.basicPublish("todos", routingKey, props, msg);
        String bodyStr = "";
        while(true) {
            GetResponse response = ch.basicGet(result.getQueue(), true);
            if ( response != null ) {
                bodyStr = new String(response.getBody(), "UTF-8");
                break;
            };
        };
        CoprocessObject.Object.Builder builder = obj.toBuilder();
        ReturnOverrides.Builder returnOverrides = builder.getRequestBuilder().getReturnOverridesBuilder();
        returnOverrides.setResponseCode(200);
        returnOverrides.setResponseError(bodyStr);
        return builder.build();
    }

    CoprocessObject.Object MyPreHook(CoprocessObject.Object request) {
        CoprocessObject.Object.Builder builder = request.toBuilder();
        builder.getRequestBuilder().putSetHeaders("customheader", "customvalue");
        return builder.build();
    }
}

