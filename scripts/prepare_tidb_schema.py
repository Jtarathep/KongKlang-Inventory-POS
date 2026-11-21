"""Generate a fresh, non-destructive TiDB schema from the reviewed MySQL schema."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = (root / 'database/schema.sql').read_text(encoding='utf-8')
sql = re.sub(r'/\*.*?\*/', '', source, flags=re.S)
sql = re.sub(r'^\s*--.*$', '', sql, flags=re.M)
sql = sql.replace("_tis620'", "_utf8mb4'")
sql = sql.replace('utf8mb4_0900_ai_ci', 'utf8mb4_unicode_ci')
statements = [s.strip() for s in sql.split(';') if s.strip()]
tables, alters = {}, []
for statement in statements:
    if statement.startswith('DROP TABLE'):
        continue
    match = re.match(r'CREATE TABLE (?:IF NOT EXISTS )?`?(\w+)`?\s*\(', statement)
    if match:
        name = match.group(1)
        assert name not in tables
        tables[name] = re.sub(r'CREATE TABLE IF NOT EXISTS', 'CREATE TABLE', statement)
    elif statement.startswith('ALTER TABLE'):
        # TiDB requires adding CHECK constraints separately from new columns.
        if 'ADD CONSTRAINT ck_store_tax' in statement:
            columns, check = statement.split(',\n ADD CONSTRAINT ck_store_tax', 1)
            alters.extend([columns, 'ALTER TABLE stores ADD CONSTRAINT ck_store_tax' + check])
        else:
            alters.append(statement)
    else:
        raise ValueError('Unexpected schema statement: ' + statement[:60])

ordered, done = [], set()
while len(done) < len(tables):
    progressed = False
    for name, statement in tables.items():
        dependencies = set(re.findall(r'REFERENCES\s+`?(\w+)`?', statement)) - {name}
        if name not in done and dependencies <= done:
            ordered.append(statement)
            done.add(name)
            progressed = True
    if not progressed:
        raise ValueError('Cyclic or missing table references: ' + str(set(tables) - done))

header = '''-- KONGKLANG: NEW EMPTY TiDB demo database ONLY.
-- Run once. Stop on the first error; do not continue or rerun on a populated database.
-- No DROP, DELETE, TRUNCATE, accounts, passwords or shop data are included.
-- Enable CHECK enforcement before creating tables; do not ignore an error here.
SET GLOBAL tidb_enable_check_constraint = ON;
CREATE DATABASE kongklang_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kongklang_demo;
SET NAMES utf8mb4;
'''
output = header + '\n\n'.join(s + ';' for s in ordered + alters) + '\n'
assert all(not re.match(r'(DROP|DELETE|TRUNCATE|INSERT)\b', s) for s in ordered + alters)
assert output.count('FOREIGN KEY') == source.count('FOREIGN KEY')
assert len(re.findall(r'\bCHECK\s*\(', output)) == len(re.findall(r'\bCHECK\s*\(', source))
(root / 'database/tidb-demo-schema.sql').write_text(output, encoding='utf-8')
print(f'Generated {len(tables)} tables and {len(alters)} ALTER statements; all foreign keys and CHECK constraints retained.')
