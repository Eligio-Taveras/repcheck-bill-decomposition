import json, os, sys, urllib.request

VID   = sys.argv[1]
MODEL = sys.argv[2] if len(sys.argv)>2 else "haiku"
SEC   = f"C:/Temp/flat-sections/{VID}.json"
GOLD  = f"C:/Users/elita/source/repos2024/repcheck-bill-decomposition/evaluation/src/main/resources/gold/{VID}.json"
HAIKU = "claude-haiku-4-5-20251001"
OLLAMA_MODEL = {"qwen":"qwen3:8b","llama3":"llama3:latest"}.get(MODEL, MODEL)
SNIP = 1400

def extract_json(s):
    i=s.find("{")
    if i<0: return {}
    d=0
    for j in range(i,len(s)):
        if s[j]=="{": d+=1
        elif s[j]=="}":
            d-=1
            if d==0:
                try: return json.loads(s[i:j+1])
                except: return {}
    return {}

def call(prompt):
    if MODEL=="haiku":
        body=json.dumps({"model":HAIKU,"max_tokens":2000,"messages":[{"role":"user","content":prompt}]}).encode()
        req=urllib.request.Request("https://api.anthropic.com/v1/messages",data=body,
            headers={"x-api-key":os.environ["ANTHROPIC_API_KEY"],"anthropic-version":"2023-06-01","content-type":"application/json"})
        return json.loads(urllib.request.urlopen(req,timeout=120).read())["content"][0]["text"]
    pre="/no_think\n" if "qwen3" in OLLAMA_MODEL else ""
    body=json.dumps({"model":OLLAMA_MODEL,"messages":[{"role":"user","content":pre+prompt}],"stream":False,
        "format":"json","keep_alive":"20m","options":{"temperature":0,"num_ctx":8192}}).encode()
    req=urllib.request.Request("http://localhost:11434/api/chat",data=body,headers={"Content-Type":"application/json"})
    return json.loads(urllib.request.urlopen(req,timeout=300).read())["message"]["content"]

d=json.load(open(SEC)); secs=sorted(d["sections"], key=lambda s:s["index"]); n=d["n"]

# 1) describe each section
dbody="\n\n".join(f"[{s['index']}] {s['text'][:SNIP].strip()}" for s in secs)
dp=('For each numbered section of this bill, write ONE plain-English sentence describing what it does. Return ONLY '
    'JSON: {"descriptions":[{"index":<int>,"description":"<one sentence>"}]}\n\nSections:\n'+dbody)
desc={x.get("index"):x.get("description","") for x in (extract_json(call(dp)).get("descriptions") or [])}

# 2) summarize + group
gbody="\n".join(f"[{s['index']}] {desc.get(s['index'],'')}" for s in secs)
gp=('Below are one-line descriptions of every section of a SINGLE bill. (1) Write a 2-3 sentence plain-English summary. '
    '(2) Group the sections by shared concept; give each a short 2-5 word name; every index appears once. Return ONLY '
    'JSON: {"summary":"...","groups":[{"concept":"<name>","sectionIndices":[<ints>]}]}\n\nSections:\n'+gbody)
res=extract_json(call(gp)); groups=res.get("groups") or []

print("="*90)
print(f"BILL {VID} — {n} sections — extractor: {MODEL}")
print("="*90)
print("SUMMARY:", res.get("summary",""))
print(f"\n{MODEL.upper()} CONCEPTS ({len(groups)} groups):")
for g in groups:
    print(f"\n  - {g.get('concept','?')}   sections {g.get('sectionIndices',[])}")
    for i in g.get("sectionIndices",[]):
        print(f"      [{i}] {desc.get(i,'(no description)')}")

# gold (Sonnet) for comparison
if os.path.exists(GOLD):
    gold=json.load(open(GOLD)); gdesc={s["index"]:(s.get("description") or "") for s in gold["sections"]}
    print(f"\n{'-'*90}\nSONNET GOLD ({len(gold['groups'])} groups):")
    print("SUMMARY:", gold.get("summary",""))
    for g in gold["groups"]:
        print(f"\n  - {g['conceptLabel']}   sections {g['sectionIndices']}")
        for i in g["sectionIndices"]:
            print(f"      [{i}] {gdesc.get(i,'')[:110]}")
