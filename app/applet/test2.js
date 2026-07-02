const WebSocket = require('ws');
require('dotenv').config();

const apiKey = process.env.GEMINI_API_KEY || 'fake';
const url = `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=${apiKey}`;

const ws = new WebSocket(url);

ws.on('open', () => {
  console.log('connected');
  ws.send(JSON.stringify({
    setup: {
      model: "models/gemini-2.0-flash-exp"
    }
  }));
});

ws.on('message', (data) => {
  console.log('message:', data.toString());
});

ws.on('close', (code, reason) => {
  console.log('close:', code, reason.toString());
});

ws.on('error', (err) => {
  console.error('error:', err);
});
