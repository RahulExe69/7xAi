import http from 'node:http';
import { timingSafeEqual } from 'node:crypto';

const HOST = process.env.HOST ?? '127.0.0.1';
const PORT = Number.parseInt(process.env.PORT ?? '20128', 10);
const TOKEN = process.env.POC_TOKEN ?? '';

function isAuthorized(request) {
  const header = request.headers.authorization ?? '';
  const prefix = 'Bearer ';
  if (!header.startsWith(prefix)) return false;

  const supplied = Buffer.from(header.slice(prefix.length));
  const expected = Buffer.from(TOKEN);
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}

function sendJson(response, status, payload) {
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'connection': 'close',
  });
  response.end(JSON.stringify(payload));
}

const server = http.createServer((request, response) => {
  if (!isAuthorized(request)) {
    sendJson(response, 401, { error: 'unauthorized' });
    return;
  }

  if (request.method === 'GET' && request.url === '/healthz') {
    sendJson(response, 200, {
      status: 'ok',
      nodeVersion: process.version,
      platform: process.platform,
      architecture: process.arch,
      pid: process.pid,
      host: HOST,
      port: PORT,
    });
    return;
  }

  if (request.method === 'POST' && request.url === '/shutdown') {
    sendJson(response, 202, { status: 'shutting_down' });
    setTimeout(() => process.exit(0), 100);
    return;
  }

  sendJson(response, 404, { error: 'not_found' });
});

server.on('error', (error) => {
  console.error('Node POC HTTP server error:', error);
  process.exit(1);
});

if (!TOKEN) {
  throw new Error('POC_TOKEN must be set before starting the server');
}

server.listen(PORT, HOST, () => {
  console.log(`7xAI embedded Node POC listening on http://${HOST}:${PORT}`);
});
