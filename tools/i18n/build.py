"""Language files (N|text, N = position in keys.json) -> assets/i18n/<code>.json mapping English -> text."""
import json, os, sys
here = os.path.dirname(os.path.abspath(__file__))
keys = json.load(open(os.path.join(here, 'keys.json'))) + [
    "Custom", "Any service with an OpenAI-compatible chat API: enter its address and model."]
out_dir = sys.argv[1]
os.makedirs(out_dir, exist_ok=True)
for code in ["zh-Hant", "ja", "ko", "es", "fr", "de", "pt", "ru", "id", "th", "vi", "ar"]:
    table = {}
    for line in open(os.path.join(here, code + '.txt'), encoding='utf-8').read().splitlines():
        if not line.strip(): continue
        n, text = line.split('|', 1)
        n = int(n)
        if n >= len(keys): raise SystemExit(f"{code}: line {n} is past the end of the list")
        text = text.replace('\\n', '\n')
        # placeholders must survive translation
        for ph in ['{0}', '{1}', '%.1f']:
            if ph in keys[n] and ph not in text: raise SystemExit(f"{code} {n}: lost {ph}")
        table[keys[n]] = text
    json.dump(table, open(os.path.join(out_dir, code + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    missing = [i for i, k in enumerate(keys) if k not in table]
    print(code, len(table), "missing (English shown):", missing)
