// Deliberately small stdio-only MCP implementation. No remote listener or package install.
export const VERSIONS = ['2025-11-25', '2025-06-18', '2025-03-26', '2024-11-05'];

export function validate(value, schema, path = 'arguments') {
  if (schema.type === 'object') {
    if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${path} must be an object`);
    for (const key of schema.required ?? []) if (!(key in value)) throw new Error(`${path}.${key} is required`);
    for (const [key, field] of Object.entries(value)) {
      if (!schema.properties?.[key]) throw new Error(`Unknown ${path}.${key}`);
      validate(field, schema.properties[key], `${path}.${key}`);
    }
  } else if (schema.type === 'string') {
    if (typeof value !== 'string' || value.length > (schema.maxLength ?? 256) || value.length < (schema.minLength ?? 1)) throw new Error(`${path} must be a bounded string`);
  } else if (schema.type === 'number' || schema.type === 'integer') {
    if (typeof value !== 'number' || !Number.isFinite(value) || (schema.type === 'integer' && !Number.isInteger(value))) throw new Error(`${path} must be ${schema.type}`);
    if (value < (schema.minimum ?? -Infinity) || value > (schema.maximum ?? Infinity)) throw new Error(`${path} is out of range`);
  } else if (schema.type === 'boolean' && typeof value !== 'boolean') throw new Error(`${path} must be boolean`);
  if (schema.enum && !schema.enum.includes(value)) throw new Error(`${path} must be one of ${schema.enum.join(', ')}`);
}

export function createProtocol(definitions, call) {
  let phase = 'new';
  return async function handle(message) {
    if (!message || typeof message !== 'object' || Array.isArray(message) || message.jsonrpc !== '2.0'
      || typeof message.method !== 'string' || (message.id !== undefined && typeof message.id !== 'string' && typeof message.id !== 'number')) {
      return { jsonrpc: '2.0', id: null, error: { code: -32600, message: 'Invalid Request' } };
    }
    const notification = message.id === undefined;
    const fail = (code, text) => notification ? undefined : { jsonrpc: '2.0', id: message.id, error: { code, message: text } };
    const reply = result => notification ? undefined : { jsonrpc: '2.0', id: message.id, result };
    if (message.method === 'initialize') {
      if (notification || phase !== 'new') return fail(-32600, 'Initialization must be the first request');
      if (!message.params || typeof message.params.protocolVersion !== 'string' || !message.params.clientInfo || !message.params.capabilities) return fail(-32602, 'Missing initialization parameters');
      phase = 'initializing';
      return reply({ protocolVersion: VERSIONS.includes(message.params.protocolVersion) ? message.params.protocolVersion : VERSIONS[0],
        capabilities: { tools: { listChanged: false } }, serverInfo: { name: 'hytale-civ-local', version: '0.1.0' },
        instructions: 'Local development only. Use hytale_status to check the localhost command bridge and hytale_command to execute native Hytale server-console commands with direct output. Build/deploy/start/stop only own processes started by this MCP instance.' });
    }
    if (message.method === 'notifications/initialized' && notification && phase === 'initializing') { phase = 'ready'; return; }
    if (message.method === 'ping') return reply({});
    if (notification) return;
    if (phase !== 'ready') return fail(-32000, 'Complete MCP initialization first');
    if (message.method === 'tools/list') return reply({ tools: definitions });
    if (message.method !== 'tools/call') return fail(-32601, 'Method not found');
    const definition = definitions.find(tool => tool.name === message.params?.name);
    if (!definition) return fail(-32602, 'Unknown tool');
    try { validate(message.params.arguments ?? {}, definition.inputSchema); }
    catch (error) { return fail(-32602, error.message); }
    try {
      const result = await call(definition.name, message.params.arguments ?? {});
      return reply({ content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] });
    } catch (error) {
      return reply({ isError: true, content: [{ type: 'text', text: error.message }] });
    }
  };
}
