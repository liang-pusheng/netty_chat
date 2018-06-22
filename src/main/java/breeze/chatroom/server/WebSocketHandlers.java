package breeze.chatroom.server;

import benchmarkserver.WebSocketServerHandler;
import breeze.chatroom.dbutil.DBUtil;
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

import java.util.*;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by Administrator on 2017/12/18.
 */
public class WebSocketHandlers extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);

    private WebSocketServerHandshaker handshaker;

    private static String name = "游客";

    public static final String GROUP = "group";

    public static final String PERSONAL = "personal";

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

        sendMsg(frame, incoming);
    }

    private void sendMsg(WebSocketFrame frame, Channel incoming) {
        //连接数据库
        DBUtil.connectDB("chat", "root", "root");
        String sql = "insert into chat_msg(from_name,to_name,content,isread,date,time,type) values (?, ?, ?, ?, ?, ?, ?)";
        //消息发送
        String message = ((TextWebSocketFrame) frame).text();
        Map<String, Object> result = new HashMap<String, Object>();
        logger.info(message);
        JSONObject jsonObject = JSONObject.parseObject(message);
        String type = jsonObject.getString("type");
        if ("online".equals(type)) {
            onlineUser();
        }
        if ("chat".equals(type)) {
            String msg = jsonObject.getString("data");
            String name = jsonObject.getString("name");
            Channel targetChannel = WebSocketServer.channelMap.get(name);
            if (targetChannel != null) {
                result.put("type", "chat");
                result.put("data", "我：" + msg);
                incoming.writeAndFlush(new TextWebSocketFrame(JSONObject.toJSONString(result)));
                result.put("data", getNickname(incoming) + "：" + msg);
                DBUtil.executeSql(sql, getNickname(incoming), getNickname(targetChannel), msg, 1,
                        new Date(), new Date(), PERSONAL);
                targetChannel.writeAndFlush(new TextWebSocketFrame(JSONObject.toJSONString(result)));
            } else {
                for (Channel channel : WebSocketServer.channels) {
                    if (channel != incoming) {
                        name = getNickname(incoming);
                        result.put("type", "chat");
                        result.put("data", name + "：" + msg);
                        DBUtil.executeSql(sql, name, getNickname(channel), msg, 1,
                                new Date(), new Date(), GROUP);
                        channel.writeAndFlush(new TextWebSocketFrame(JSONObject.toJSONString(result)));
                    } else {
                        result.put("type", "chat");
                        result.put("data", "我：" + msg);
                        name = getNickname(channel);
                        DBUtil.executeSql(sql, name, getNickname(incoming), msg, 1,
                                new Date(), new Date(), GROUP);
                        channel.writeAndFlush(new TextWebSocketFrame(JSONObject.toJSONString(result)));
                    }
                }
            }
        }
    }

    private String getNickname(Channel channel) {
        Set<String> keys = WebSocketServer.channelMap.keySet();
        for (String key : keys) {
            Channel channel1 = WebSocketServer.channelMap.get(key);
            if (channel1 == channel) {
                return key;
            }
        }
        return null;
    }

    private void onlineUser() {
        Map<String, Object> result = new HashMap<String, Object>();
        List<String> list = new ArrayList<String>();
        for (String s : WebSocketServer.channelMap.keySet()) {
            list.add(s);
        }
        System.out.println(WebSocketServer.channelMap);
        result.put("type", "online");
        result.put("data", list);
        WebSocketServer.channels.writeAndFlush(new TextWebSocketFrame(JSONObject.toJSONString(result)));
        logger.info("onlineUser：" + JSONObject.toJSONString(result));
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
        int i = 0;
        name = name + i;
        i++;
        WebSocketServer.channelMap.put(name, ctx.channel());
        logger.info("[" + name + "==> " + "上线了!");
        for (String key : WebSocketServer.channelMap.keySet()) {
            logger.info("[" + key + "==> " + "在线!");
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        WebSocketServer.channels.remove(ctx.channel());
        Set<String> keys = WebSocketServer.channelMap.keySet();
        for (String key : keys) {
            Channel channel = WebSocketServer.channelMap.get(key);
            if (channel == ctx.channel()) {
                WebSocketServer.channelMap.remove(key);
                logger.info("Remove" + name + " ==> " + "离开");
                onlineUser();
                System.out.println("Remove：" + WebSocketServer.channelMap);
                return;
            }
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
        logger.error(cause.getMessage());
    }
}
