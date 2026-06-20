import json, glob, os, re, math, statistics as st
from math import comb, log, exp
from collections import Counter

SEC="C:/Temp/expansion/sections"; GOLD="C:/Temp/expansion/gold"
STATS="C:/Users/elita/source/repos2024/repcheck-bill-decomposition/decomposition-ml/src/main/resources/standardization/standardization-stats-v1.json"
VALFILE="C:/Temp/validation.txt"; EMB="C:/Temp/expansion/emb"; TOPN=15
STOP=set("""the a an and or of to in for on by with as at from is are be shall may not this that such any section sec
subsection paragraph subparagraph clause act code title united states public law amended amend following under
pursuant chapter part subtitle provided including include means term house senate congress bill resolution date
effective whoever person rule""".split())
USC=re.compile(r"(\d{1,2})\s*U\.?\s?S\.?\s?C\.?\s*(?:§+\s?)?(\d+[A-Za-z0-9]*)")
PUBL=re.compile(r"[Pp]ub(?:lic)?\.?\s?L(?:aw)?\.?\s?(\d{1,3}[-–]\d{1,4})")
ACT=re.compile(r"([A-Z][A-Za-z']+(?:\s+[A-Z][A-Za-z']+){0,5})\s+Act\s+of\s+(\d{4})")
def cites(t): return ({f"usc:{m.group(1)}:{m.group(2)}" for m in USC.finditer(t)} | {f"pl:{m.group(1)}" for m in PUBL.finditer(t)} | {f"act:{m.group(1).lower()}" for m in ACT.finditer(t)})
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
def lab_g(groups,n):
    L=[-1]*n
    for gi,g in enumerate(groups):
        for si in g.get("sectionIndices",[]):
            if 0<=si<n and L[si]==-1: L[si]=gi
    nx=len(groups)
    for i in range(n):
        if L[i]==-1: L[i]=nx; nx+=1
    return L
def ari(p,g):
    n=len(p)
    if n<2: return 1.0
    c=Counter(zip(p,g));a=Counter(p);b=Counter(g)
    idx=sum(comb(v,2) for v in c.values());sa=sum(comb(v,2) for v in a.values());sb=sum(comb(v,2) for v in b.values())
    e=sa*sb/comb(n,2);mx=(sa+sb)/2
    return 1.0 if mx==e else (idx-e)/(mx-e)
def dendro(aff):
    n=len(aff); mem={i:[i] for i in range(n)}; act=list(range(n)); cid=n; sims=[]; snaps=[list(range(n))]
    def L():
        root={}
        for cl in act:
            for lf in mem[cl]: root[lf]=cl
        idx={}; out=[0]*n
        for lf in range(n):
            r=root[lf]
            if r not in idx: idx[r]=len(idx)
            out[lf]=idx[r]
        return out
    while len(act)>1:
        best=None
        for ii in range(len(act)):
            for jj in range(ii+1,len(act)):
                a,b=act[ii],act[jj]; s=sum(aff[x][y] for x in mem[a] for y in mem[b])/(len(mem[a])*len(mem[b]))
                if best is None or s>best[0]: best=(s,a,b)
        s,a,b=best; mem[cid]=mem[a]+mem[b]; act.remove(a); act.remove(b); act.append(cid); del mem[a]; del mem[b]; cid+=1
        sims.append(s); snaps.append(L())
    return sims,snaps
def cutK(snap,K):
    n=len(snap[0]); return snap[max(0,min(n-1,n-K))]
def cutT(sims,snap,T):
    m=0
    for s in sims:
        if s>=T: m+=1
        else: break
    return snap[m]
def bestT(snap,ref):
    n=len(snap[0]); bm=0; ba=-2
    for m in range(n):
        a=ari(snap[m],ref)
        if a>ba: ba=a; bm=m
    return snap[bm]
valset=set(l.strip() for l in open(VALFILE) if l.strip())
bills=[]
for sf in sorted(glob.glob(f"{SEC}/*.json")):
    d=json.load(open(sf)); vid=str(d["versionId"]); secs=sorted(d["sections"],key=lambda x:x["index"]); n=len(secs)
    if n<3 or not os.path.exists(f"{GOLD}/{vid}.json"): continue
    g=json.load(open(f"{GOLD}/{vid}.json")); ref=lab_g(g["groups"],n); k=len(g["groups"])
    bills.append({"vid":vid,"texts":[s["text"] for s in secs],"n":n,"ref":ref,"k":k,"val":vid in valset})
train=[B for B in bills if not B["val"]]; val=[B for B in bills if B["val"]]
df=Counter()
for B in train:
    for t in B["texts"]:
        for term in set(toks(t)): df[term]+=1
Nt=sum(B["n"] for B in train); IDF={t:log(Nt/(1+c)) for t,c in df.items()}
def tfidf(t):
    tf=Counter(toks(t)); return {x:(1+log(c))*IDF.get(x,0) for x,c in tf.items()}
def tt(t):
    v=tfidf(t); return set(x for x,_ in sorted(v.items(),key=lambda z:-z[1])[:TOPN])
for B in bills:
    e=json.load(open(f"{EMB}/{B['vid']}.json")); B["emb"]=[stdz(x) for x in e]
    B["tf"]=[tfidf(t) for t in B["texts"]]; B["tt"]=[tt(t) for t in B["texts"]]; B["ci"]=[cites(t) for t in B["texts"]]
def pf(B,i,j):
    return [cos(B["emb"][i],B["emb"][j]),cos(B["tf"][i],B["tf"][j]),jac(B["tt"][i],B["tt"][j]),jac(B["ci"][i],B["ci"][j]),1.0-abs(i-j)/B["n"]]
def sig(z): return 1/(1+exp(-max(-30,min(30,z))))
def train_m(pairs):
    m=len(pairs[0][0]); mu=[st.mean([p[0][j] for p in pairs]) for j in range(m)]; sd=[st.pstdev([p[0][j] for p in pairs]) or 1 for j in range(m)]
    pos=sum(1 for p in pairs if p[1]); neg=len(pairs)-pos; wp=neg/max(1,pos); w=[0.0]*m; b=0.0
    for _ in range(400):
        gw=[0.0]*m; gb=0.0
        for x,y in pairs:
            xs=[(x[j]-mu[j])/sd[j] for j in range(m)]; p=sig(sum(w[j]*xs[j] for j in range(m))+b); wt=wp if y else 1.0; e=(p-y)*wt
            for j in range(m): gw[j]+=e*xs[j]
            gb+=e
        for j in range(m): w[j]-=0.3*gw[j]/len(pairs)
        b-=0.3*gb/len(pairs)
    return (w,b,mu,sd)
def pred(M,x):
    w,b,mu,sd=M; xs=[(x[j]-mu[j])/sd[j] for j in range(len(w))]; return sig(sum(w[j]*xs[j] for j in range(len(w)))+b)
M=train_m([(pf(B,i,j),1 if B["ref"][i]==B["ref"][j] else 0) for B in train for i in range(B["n"]) for j in range(i+1,B["n"])])
T=0.625
rows=[]
for B in val:
    n=B["n"]; A=[[1.0 if i==j else pred(M,pf(B,i,j)) for j in range(n)] for i in range(n)]
    sims,snap=dendro(A)
    pT=cutT(sims,snap,T); pK=cutK(snap,B["k"]); pC=bestT(snap,B["ref"])
    rows.append((B["vid"],n,B["k"],len(set(pT)),ari(pT,B["ref"]),ari(pK,B["ref"]),ari(pC,B["ref"])))
rows.sort(key=lambda r:r[4])
print(f"{'vid':>9} {'n':>3} {'kGold':>5} {'kPred':>5} {'ARI_T':>7} {'ARI_oK':>7} {'ceil':>6}")
for r in rows:
    print(f"{r[0]:>9} {r[1]:>3} {r[2]:>5} {r[3]:>5} {r[4]:>7.3f} {r[5]:>7.3f} {r[6]:>6.3f}")
aT=[r[4] for r in rows]
print(f"\nN={len(rows)}  ARI_T mean {st.mean(aT):.3f} median {st.median(aT):.3f}")
print(f"  ARI_T <=0: {sum(1 for a in aT if a<=0.001)}   0-0.3: {sum(1 for a in aT if 0.001<a<=0.3)}   0.3-0.5: {sum(1 for a in aT if 0.3<a<=0.5)}   0.5-0.8: {sum(1 for a in aT if 0.5<a<=0.8)}   >0.8: {sum(1 for a in aT if a>0.8)}")
print(f"  perfect (ARI_T==1.0): {sum(1 for a in aT if a>0.999)}")
def bucket(lo,hi):
    rs=[r for r in rows if lo<=r[1]<=hi]
    if not rs: return
    print(f"  n {lo}-{hi}: {len(rs):2d} bills  meanARI_T {st.mean([r[4] for r in rs]):+.3f}  meanCeil {st.mean([r[6] for r in rs]):.3f}")
print("by size:"); bucket(3,4); bucket(5,6); bucket(7,9); bucket(10,99)
afail=[r for r in rows if r[6]<=0.1]   # ceiling near 0 = no good partition exists (affinity failure)
cutfix=[r for r in rows if r[6]-r[4]>0.2]  # good partition exists but our cut misses it
print(f"\n  AFFINITY-failures (ceiling<=0.1, no cut can help): {len(afail)}/{len(rows)}  (avg n {st.mean([r[1] for r in afail]):.1f})")
print(f"  CUT-missed (ceiling-ARI_T>0.2, good partition exists but threshold misses): {len(cutfix)}/{len(rows)}")
