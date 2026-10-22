import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {run} from './integration-runner.mjs';

await run(async({call,check,observations:o,product,fixture})=>{
  o.profile=await call('probe.resources');
  const directory=path.resolve(fixture,o.profile.finalClassDirectory);
  check('native final-class directory belongs to owned fixture',directory.startsWith(path.resolve(fixture)+path.sep),true);
  o.classes={};
  for(const owner of ['net.minecraft.client.Minecraft','net.minecraft.client.resources.SimpleReloadableResourceManager']){
    const file=path.join(directory,...owner.split('.'))+'.class',bytes=await fs.readFile(file);
    o.classes[owner]={file,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),size:bytes.length};
    check(owner+' final native dump exists',bytes.length>100,true);
  }
  if(product){
    check('pinned EMI artifact selected optional inspection',o.profile.admission.queued,'true');
    for(const stage of ['client','manager','late'])check(stage+' actual profile recorded',/^[0-9a-f]{64}$/.test(o.profile.admission[stage+'.signature']),true);
  }
});
