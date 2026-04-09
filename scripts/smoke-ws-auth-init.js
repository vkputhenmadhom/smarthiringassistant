#!/usr/bin/env node
'use strict';

const WebSocket = require('ws');

const gatewayBaseUrl = process.env.GATEWAY_BASE_URL || 'http://localhost:8000';
const defaultWsUrl = `${gatewayBaseUrl.replace(/^http:\/\//, 'ws://').replace(/^https:\/\//, 'wss://')}/graphql-ws`;
const wsUrlInput = process.env.GRAPHQL_WS_URL || defaultWsUrl;
const wsUrl = wsUrlInput.startsWith('http://')
  ? `ws://${wsUrlInput.slice('http://'.length)}`
  : wsUrlInput.startsWith('https://')
	? `wss://${wsUrlInput.slice('https://'.length)}`
	: wsUrlInput;

const authTokenInput = process.env.AUTH_TOKEN || '';
const userId = process.env.USER_ID || '';
const timeoutMs = Number(process.env.TIMEOUT_MS || '3500');

if (!authTokenInput) {
  console.error('Missing AUTH_TOKEN environment variable');
  console.error('Example: AUTH_TOKEN=<jwt> USER_ID=<id> node scripts/smoke-ws-auth-init.js');
  process.exit(1);
}

if (!userId) {
  console.error('Missing USER_ID environment variable');
  console.error('Example: AUTH_TOKEN=<jwt> USER_ID=<id> node scripts/smoke-ws-auth-init.js');
  process.exit(1);
}

const authToken = authTokenInput.startsWith('Bearer ') ? authTokenInput : `Bearer ${authTokenInput}`;
const keysToTest = ['Authorization', 'authorization', 'authToken'];

const subscriptionQuery = `subscription SmokeNewNotification($userId: ID!) {
  newNotification(userId: $userId) {
	id
  }
}`;

function smokeOneKey(authKey) {
  return new Promise((resolve, reject) => {
	const socket = new WebSocket(wsUrl, 'graphql-transport-ws');
	let acked = false;
	let subscribed = false;
	let done = false;
	let failureMessage = null;

	const finalize = (isSuccess, message) => {
	  if (done) {
		return;
	  }
	  done = true;
	  if (isSuccess) {
		resolve();
	  } else {
		failureMessage = message;
		reject(new Error(message));
	  }
	  try {
		socket.close(isSuccess ? 1000 : 4400, (message || '').slice(0, 120));
	  } catch (_error) {
		// Ignore close race conditions.
	  }
	};

	const timer = setTimeout(() => {
	  if (!acked) {
		finalize(false, `key=${authKey}: did not receive connection_ack within ${timeoutMs}ms`);
		return;
	  }
	  if (!subscribed) {
		finalize(false, `key=${authKey}: subscribe message was not sent`);
		return;
	  }
	  // Success criteria: ack + subscribe accepted without auth rejection in timeout window.
	  finalize(true, `key=${authKey}: pass`);
	}, timeoutMs);

	socket.on('open', () => {
	  socket.send(JSON.stringify({
		type: 'connection_init',
		payload: {
		  [authKey]: authToken,
		},
	  }));
	});

	socket.on('message', (rawPayload) => {
	  let payload;
	  try {
		payload = JSON.parse(String(rawPayload));
	  } catch (_error) {
		finalize(false, `key=${authKey}: received non-JSON WS frame`);
		return;
	  }

	  if (payload.type === 'connection_ack') {
		acked = true;
		subscribed = true;
		socket.send(JSON.stringify({
		  id: 'smoke-sub-1',
		  type: 'subscribe',
		  payload: {
			query: subscriptionQuery,
			variables: { userId },
		  },
		}));
		return;
	  }

	  if (payload.type === 'connection_error') {
		finalize(false, `key=${authKey}: connection_error ${JSON.stringify(payload.payload)}`);
		return;
	  }

	  if (payload.type === 'error' && payload.id === 'smoke-sub-1') {
		finalize(false, `key=${authKey}: subscription_error ${JSON.stringify(payload.payload)}`);
	  }
	});

	socket.on('error', (error) => {
	  clearTimeout(timer);
	  if (!done) {
		finalize(false, `key=${authKey}: socket_error ${error.message}`);
	  }
	});

	socket.on('close', (code, reasonBuffer) => {
	  clearTimeout(timer);
	  if (done) {
		return;
	  }

	  const reason = String(reasonBuffer || '');
	  if (!acked) {
		finalize(false, `key=${authKey}: closed before ack code=${code} reason=${reason}`);
		return;
	  }
	  if (code >= 4400) {
		finalize(false, `key=${authKey}: auth close code=${code} reason=${reason}`);
		return;
	  }
	  if (failureMessage) {
		finalize(false, failureMessage);
		return;
	  }
	  finalize(true, `key=${authKey}: pass`);
	});
  });
}

(async () => {
  for (const key of keysToTest) {
	process.stdout.write(`[WS AUTH] Testing connection_init key '${key}' ... `);
	await smokeOneKey(key);
	process.stdout.write('PASS\n');
  }
  process.stdout.write('All WS connection_init auth key checks passed.\n');
})().catch((error) => {
  console.error(`FAIL: ${error.message}`);
  process.exit(1);
});

