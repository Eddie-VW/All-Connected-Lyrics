"""Every English text the app shows: tr()/trf() first arguments (string literals joined with +),
plus the service names and notes in Translators.kt. Writes keys.json (a list, in order)."""
import json, os, re, sys
root = sys.argv[1]
def literal_at(s, i):
    # a Kotlin "..." literal starting at s[i] == '"'; returns (value, end)
    assert s[i] == '"'
    out = []; i += 1
    while s[i] != '"':
        if s[i] == '\\':
            n = s[i+1]
            out.append({'n': '\n', 't': '\t', '"': '"', '\\': '\\', '$': '$', "'": "'"}.get(n, '\\' + n)); i += 2
        else:
            out.append(s[i]); i += 1
    return ''.join(out), i + 1
keys = []
def add(k):
    if k and k not in keys and re.search('[A-Za-z]', k): keys.append(k)
for dp, _, fs in os.walk(root):
    for f in sorted(fs):
        if not f.endswith('.kt'): continue
        s = open(os.path.join(dp, f), encoding='utf-8').read()
        for m in re.finditer(r'\btrf?\(\s*"', s):
            i = m.end() - 1; parts = []
            while True:
                v, i = literal_at(s, i); parts.append(v)
                j = i
                while s[j] in ' \t\n': j += 1
                if s[j] == '+':
                    j += 1
                    while s[j] in ' \t\n': j += 1
                    if s[j] == '"': i = j; continue
                break
            add(''.join(parts))
        if f == 'Translators.kt':
            for m in re.finditer(r'Provider\(|^\s*[A-Z]+\("', s, re.M): pass
            for m in re.finditer(r'^\s*[A-Z_]+\("[a-z]+", "([^"]+)", "[^"]+",', s, re.M): add(m.group(1))
            for m in re.finditer(r'(?:true|false), (?:true|false), "([^"]+)", "[^"]+"\)', s): add(m.group(1))
            for m in re.finditer(r'AiPreset\("([^"]+)", "[^"]+",[^\n]*\n\s*"([^"]+)",', s): add(m.group(1)); add(m.group(2))
            for m in re.finditer(r'"\s*,\n\s*"([^"]+)",\n\s*"[^"]*"\),', s): add(m.group(1))
json.dump(keys, open(os.path.join(os.path.dirname(__file__), 'keys.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
print(len(keys))
