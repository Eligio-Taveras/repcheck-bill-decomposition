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

# ---- full average-linkage dendrogram from affinity (similarity). Returns merge sims (desc) + a cut-by-#merges fn ----
def dendrogram(aff):
    n=len(aff)
    members={i:[i] for i in range(n)}; active=list(range(n)); cid=n
    sims=[]; snapshots=[ [i for i in range(n)] ]   # snapshots[m] = labels after m merges
    parent={i:i for i in range(n)}
    def labels():
        # map each leaf to its current root cluster
        root={}; out=[0]*n
        for cl in active:
            for leaf in members[cl]: root[leaf]=cl
        idx={};
        for leaf in range(n):
            r=root[leaf]
            if r not in idx: idx[r]=len(idx)
            out[leaf]=idx[r]
        return out
    while len(active)>1:
        best=None
        for ii in range(len(active)):
            for jj in range(ii+1,len(active)):
                a,b=active[ii],active[jj]
                s=sum(aff[x][y] for x in members[a] for y in members[b])/(len(members[a])*len(members[b]))
                if best is None or s>best[0]: best=(s,a,b)
        s,a,b=best; members[cid]=members[a]+members[b]
        active.remove(a); active.remove(b); active.append(cid); del members[a]; del members[b]; cid+=1
        sims.append(s); snapshots.append(labels())
    return sims, snapshots   # m merges -> snapshots[m] has n-m clusters; sims[m-1] is the m-th merge similarity

def cut_by_merges(snap, m): return snap[m]
def cut_by_K(sims, snap, K):
    n=len(snap[0]); m=max(0,min(n-1, n-K)); return snap[m]
def cut_by_T(sims, snap, T):
    m=0
    for s in sims:
        if s>=T: m+=1
        else: break
    return snap[m]
def gap_cut(sims, snap):  # cut before the largest drop in merge similarity
    if not sims: return snap[0]
    if len(sims)==1: return snap[0]
    gaps=[(sims[i]-sims[i+1], i) for i in range(len(sims)-1)]
    best=max(gaps, key=lambda z:z[0]); m=best[1]+1
    return snap[m]
def best_T_star(sims, snap, ref):  # oracle per-bill: #merges maximizing ARI -> representative threshold
    n=len(snap[0]); bestm=0; besta=-2
    for m in range(0,n):
        a=ari(snap[m],ref)
        if a>besta: besta=a; bestm=m
    if bestm==0: T=(sims[0]+1.0)/2 if sims else 1.0
    elif bestm>=len(sims): T=(sims[-1]+0.0)/2
    else: T=(sims[bestm-1]+sims[bestm])/2
    return T, besta, bestm

# ---- load + featurize (uses cache) ----
valset=set(l.strip() for l in open(VALFILE) if l.strip())
bills=[]
for sf in sorted(glob.glob(f"{SEC}/*.json")):
    d=json.load(open(sf)); vid=str(d["versionId"]); secs=sorted(d["sections"],key=lambda x:x["index"]); n=len(secs)
    if n<3: continue
    gp=f"{GOLD}/{vid}.json"
    if not os.path.exists(gp): continue
    g=json.load(open(gp)); ref=labels_from_groups(g["groups"],n); k=len(g["groups"])
    bills.append({"vid":vid,"texts":[s["text"] for s in secs],"n":n,"ref":ref,"k":k,"val":vid in valset,
                  "chars":sum(len(s["text"]) for s in secs)})
train=[B for B in bills if not B["val"]]; val=[B for B in bills if B["val"]]
print(f"loaded {len(bills)}: train={len(train)} val={len(val)}", flush=True)

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

trpairs=[(pf(B,i,j), 1 if B["ref"][i]==B["ref"][j] else 0) for B in train for i in range(B["n"]) for j in range(i+1,B["n"])]
model=trainmodel(trpairs)
for B in bills:
    n=B["n"]; A=[[1.0 if i==j else predict(model,pf(B,i,j)) for j in range(n)] for i in range(n)]
    B["aff"]=A; B["sims"],B["snap"]=dendrogram(A)
    off=[A[i][j] for i in range(n) for j in range(i+1,n)]
    Tg,Ta,Tm=best_T_star(B["sims"],B["snap"],B["ref"])
    B["Tstar"]=Tg; B["Tstar_ari"]=Ta
    biggap=max((B["sims"][i]-B["sims"][i+1] for i in range(len(B["sims"])-1)), default=0.0)
    B["feat"]=[B["n"], log(B["chars"]+1), B["chars"]/n, st.mean(off), (st.pstdev(off) if len(off)>1 else 0.0),
               biggap, sum(1 for o in off if o>0.5)/len(off)]

# ---- ABLATION: embedding-free 4-feature model (drop emb_cos = feature 0) ----
def pf4(B,i,j): return pf(B,i,j)[1:]
trpairs4=[(pf4(B,i,j), 1 if B["ref"][i]==B["ref"][j] else 0) for B in train for i in range(B["n"]) for j in range(i+1,B["n"])]
model4=trainmodel(trpairs4)
for B in bills:
    n=B["n"]; A4=[[1.0 if i==j else predict(model4,pf4(B,i,j)) for j in range(n)] for i in range(n)]
    B["sims4"],B["snap4"]=dendrogram(A4)

# ---- global best-T on train ----
def mean_ari_T(bs,T): return st.mean([ari(cut_by_T(B["sims"],B["snap"],T),B["ref"]) for B in bs])
def mean_ari_T4(bs,T): return st.mean([ari(cut_by_T(B["sims4"],B["snap4"],T),B["ref"]) for B in bs])
grid=[round(0.30+0.025*i,3) for i in range(17)]
Tglobal=max(grid,key=lambda T:mean_ari_T(train,T))
Tglobal4=max(grid,key=lambda T:mean_ari_T4(train,T))

# ---- ridge regression  T* = g(features)  on train ----
FN=len(train[0]["feat"])
mu=[st.mean([B["feat"][j] for B in train]) for j in range(FN)]
sd=[st.pstdev([B["feat"][j] for B in train]) or 1 for j in range(FN)]
def z(B): return [(B["feat"][j]-mu[j])/sd[j] for j in range(FN)]
X=[z(B) for B in train]; Y=[B["Tstar"] for B in train]
w=[0.0]*FN; b=st.mean(Y); lr=0.1; lam=1.0
for _ in range(2000):
    gw=[0.0]*FN; gb=0.0
    for xi,yi in zip(X,Y):
        p=sum(w[j]*xi[j] for j in range(FN))+b; e=p-yi
        for j in range(FN): gw[j]+=e*xi[j]
        gb+=e
    for j in range(FN): w[j]-=lr*(gw[j]/len(X)+lam*w[j]/len(X))
    b-=lr*gb/len(X)
def predT(B):
    zb=z(B); return min(0.85,max(0.15, sum(w[j]*zb[j] for j in range(FN))+b))

# held-out regression R^2 on T*
yv=[B["Tstar"] for B in val]; pv=[predT(B) for B in val]; ybar=st.mean(yv)
ss_res=sum((a-c)**2 for a,c in zip(yv,pv)); ss_tot=sum((a-ybar)**2 for a in yv) or 1e-9
r2=1-ss_res/ss_tot

def ev(bs,fn):
    a=[ari(fn(B),B["ref"]) for B in bs]; pg=st.mean([len(set(fn(B))) for B in bs]); gg=st.mean([B["k"] for B in bs])
    return st.mean(a),pg,gg
oracleK   = ev(val, lambda B: cut_by_K(B["sims"],B["snap"],B["k"]))
oracleT   = ev(val, lambda B: cut_by_T(B["sims"],B["snap"],B["Tstar"]))
globalT   = ev(val, lambda B: cut_by_T(B["sims"],B["snap"],Tglobal))
regT      = ev(val, lambda B: cut_by_T(B["sims"],B["snap"],predT(B)))
gapC      = ev(val, lambda B: gap_cut(B["sims"],B["snap"]))

FEATNAMES=["n_sec","log_chars","avg_sec_chars","mean_aff","std_aff","big_gap","frac>0.5"]
print(f"\n=== HELD-OUT VAL ({len(val)} bills)   [ARI | avg pred groups vs gold groups] ===")
print(f"  oracle-K (count handed)        : {oracleK[0]:.3f} | {oracleK[1]:.1f} vs {oracleK[2]:.1f}")
print(f"  oracle per-bill T* (ceiling)   : {oracleT[0]:.3f} | {oracleT[1]:.1f} vs {oracleT[2]:.1f}")
print(f"  STATIC global T={Tglobal:<5}         : {globalT[0]:.3f} | {globalT[1]:.1f} vs {globalT[2]:.1f}")
print(f"  REGRESSION T=g(feat)  (Tier 2) : {regT[0]:.3f} | {regT[1]:.1f} vs {regT[2]:.1f}   <-- length-aware")
print(f"  gap-statistic (structure only) : {gapC[0]:.3f} | {gapC[1]:.1f} vs {gapC[2]:.1f}")
print(f"\n  threshold-regression held-out R^2 = {r2:.3f}")
print(f"  feature weights: " + ", ".join(f"{FEATNAMES[j]}:{w[j]:+.3f}" for j in range(FN)))
print(f"  (headroom static->ceiling = {oracleT[0]-globalT[0]:+.3f};  regression captured = {regT[0]-globalT[0]:+.3f})")

# ---- embedding-free ablation: same cuts, 4-feature model ----
oracleK4 = ev(val, lambda B: cut_by_K(B["sims4"],B["snap4"],B["k"]))
globalT4 = ev(val, lambda B: cut_by_T(B["sims4"],B["snap4"],Tglobal4))
gapC4    = ev(val, lambda B: gap_cut(B["sims4"],B["snap4"]))
print(f"\n=== EMBEDDING-FREE ablation (4 feat: tfidf,terms,cites,pos — NO Ollama) ===")
print(f"  oracle-K          full {oracleK[0]:.3f}  ->  no-emb {oracleK4[0]:.3f}   (delta {oracleK4[0]-oracleK[0]:+.3f})")
print(f"  static global-T   full {globalT[0]:.3f}  ->  no-emb {globalT4[0]:.3f}   (delta {globalT4[0]-globalT[0]:+.3f})  [T={Tglobal4}]")
print(f"  gap-statistic     full {gapC[0]:.3f}  ->  no-emb {gapC4[0]:.3f}   (delta {gapC4[0]-gapC[0]:+.3f})")
print(f"  no-emb weights [tfidf,terms,cites,pos]: {[round(x,2) for x in model4[0]]}")
