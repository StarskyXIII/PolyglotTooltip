import {Bridge} from '../name-audit/runner/bridge.mjs';

const [port, method, json = '{}'] = process.argv.slice(2);
const bridge = await Bridge.connect(Number(port));
try {
  console.log(JSON.stringify(await bridge.call(method, JSON.parse(json), 125000), null, 2));
} finally {
  bridge.close();
}
