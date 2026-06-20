import json, glob, os, re, math, statistics as st, urllib.request
from math import comb, log, exp
from collections import Counter

SEC  = "C:/Temp/expansion/sections"
GOLD = "C:/Temp/expansion/gold"
STATS= "C:/Users/elita/source/repos2024/repcheck-bill-decomposition/decomposition-ml/src/main/resources/standardization/standardization-stats-v1.json"
VALFILE = "C:/Temp/validation.txt"
EMB  = "C:/Temp/expansion/emb"; os.makedirs(EMB, exist_ok=True)
EMODEL="qwen3-embedding:0.6b"; TOPN=15

STOP=set("""the a an and or of to in for on by with as at from is are be shall may not this that such any section sec
subsection paragraph subparagraph clause act code title united states public law amended amend following under
pursuant chapter part subtitle provided including include means term house senate congress bill resolution date
effective whoever person rule""".split())
USC=re.compile(r"(\d{1,2})\s*U\.?\s?S\.?\s?C\.?\s*(?:§+\s?)?(\d+[A-Za-z0-9]*)")
PUBL=re.compile(r"[Pp]ub(?:lic)?\.?\s?L(?:aw)?\.?\s?(\d{1,3}[-–]\d{1,4})")
ACT=re.compile(r"([A-Z][A-Za-z']+(?:\s+[A-Z][A-Za-z']+){0,5})\s+Act\s+of\s+(\d{4})")
def cites(t): return ({f"usc:{m.group(1)}:{m.group(2)}" for m in USC.finditer(t)} |
                      {f"pl:{m.group(1)}" for m in PUBL.finditer(t)} |
                      {f"act:{m.group(1).lower()}" for m in ACT.finditer(t)})

sg=json.load(open(STATS)); GM=sg["mean"]; GS=sg["std"]
def stdz(v): return [ (v[i]-GM[i])/GS[i] if GS[i] else 0.0 for i in range(len(v)) ]
def toks(t):
    w=[x for x in re.findall(r"[a-z]{3,}", t.lower()) if x not in STOP]
    return w+[f"{w[i]}_{w[i+1]}" for i in range(len(w)-1)]
def cos(u,v):
    if isinstance(u,dict):
        dot=sum(u.get(k,0)*v.get(k,0) for k in u); nu=math.sqrt(sum(x*x for x in u.values())); nv=math.sqrt(sum(x*x for x in v.values()))
    else:
        dot=sum(a*b for a,b in zip(u,v)); nu=math.sqrt(sum(a*a for a in u)); nv=math.sqrt(sum(b*b for b in v))
    return dot/(nu*nv) if nu*nv else 0.0
def jac(a,b): return (len(a&b)/len(a|b)) if (a or b) else 0.0
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
def embed(texts):
    body=json.dumps({"model":EMODEL,"input":texts}).encode()
    return json.loads(urllib.request.urlopen(urllib.request.Request("http://localhost:11434/api/embed",data=body,headers={"Content-Type":"application/json"}),timeout=300).read())["embeddings"]
def get_emb(vid,texts):
    p=f"{EMB}/{vid}.json"
    if os.path.exists(p):
        try: return json.load(open(p))
        except: pass
    e=embed([t[:4000] for t in texts]); json.dump(e,open(p,"w")); return e

# cut at a fixed K (oracle) — average linkage on distance=1-aff
def hac_k(aff,k):
    n=len(aff)
    if k>=n: return list(range(n))
    members={i:[i] for i in range(n)}; active=set(range(n)); cid=n
    while len(active)>k:
        al=list(active); best=None
        for ii in range(len(al)):
            for jj in range(ii+1,len(al)):
                a,b=al[ii],al[jj]; s=sum(aff[x][y] for x in members[a] for y in members[b])/(len(members[a])*len(members[b]))
                if best is None or s>best[0]: best=(s,a,b)
        _,a,b=best; members[cid]=members[a]+members[b]; active.discard(a);active.discard(b);active.add(cid); del members[a];del members[b]; cid+=1
    lab=[0]*n
    for li,c in enumerate(active):
        for m in members[c]: lab[m]=li
    return lab

# K-FREE: merge most-similar pair while avg affinity >= T; K falls out
def hac_threshold(aff,T):
    n=len(aff)
    members={i:[i] for i in range(n)}; active=set(range(n)); cid=n
    while len(active)>1:
        al=list(active); best=None
        for ii in range(len(al)):
            for jj in range(ii+1,len(al)):
                a,b=al[ii],al[jj]; s=sum(aff[x][y] for x in members[a] for y in members[b])/(len(members[a])*len(members[b]))
                if best is None or s>best[0]: best=(s,a,b)
        if best is None or best[0] < T: break
        _,a,b=best; members[cid]=members[a]+members[b]; active.discard(a);active.discard(b);active.add(cid); del members[a];del members[b]; cid+=1
    lab=[0]*n
    for li,c in enumerate(active):
        for m in members[c]: lab[m]=li
    return lab

# ---- load ----
valset=set(l.strip() for l in open(VALFILE) if l.strip())
bills=[]
for sf in sorted(glob.glob(f"{SEC}/*.json")):
    d=json.load(open(sf)); vid=str(d["versionId"]); secs=sorted(d["sections"],key=lambda x:x["index"]); n=len(secs)
    if n<3: continue
    gp=f"{GOLD}/{vid}.json"
    if not os.path.exists(gp): continue
    g=json.load(open(gp)); ref=labels_from_groups(g["groups"],n); k=len(g["groups"])
    bills.append({"vid":vid,"texts":[s["text"] for s in secs],"n":n,"ref":ref,"k":k,"val":vid in valset})
train=[B for B in bills if not B["val"]]; val=[B for B in bills if B["val"]]
print(f"loaded {len(bills)}: train={len(train)} val={len(val)}", flush=True)

# IDF on train only
df=Counter()
for B in train:
    for t in B["texts"]:
        for term in set(toks(t)): df[term]+=1
Nt=sum(B["n"] for B in train); IDF={t:log(Nt/(1+c)) for t,c in df.items()}
def tfidf(t):
    tf=Counter(toks(t)); return {x:(1+log(c))*IDF.get(x,0) for x,c in tf.items()}
def topterms(t):
    v=tfidf(t); return set(x for x,_ in sorted(v.items(),key=lambda z:-z[1])[:TOPN])

for bi,B in enumerate(bills):
    B["emb"]=[stdz(e) for e in get_emb(B["vid"],B["texts"])]
    B["tf"]=[tfidf(t) for t in B["texts"]]; B["tt"]=[topterms(t) for t in B["texts"]]; B["ci"]=[cites(t) for t in B["texts"]]
    if (bi+1)%50==0: print(f"  featurized {bi+1}/{len(bills)}", flush=True)

def pf(B,i,j):
    return [cos(B["emb"][i],B["emb"][j]), cos(B["tf"][i],B["tf"][j]),
            jac(B["tt"][i],B["tt"][j]), jac(B["ci"][i],B["ci"][j]), 1.0-abs(i-j)/B["n"]]
def sigmoid(z): return 1/(1+exp(-max(-30,min(30,z))))
def trainmodel(pairs):
    m=len(pairs[0][0]); mu=[st.mean([p[0][j] for p in pairs]) for j in range(m)]; sd=[st.pstdev([p[0][j] for p in pairs]) or 1 for j in range(m)]
    pos=sum(1 for p in pairs if p[1]); neg=len(pairs)-pos; wpos=neg/max(1,pos)
    w=[0.0]*m; b=0.0; lr=0.3
    for _ in range(400):
        gw=[0.0]*m; gb=0.0
        for x,y in pairs:
            xs=[(x[j]-mu[j])/sd[j] for j in range(m)]; p=sigmoid(sum(w[j]*xs[j] for j in range(m))+b)
            wt=wpos if y else 1.0; e=(p-y)*wt
            for j in range(m): gw[j]+=e*xs[j]
            gb+=e
        for j in range(m): w[j]-=lr*gw[j]/len(pairs)
        b-=lr*gb/len(pairs)
    return (w,b,mu,sd)
def predict(model,x):
    w,b,mu,sd=model; xs=[(x[j]-mu[j])/sd[j] for j in range(len(w))]; return sigmoid(sum(w[j]*xs[j] for j in range(len(w)))+b)
def affmat(model,B):
    n=B["n"]; return [[1.0 if i==j else predict(model,pf(B,i,j)) for j in range(n)] for i in range(n)]

trpairs=[(pf(B,i,j), 1 if B["ref"][i]==B["ref"][j] else 0) for B in train for i in range(B["n"]) for j in range(i+1,B["n"])]
model=trainmodel(trpairs)
for B in bills: B["aff"]=affmat(model,B)

# ---- tune threshold T on TRAIN (maximize mean train ARI), apply to VAL ----
grid=[round(0.30+0.025*i,3) for i in range(17)]   # 0.30 .. 0.70
def mean_ari_threshold(bs,T): return st.mean([ari(hac_threshold(B["aff"],T),B["ref"]) for B in bs])
train_curve=[(T, mean_ari_threshold(train,T)) for T in grid]
Tbest=max(train_curve,key=lambda z:z[1])[0]

def gstats(bs,fn):
    aris=[ari(fn(B),B["ref"]) for B in bs]
    pg=st.mean([len(set(fn(B))) for B in bs]); gg=st.mean([B["k"] for B in bs])
    return st.mean(aris), pg, gg, sum(1 for a in aris if a>0.5), sum(1 for a in aris if a>0.3), len(aris)

val_oracle = gstats(val, lambda B: hac_k(B["aff"],B["k"]))
val_tbest  = gstats(val, lambda B: hac_threshold(B["aff"],Tbest))
val_t50    = gstats(val, lambda B: hac_threshold(B["aff"],0.5))

print("\n=== TRAIN threshold sweep (mean ARI) ===")
print("  " + "  ".join(f"{T}:{a:.3f}" for T,a in train_curve))
print(f"  best T on train = {Tbest}")
print(f"\n=== HELD-OUT VAL ({val_oracle[5]} bills)  [ARI | avg pred groups vs avg gold groups | >0.5 | >0.3] ===")
print(f"  oracle-K (upper bound)     : {val_oracle[0]:.3f} | {val_oracle[1]:.1f} vs {val_oracle[2]:.1f} | {val_oracle[3]}/{val_oracle[5]} | {val_oracle[4]}/{val_oracle[5]}")
print(f"  K-FREE threshold T={Tbest:<5}    : {val_tbest[0]:.3f} | {val_tbest[1]:.1f} vs {val_tbest[2]:.1f} | {val_tbest[3]}/{val_tbest[5]} | {val_tbest[4]}/{val_tbest[5]}   <-- production-shaped")
print(f"  K-FREE threshold T=0.5      : {val_t50[0]:.3f} | {val_t50[1]:.1f} vs {val_t50[2]:.1f} | {val_t50[3]}/{val_t50[5]} | {val_t50[4]}/{val_t50[5]}")
print(f"  weights [emb,tfidf,terms,cites,pos]: {[round(x,2) for x in model[0]]}")
