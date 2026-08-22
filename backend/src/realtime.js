import { WebSocketServer } from 'ws';

export function attachRealtime(server) {
  const wss = new WebSocketServer({ server, path: '/v1/realtime' });
  const peers = new Map();

  wss.on('connection', (socket, request) => {
    const auth = request.headers.authorization || '';
    const expected = process.env.LINKO_API_TOKEN || '';
    if (!expected || auth !== `Bearer ${expected}`) {
      socket.close(1008, 'unauthorized');
      return;
    }

    socket.once('message', raw => {
      try {
        const message = JSON.parse(raw.toString());
        if (!message.peerId || typeof message.peerId !== 'string') throw new Error('peerId required');
        peers.set(message.peerId, socket);
        socket.send(JSON.stringify({ type: 'ready', peerId: message.peerId }));
      } catch {
        socket.close(1003, 'invalid registration');
      }
    });

    socket.on('message', raw => {
      try {
        const message = JSON.parse(raw.toString());
        if (message.type !== 'signal' || !message.to || !message.payload) return;
        const target = peers.get(message.to);
        if (target?.readyState === 1) target.send(JSON.stringify({ type: 'signal', from: message.peerId, payload: message.payload }));
      } catch { /* malformed messages are ignored */ }
    });

    socket.on('close', () => {
      for (const [peerId, peer] of peers) if (peer === socket) peers.delete(peerId);
    });
  });

  return wss;
}
