package breeze.chatroom.server;

import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.plaf.metal.MetalBorders;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by Administrator on 2017/12/18.
 * <p>
 * ChannelHandlerContext主要的功能是管理通过同一个ChannelPipeline关联的ChannelHandler之间的交互
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);

    private WebSocketServerHandshaker handshaker;

    protected void channelRead0(ChannelHandlerContext ctx, Object object) throws Exception {
        if (object instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) object);
        } else if (object instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) object);
        }

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        //如果解码失败，返回HTTP异常
        if (!request.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }
        //如果不是GET请求，返回HTTP异常
        if (request.method() != GET) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }
        //WebSocket握手
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory
                ("ws://127.0.0.1:8080/ws", null, true);
        handshaker = wsFactory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            logger.info("握手成功!");
            handshaker.handshake(ctx.channel(), request);
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        Channel incoming = ctx.channel();
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported",
                    frame.getClass().getName()));
        }

        //消息发送
        String message = ((TextWebSocketFrame) frame).text();
        Map<String, Object> result = new HashMap<String, Object>();
        logger.info(message);
        JSONObject jsonObject = JSONObject.parseObject(message);
        String value = jsonObject.getString("type");
        if ("online".equals(value)) {
            onlineUser();
        } else if ("chat".equals(value)) {
            String msg = jsonObject.getString("data");
            String id = jsonObject.getString("id");
            Channel targetChannel = WebSocketServer.channelMap.get(id);
            if (targetChannel != null) {
                result.put("type", "chat");
                result.put("data", "我：" + msg);
                incoming.writeAndFlush(new TextWebSocketFrame(jsonObject.toJSONString(result)));
                result.put("data", "[" + incoming.remoteAddress() + "]：" + msg);
                targetChannel.writeAndFlush(new TextWebSocketFrame(jsonObject.toJSONString(result)));
            } else {
                for (Channel channel : WebSocketServer.channels) {
                    if (channel != incoming) {
                        result.put("type", "chat");
                        result.put("data", "[" + incoming.remoteAddress() + "]：" + msg);
                        channel.writeAndFlush(new TextWebSocketFrame(jsonObject.toJSONString(result)));
                    } else {
                        result.put("type", "chat");
                        result.put("data", "我：" + msg);
                        channel.writeAndFlush(new TextWebSocketFrame(jsonObject.toJSONString(result)));
                    }
                }
            }
        }
    }

    private void onlineUser() {
        Map<String, Object> result = new HashMap<String, Object>();
        List<String> list = new ArrayList<String>();
        for (Channel channel : WebSocketServer.channels) {
            list.add(channel.remoteAddress() + channel.id().toString());
        }
        result.put("type", "online");
        result.put("data", list);
        WebSocketServer.channels.writeAndFlush(new TextWebSocketFrame(JSONObject.toJSONString(result)));
        logger.info(JSONObject.toJSONString(result));
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        WebSocketServer.channels.add(ctx.channel());
        //将Channel和对应的ID添加到Map中
        WebSocketServer.channelMap.put(ctx.channel().id().toString(), ctx.channel());
        logger.info("[" + ctx.channel().remoteAddress() + ctx.channel().id() + "] ==> " + "上线了!");
        for (Channel channel : WebSocketServer.channels) {
            logger.info("[" + channel.remoteAddress() + ":" + channel.id() + "] ==> " + "在线!");
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        WebSocketServer.channels.remove(ctx.channel());
        WebSocketServer.channelMap.remove(ctx.channel().id().toString());
        logger.info("[" + ctx.channel().remoteAddress() + "] ==> " + "离开");

        onlineUser();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
        logger.error(cause.getMessage());
    }
}

