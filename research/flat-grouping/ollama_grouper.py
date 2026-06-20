import json, glob, os, sys, urllib.request, statistics as st
from math import comb
from collections import Counter

MODEL = sys.argv[1] if len(sys.argv)>1 else "qwen3:8b"
LIMIT = int(sys.argv[2]) if len(sys.argv)>2 else 9999
SEC   = "C:/Temp/flat-sections"
GOLD  = "C:/Users/elita/source/repos2024/repcheck-bill-decomposition/evaluation/src/main/resources/gold"
MAXCHARS = 1600

SYSTEM = ("You group the sections of a U.S. bill into distinct CONCEPTS (themes). Each concept is a set of related "
  "section indices. Every section index must appear in exactly one group. Reply with ONLY JSON, no prose: "
  '{"groups":[{"concept":"<short name>","sectionIndices":[<ints>]}]}')

def ollama(system, user):
    body=json.dumps({"model":MODEL,"messages":[{"role":"system","content":system},{"role":"user","content":user}],
        "stream":False,"format":"json","keep_alive":"15m","options":{"temperature":0,"num_ctx":8192}}).encode()
    req=urllib.request.Request("http://localhost:11434/api/chat",data=body,headers={"Content-Type":"application/json"})
    with urllib.request.urlopen(req,timeout=300) as r:
        return json.loads(r.read())["message"]["content"]

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
    idx=sum(comb(v,2) for v in cont.values())
    sa=sum(comb(v,2) for v in a.values()); sb=sum(comb(v,2) for v in b.values())
    exp=sa*sb/comb(n,2); mx=(sa+sb)/2
    return 1.0 if mx==exp else (idx-exp)/(mx-exp)

files=sorted(glob.glob(f"{SEC}/*.json"))[:LIMIT]
rows=[]
for sf in files:
    d=json.load(open(sf)); vid=d["versionId"]; n=d["n"]
    g=json.load(open(f"{GOLD}/{vid}.json"))
    ref=labels_from_groups(g["groups"], n)
    secs=sorted(d["sections"], key=lambda s:s["index"])
    body="\n".join(f"[{s['index']}] {s['text'][:MAXCHARS]}" for s in secs)
    user="/no_think\nGroup these bill sections into distinct concepts.\n\nBill sections:\n"+body
    try:
        out=ollama(SYSTEM,user); pred_groups=json.loads(out).get("groups",[])
        pred=labels_from_groups(pred_groups, n)
        a=ari(pred,ref)
        rows.append((vid,n,len(pred_groups),len(g["groups"]),a))
        print(f"{vid:8s} n={n:2d} llmK={len(pred_groups):2d} refK={len(g['groups']):2d}  ARI={a:.3f}", flush=True)
    except Exception as e:
        print(f"{vid}: ERR {e}", flush=True)
if rows:
    print(f"\n=== {MODEL} on {len(rows)} flat bills ===")
    print(f"MEAN ARI vs Claude gold = {st.mean([r[4] for r in rows]):.3f}   (section-clustering baseline = 0.145)")
    print(f"ARI>0.5: {sum(1 for r in rows if r[4]>0.5)}/{len(rows)}   ARI>0.3: {sum(1 for r in rows if r[4]>0.3)}/{len(rows)}")
