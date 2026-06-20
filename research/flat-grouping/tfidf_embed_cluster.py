import json, glob, os, re, math, statistics as st, urllib.request
from math import comb, log
from collections import Counter

SEC  = "C:/Temp/flat-sections"
GOLD = "C:/Users/elita/source/repos2024/repcheck-bill-decomposition/evaluation/src/main/resources/gold"
STATS= "C:/Users/elita/source/repos2024/repcheck-bill-decomposition/decomposition-ml/src/main/resources/standardization/standardization-stats-v1.json"
TOPN = 15
EMODEL = "qwen3-embedding:0.6b"

STOP = set("""the a an and or of to in for on by with as at from is are be shall may not this that such any
section sec subsection paragraph subparagraph clause act code title united states public law amended amend
following under pursuant chapter part subtitle provided including include means term subsec rule house senate
congress bill resolution date effective whoever person shall_be""".split())

# ---- load global standardization stats ----
sg=json.load(open(STATS)); GMEAN=sg["mean"]; GSTD=sg["std"]
def standardize(v): return [ (v[i]-GMEAN[i])/GSTD[i] if GSTD[i] else 0.0 for i in range(len(v)) ]

# ---- tokenize + tf-idf ----
def toks(text):
    w=[t for t in re.findall(r"[a-z]{3,}", text.lower()) if t not in STOP]
    bg=[f"{w[i]}_{w[i+1]}" for i in range(len(w)-1)]
    return w+bg

def labels_from_groups(groups,n):
    lab=[-1]*n
    for gi,g in enumerate(groups):
        for si in g.get("sectionIndices",[]):
            if 0<=si<n and lab[si]==-1: lab[si]=gi
    nx=len(groups)
    for i in range(n):
        if lab[i]==-1: lab[i]=nx; nx+=1
    return lab
def ari(pred,gold):
    n=len(pred)
    if n<2: return 1.0
    c=Counter(zip(pred,gold));a=Counter(pred);b=Counter(gold)
    idx=sum(comb(v,2) for v in c.values());sa=sum(comb(v,2) for v in a.values());sb=sum(comb(v,2) for v in b.values())
    e=sa*sb/comb(n,2);mx=(sa+sb)/2
    return 1.0 if mx==e else (idx-e)/(mx-e)

def cos_dist(u,v):
    dot=sum(u.get(k,0)*v.get(k,0) for k in u) if isinstance(u,dict) else sum(a*b for a,b in zip(u,v))
    if isinstance(u,dict):
        nu=math.sqrt(sum(x*x for x in u.values())); nv=math.sqrt(sum(x*x for x in v.values()))
    else:
        nu=math.sqrt(sum(x*x for x in u)); nv=math.sqrt(sum(x*x for x in v))
    return 1.0 - (dot/(nu*nv) if nu*nv else 0.0)

def hac_cut(vecs, k, sparse=False):
    n=len(vecs)
    if k>=n: return list(range(n))
    D=[[0.0]*n for _ in range(n)]
    for i in range(n):
        for j in range(i+1,n):
            D[i][j]=D[j][i]=cos_dist(vecs[i],vecs[j])
    members={i:[i] for i in range(n)}; active=set(range(n)); cid=n
    while len(active)>k:
        al=list(active); best=None
        for ii in range(len(al)):
            for jj in range(ii+1,len(al)):
                a,b=al[ii],al[jj]
                d=sum(D[x][y] for x in members[a] for y in members[b])/(len(members[a])*len(members[b]))
                if best is None or d<best[0]: best=(d,a,b)
        _,a,b=best; members[cid]=members[a]+members[b]; active.discard(a);active.discard(b);active.add(cid)
        del members[a]; del members[b]; cid+=1
    lab=[0]*n
    for li,c in enumerate(active):
        for m in members[c]: lab[m]=li
    return lab

def embed(texts):
    body=json.dumps({"model":EMODEL,"input":texts}).encode()
    req=urllib.request.Request("http://localhost:11434/api/embed",data=body,headers={"Content-Type":"application/json"})
    return json.loads(urllib.request.urlopen(req,timeout=300).read())["embeddings"]

# ---- corpus IDF over all flat sections ----
files=sorted(glob.glob(f"{SEC}/*.json"))
allsec=[]   # (vid, idx, text)
for sf in files:
    d=json.load(open(sf))
    for s in sorted(d["sections"],key=lambda x:x["index"]): allsec.append((d["versionId"], s["index"], s["text"]))
df=Counter()
for _,_,t in allsec:
    for term in set(toks(t)): df[term]+=1
N=len(allsec); IDF={t: log(N/(1+c)) for t,c in df.items()}

def tfidf_vec(text):
    tf=Counter(toks(text));
    return {t: (1+log(c))*IDF.get(t,0) for t,c in tf.items()}
def top_terms(text):
    v=tfidf_vec(text)
    return " ".join(t.replace("_"," ") for t,_ in sorted(v.items(),key=lambda x:-x[1])[:TOPN])

rows=[]
for sf in files:
    d=json.load(open(sf)); vid=d["versionId"]; secs=sorted(d["sections"],key=lambda x:x["index"]); n=len(secs)
    if n<2: continue
    g=json.load(open(f"{GOLD}/{vid}.json")); ref=labels_from_groups(g["groups"],n); k=len(g["groups"])
    texts=[s["text"] for s in secs]; kw=[top_terms(s["text"]) for s in secs]
    # pure tf-idf cluster
    tfv=[tfidf_vec(t) for t in texts]; a_tfidf=ari(hac_cut(tfv,k,sparse=True), ref)
    # whole-section embed (standardized) cluster
    wv=[standardize(e) for e in embed([t[:4000] for t in texts])]; a_whole=ari(hac_cut(wv,k), ref)
    # keyword embed (standardized) cluster
    kv=[standardize(e) for e in embed(kw)]; a_kw=ari(hac_cut(kv,k), ref)
    rows.append((vid,n,k,a_tfidf,a_whole,a_kw))
    print(f"{vid:8s} n={n:2d} k={k:2d}  tfidf={a_tfidf:.3f}  whole_embed={a_whole:.3f}  kw_embed={a_kw:.3f}", flush=True)

print(f"\n=== {len(rows)} flat bills (cluster at oracle K) ===")
print(f"  pure TF-IDF            : {st.mean([r[3] for r in rows]):.3f}")
print(f"  whole-section embed    : {st.mean([r[4] for r in rows]):.3f}   (production baseline ~0.143)")
print(f"  TF-IDF-terms -> embed  : {st.mean([r[5] for r in rows]):.3f}   (the new idea)")
