/**
 * Created by Administrator on 2018/1/3.
 */
var socket;
if (!window.WebSocket) {
    window.WebSocket = window.MozWebSocket;
}
if (window.WebSocket) {
    socket = new WebSocket("ws://localhost:8080/ws");
    //开启连接
    socket.onopen = function (event) {
        $("#divContent").text("连接开启!");
        socket.send("{\"type\":\"online\"}");
    };
    //接收信息
    socket.onmessage = function (event) {
        var response = $("#divContent");
        try {
            var newData = JSON.parse(event.data);
            var type = newData.type;
            if (type === "online") {
                var onlineUser = $("#online");
                onlineUser.empty();
                for (var i = 0; i < newData.data.length; i++) {
                    onlineUser.append("<li><a href='#'>" + newData.data[i] + "</a></li>");
                }
                getID();
            } else {
                response.append("<p>" + newData.data +"</p>");
            }
        } catch (e) {
            console.log(e);
        }
    };
    //关闭连接
    socket.onclose = function (event) {
        $("#divContent").text("\n连接关闭!");
        socket.send("{\"type\":\"online\"}");
    };
} else {
    alert("您的浏览器不支持WebSocket!");
}
//向服务器发送消息
function send() {
    if (!window.WebSocket) {
        return;
    }
    var message = $("#txtContent").val();
    console.log(message);
    if (socket.readyState == WebSocket.OPEN) {
        if (message == "") {
            alert("发送的信息不能为空");
        } else {
            var param = {
                id: window.id,
                type: "chat",
                data: message
            }
            var jsonString = JSON.stringify(param);
            socket.send(jsonString);
            $("#txtContent").val("");
        }
    } else {
        alert("连接还未开启");
    }
}
function getID() {
    $("#online").on("click", function (event) {
        var value = event.target.textContent;
        var id = value.substring(value.length - 8, value.length);
        window.id = id;
    });
}
//清空聊天记录
function clearContent() {
    $("#divContent").text("");
}