import json, glob, os, sys, urllib.request, statistics as st
from math import comb
from collections import Counter

BACKEND = sys.argv[1] if len(sys.argv)>1 else "haiku"   # haiku | qwen
LIMIT   = int(sys.argv[2]) if len(sys.argv)>2 else 30
SEC  = "C:/Temp/flat-sections"
GOLD = "C:/Users/elita/source/repos2024/repcheck-bill-decomposition/evaluation/src/main/resources/gold"
HAIKU = "claude-haiku-4-5-20251001"
SNIP = 1200

def extract_json(s):
    i=s.find("{")
    if i<0: return {}
    depth=0
    for j in range(i,len(s)):
        if s[j]=="{": depth+=1
        elif s[j]=="}":
            depth-=1
            if depth==0:
                try: return json.loads(s[i:j+1])
                except: return {}
    return {}

def anthropic(prompt):
    body=json.dumps({"model":HAIKU,"max_tokens":1500,"messages":[{"role":"user","content":prompt}]}).encode()
    req=urllib.request.Request("https://api.anthropic.com/v1/messages",data=body,
        headers={"x-api-key":os.environ["ANTHROPIC_API_KEY"],"anthropic-version":"2023-06-01","content-type":"application/json"})
    with urllib.request.urlopen(req,timeout=120) as r:
        return json.loads(r.read())["content"][0]["text"]

OLLAMA_MODEL = {"qwen":"qwen3:8b","llama3":"llama3:latest","ollama3":"llama3:latest"}.get(BACKEND, BACKEND)

def ollama(prompt):
    pre = "/no_think\n" if "qwen3" in OLLAMA_MODEL else ""   # qwen3 reasoning switch; harmless to omit for llama3
    body=json.dumps({"model":OLLAMA_MODEL,"messages":[{"role":"user","content":pre+prompt}],
        "stream":False,"format":"json","keep_alive":"20m","options":{"temperature":0,"num_ctx":8192}}).encode()
    req=urllib.request.Request("http://localhost:11434/api/chat",data=body,headers={"Content-Type":"application/json"})
    with urllib.request.urlopen(req,timeout=300) as r:
        return json.loads(r.read())["message"]["content"]

CALL = anthropic if BACKEND=="haiku" else ollama

def describe_then_group(secs):
    dbody="\n\n".join(f"[{s['index']}] Section\n{s['text'][:SNIP].strip()}" for s in secs)
    dp=('For each numbered section of this bill, write ONE plain-English sentence describing what it does. Return ONLY '
        'JSON, no prose, no code fences: {"descriptions":[{"index":<int>,"description":"<one sentence>"}]}\n\nSections:\n'+dbody)
    desc={d.get("index"):d.get("description","") for d in (extract_json(CALL(dp)).get("descriptions") or [])}
    gbody="\n".join(f"[{s['index']}] Section: {desc.get(s['index'],'')}" for s in secs)
    gp=('Below are one-line descriptions of every section of a SINGLE bill. Group the sections by shared concept - '
        'sections on the same policy mechanism, program, funding stream, or subject belong together. Give each group a '
        'short 2-5 word concept name. Every section index must appear in exactly one group. Return ONLY JSON, no prose: '
        '{"groups":[{"concept":"<short name>","sectionIndices":[<ints>]}]}\n\nSections:\n'+gbody)
    return extract_json(CALL(gp)).get("groups") or []

def labels_from_groups(groups, n):
    lab=[-1]*n
    for gi,g in enumerate(groups or []):
        for si in (g.get("sectionIndices") or []):
            if isinstance(si,int) and 0<=si<n and lab[si]==-1: lab[si]=gi
    nx=len(groups or [])
    for i in range(n):
        if lab[i]==-1: lab[i]=nx; nx+=1
    return lab

def ari(pred, gold):
    n=len(pred)
    if n<2: return 1.0
    cont=Counter(zip(pred,gold)); a=Counter(pred); b=Counter(gold)
    idx=sum(comb(v,2) for v in cont.values()); sa=sum(comb(v,2) for v in a.values()); sb=sum(comb(v,2) for v in b.values())
    exp=sa*sb/comb(n,2); mx=(sa+sb)/2
    return 1.0 if mx==exp else (idx-exp)/(mx-exp)

files=sorted(glob.glob(f"{SEC}/*.json"))[:LIMIT]
rows=[]
for sf in files:
    d=json.load(open(sf)); vid=d["versionId"]; n=d["n"]
    g=json.load(open(f"{GOLD}/{vid}.json")); ref=labels_from_groups(g["groups"], n)
    secs=sorted(d["sections"], key=lambda s:s["index"])
    try:
        groups=describe_then_group(secs); pred=labels_from_groups(groups, n); a=ari(pred,ref)
        rows.append((vid,n,len(groups),len(g["groups"]),a))
        print(f"{vid:8s} n={n:2d} predK={len(groups):2d} refK={len(g['groups']):2d}  ARI={a:.3f}", flush=True)
    except Exception as e:
        print(f"{vid}: ERR {e}", flush=True)
if rows:
    print(f"\n=== {BACKEND} (describe-then-group) on {len(rows)} flat bills ===")
    print(f"MEAN ARI vs Sonnet gold = {st.mean([r[4] for r in rows]):.3f}")
    print(f"ARI>0.5: {sum(1 for r in rows if r[4]>0.5)}/{len(rows)}   ARI>0.3: {sum(1 for r in rows if r[4]>0.3)}/{len(rows)}")
