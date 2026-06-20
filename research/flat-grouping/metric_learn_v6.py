import json, glob, os, re, math, statistics as st, urllib.request
from math import comb, log, exp
from collections import Counter, defaultdict

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
HEAD=re.compile(r"^\s*SEC(?:TION)?\.?\s*\d+[A-Za-z]?\.?\s*(.+?)(?:[\.—–\n]|$)", re.I)
def cites(t): return ({f"usc:{m.group(1)}:{m.group(2)}" for m in USC.finditer(t)} |
                      {f"pl:{m.group(1)}" for m in PUBL.finditer(t)} |
                      {f"act:{m.group(1).lower()}" for m in ACT.finditer(t)})
def headtoks(t):
    m=HEAD.match(t.strip())
    if not m: return set()
    return set(x for x in re.findall(r"[a-z]{3,}", m.group(1).lower()) if x not in STOP)
sg=json.load(open(STATS)); GM=sg["mean"]; GS=sg["std"]
def stdz(v): return [ (v[i]-GM[i])/GS[i] if GS[i] else 0.0 for i in range(len(v)) ]
def toks(t):
    w=[x for x in re.findall(r"[a-z]{3,}", t.lower()) if x not in STOP]
    return w+[f"{w[i]}_{w[i+1]}" for i in range(len(w)-1)]
def uni(t): return set(x for x in re.findall(r"[a-z]{3,}", t.lower()) if x not in STOP)
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
def dendrogram(aff):
    n=len(aff); members={i:[i] for i in range(n)}; active=list(range(n)); cid=n
    sims=[]; snaps=[ list(range(n)) ]
    def labels():
        root={}
        for cl in active:
            for leaf in members[cl]: root[leaf]=cl
        idx={}; out=[0]*n
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
        sims.append(s); snaps.append(labels())
    return sims, snaps
def cut_by_K(snap,K):
    n=len(snap[0]); m=max(0,min(n-1,n-K)); return snap[m]
def cut_by_T(sims,snap,T):
    m=0
    for s in sims:
        if s>=T: m+=1
        else: break
    return snap[m]
def best_Tstar(snap,ref):
    n=len(snap[0]); bestm=0; besta=-2
    for m in range(0,n):
        a=ari(snap[m],ref)
        if a>besta: besta=a; bestm=m
    return snap[bestm], besta

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
df=Counter()
for B in train:
    for t in B["texts"]:
        for term in set(toks(t)): df[term]+=1
Nt=sum(B["n"] for B in train); IDF={t:log(Nt/(1+c)) for t,c in df.items()}
def tfidf(t):
    tf=Counter(toks(t)); return {x:(1+log(c))*IDF.get(x,0) for x,c in tf.items()}
def topterms(t):
    v=tfidf(t); return set(x for x,_ in sorted(v.items(),key=lambda z:-z[1])[:TOPN])
hcov=0; htot=0
for bi,B in enumerate(bills):
    B["emb"]=[stdz(e) for e in get_emb(B["vid"],B["texts"])]
    B["tf"]=[tfidf(t) for t in B["texts"]]; B["tt"]=[topterms(t) for t in B["texts"]]; B["ci"]=[cites(t) for t in B["texts"]]
    B["uni"]=[uni(t) for t in B["texts"]]; B["hd"]=[headtoks(t) for t in B["texts"]]
    hcov+=sum(1 for h in B["hd"] if h); htot+=B["n"]
    if (bi+1)%50==0: print(f"  featurized {bi+1}/{len(bills)}", flush=True)
print(f"  heading extraction coverage: {hcov}/{htot} = {100*hcov/htot:.0f}% of sections", flush=True)

def base(B,i,j):
    return [cos(B["emb"][i],B["emb"][j]), cos(B["tf"][i],B["tf"][j]),
            jac(B["tt"][i],B["tt"][j]), jac(B["ci"][i],B["ci"][j]), 1.0-abs(i-j)/B["n"]]
def ext(B,i,j):
    e,tf,_,_,p = base(B,i,j)
    return base(B,i,j) + [e*p, tf*p, e*tf, jac(B["uni"][i],B["uni"][j]), jac(B["hd"][i],B["hd"][j])]

def sigmoid(z): return 1/(1+exp(-max(-30,min(30,z))))
def trainmodel(pairs):
    m=len(pairs[0][0]); mu=[st.mean([p[0][j] for p in pairs]) for j in range(m)]; sd=[st.pstdev([p[0][j] for p in pairs]) or 1 for j in range(m)]
    pos=sum(1 for p in pairs if p[1]); neg=len(pairs)-pos; wpos=neg/max(1,pos)
    w=[0.0]*m; b=0.0; lr=0.3
    for _ in range(500):
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

def build(featfn, tag):
    trp=[(featfn(B,i,j), 1 if B["ref"][i]==B["ref"][j] else 0) for B in train for i in range(B["n"]) for j in range(i+1,B["n"])]
    model=trainmodel(trp)
    for B in bills:
        n=B["n"]; A=[[1.0 if i==j else predict(model,featfn(B,i,j)) for j in range(n)] for i in range(n)]
        B[tag+"_sims"],B[tag+"_snap"]=dendrogram(A)
    grid=[round(0.30+0.025*i,3) for i in range(17)]
    Tg=max(grid,key=lambda T:st.mean([ari(cut_by_T(B[tag+"_sims"],B[tag+"_snap"],T),B["ref"]) for B in train]))
    return model, Tg

mb,Tb = build(base,"b")
me,Te = build(ext,"e")

def ev(bs, tag, cutfn):
    a=[ari(cutfn(B,tag),B["ref"]) for B in bs]; pg=st.mean([len(set(cutfn(B,tag))) for B in bs])
    return st.mean(a), pg
gg=st.mean([B["k"] for B in val])
oKb =ev(val,"b",lambda B,t:cut_by_K(B[t+"_snap"],B["k"]));   oTb=ev(val,"b",lambda B,t:best_Tstar(B[t+"_snap"],B["ref"])[0]); gTb=ev(val,"b",lambda B,t:cut_by_T(B[t+"_sims"],B[t+"_snap"],Tb))
oKe =ev(val,"e",lambda B,t:cut_by_K(B[t+"_snap"],B["k"]));   oTe=ev(val,"e",lambda B,t:best_Tstar(B[t+"_snap"],B["ref"])[0]); gTe=ev(val,"e",lambda B,t:cut_by_T(B[t+"_sims"],B[t+"_snap"],Te))

NAMES=["emb","tfidf","sterms","scites","pos","emb*pos","tfidf*pos","emb*tfidf","full_jac","head_jac"]
print(f"\n=== HELD-OUT VAL ({len(val)} bills, gold avg {gg:.1f} groups)   [ARI | avg pred groups] ===")
print(f"                         oracle-K   |  oracle-T* (ceiling)  |  static trained-T")
print(f"  BASELINE (5 feat)    : {oKb[0]:.3f} ({oKb[1]:.1f}) |   {oTb[0]:.3f} ({oTb[1]:.1f})       |  {gTb[0]:.3f} ({gTb[1]:.1f})  [T={Tb}]")
print(f"  ENHANCED (10 feat)   : {oKe[0]:.3f} ({oKe[1]:.1f}) |   {oTe[0]:.3f} ({oTe[1]:.1f})       |  {gTe[0]:.3f} ({gTe[1]:.1f})  [T={Te}]")
print(f"\n  delta oracle-K (affinity quality) : {oKe[0]-oKb[0]:+.3f}")
print(f"  delta static-T (production)       : {gTe[0]-gTb[0]:+.3f}")
print(f"  enhanced weights:")
for nm,wt in sorted(zip(NAMES,me[0]), key=lambda z:-abs(z[1])):
    print(f"     {nm:12s} {wt:+.3f}")
