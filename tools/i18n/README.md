# App languages

English and Simplified Chinese are written in the code: `tr("English", "简体中文")`.
The other languages live in `app/src/main/assets/i18n/<code>.json` (English text → translation),
built from the files here:

- `keys.json` – every English text in the app, in order (made by `extract.py`)
- `<code>.txt` – one line per text: `N|translation`, N = its position in `keys.json`
  (`\n` = line break; `{0}`, `{1}`, `%.1f` must be kept). Missing lines show English.

After adding or changing texts in the code:

```
python3 tools/i18n/extract.py app/src/main/java/com/autosynced/lyrics   # refresh keys.json
# add / fix lines in the <code>.txt files (new texts get new numbers at the end)
python3 tools/i18n/build.py app/src/main/assets/i18n
```

Note: `extract.py` appends in the order it finds texts, so re-running it after edits can renumber
lines; check the diff of `keys.json` and adjust the `.txt` files with it.
