const WS_URL = `ws://${location.host}/ws/voice`;

let socket;

export function connect(onMessage) {
    socket = new WebSocket(WS_URL);

    socket.onopen = () => console.log("WebSocket подключен");
    socket.onmessage = (e) => onMessage(JSON.parse(e.data));
    socket.onerror = () => console.error("WS error");
}

export function disconnect() {
    if (socket) socket.close();
}
