const {spawnSync} = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const output = path.join(root, 'out', 'connore-simple-source.zip');
fs.mkdirSync(path.dirname(output), {recursive: true});
const result = spawnSync(process.env.GIT_EXECUTABLE || 'git',
  ['archive', '--format=zip', '--prefix=Connore-Simple/', `--output=${output}`, 'HEAD'],
  {cwd: root, encoding: 'utf8'});
if (result.error) throw result.error;
if (result.status !== 0) throw new Error(result.stderr.trim() || 'Commit the project before packaging it.');
console.log(`Created ${output} from committed files only.`);
