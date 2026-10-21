const {spawnSync} = require('node:child_process');
const path = require('node:path');

const git = process.env.GIT_EXECUTABLE || 'git';
const root = path.resolve(__dirname, '..');
function run(args) {
  const result = spawnSync(git, args, {cwd: root, encoding: 'utf8'});
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr.trim() || `git ${args[0]} failed`);
  return result.stdout;
}
const files = run(['ls-files', '-z']).split('\0').filter(Boolean);
if (!files.length) throw new Error('No tracked files. Stage the project before checking it.');
const forbidden = /^(?:\.env(?:\..*)?$|config\.properties$|test-db\.properties$|(?:out|tmp|output|versions|archive|\.idea|node_modules)\/|src\/main\/webapp\/|dist\/motion\/)|\.(?:jar|class|war|log|zip|iml)$/i;
const problems = files.filter(file => file !== '.env.example' && forbidden.test(file));
for (const file of files) {
  if (!/\.(?:properties|example)$/.test(file)) continue;
  const text = run(['show', `:${file}`]);
  for (const line of text.split(/\r?\n/)) {
    const match = line.match(/^\s*(?:DB_PASSWORD|db\.password)\s*=\s*(.*?)\s*$/);
    if (!match) continue;
    const value = match[1].replace(/^['"]|['"]$/g, '');
    if (value && !/^(?:YOUR_[A-Z_]+|CHANGE_ME|changeme)$/.test(value)) problems.push(file);
  }
}
if (problems.length) throw new Error('Private or generated files are tracked: ' + [...new Set(problems)].join(', '));
run(['diff', '--cached', '--check']);
console.log(`Repository checks passed: ${files.length} tracked files; local secrets and generated output excluded.`);
