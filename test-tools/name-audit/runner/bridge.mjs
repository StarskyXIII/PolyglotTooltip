export class Bridge {
  constructor(socket) {
    this.socket = socket;
    this.pending = new Map();
    this.sequence = 0;
    this.hello = new Promise((resolve, reject) => {
      const timer = setTimeout(() => { reject(new Error('Bridge handshake timed out')); socket.close(); }, 10000);
      socket.addEventListener('message', event => {
        let message;
        try { message = JSON.parse(event.data); } catch { return; }
        if (message.method === 'hello') {
          clearTimeout(timer);
          if (message.params.protocol !== 1) { reject(new Error('Unsupported Bridge protocol')); socket.close(); }
          else resolve(message.params);
        }
        const request = this.pending.get(message.id);
        if (request) {
          clearTimeout(request.timer); this.pending.delete(message.id);
          if (message.error) request.reject(new Error(JSON.stringify(message.error)));
          else request.resolve(message.result);
        }
      });
      const fail = () => {
        clearTimeout(timer); reject(new Error('Bridge connection closed or failed'));
        for (const request of this.pending.values()) { clearTimeout(request.timer); request.reject(new Error('Bridge connection closed')); }
        this.pending.clear();
      };
      socket.addEventListener('close', fail); socket.addEventListener('error', fail);
    });
  }
  static async connect(port) {
    const bridge = new Bridge(new WebSocket(`ws://127.0.0.1:${port}`));
    await bridge.hello;
    return bridge;
  }
  call(method, params = {}, timeout = 60000) {
    const id = ++this.sequence;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error(`RPC timed out: ${method}`)); }, timeout);
      this.pending.set(id, {resolve, reject, timer});
      try { this.socket.send(JSON.stringify({jsonrpc: '2.0', id, method, params})); }
      catch (error) { clearTimeout(timer); this.pending.delete(id); reject(error); }
    });
  }
  close() { this.socket.close(); }
}
