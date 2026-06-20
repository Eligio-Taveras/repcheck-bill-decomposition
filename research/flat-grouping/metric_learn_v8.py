import json, glob, os, re, math, statistics as st, random
from math import comb, log, exp
from collections import Counter
random.seed(0)

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
def sig(z): return 1/(1+exp(-max(-30,min(30,z))))

def pf(B,i,j):
    return [B["E"][i][j],B["F"][i][j],jac(B["tt"][i],B["tt"][j]),jac(B["ci"][i],B["ci"][j]),1.0-abs(i-j)/B["n"]]
def train_lr(pairs, epochs=400, lr=0.3):
    m=len(pairs[0][0]); mu=[st.mean([p[0][j] for p in pairs]) for j in range(m)]; sd=[st.pstdev([p[0][j] for p in pairs]) or 1 for j in range(m)]
    pos=sum(1 for p in pairs if p[1]); neg=len(pairs)-pos; wp=neg/max(1,pos); w=[0.0]*m; b=0.0
    for _ in range(epochs):
        gw=[0.0]*m; gb=0.0
        for x,y in pairs:
            xs=[(x[j]-mu[j])/sd[j] for j in range(m)]; p=sig(sum(w[j]*xs[j] for j in range(m))+b); wt=wp if y else 1.0; e=(p-y)*wt
            for j in range(m): gw[j]+=e*xs[j]
            gb+=e
        for j in range(m): w[j]-=lr*gw[j]/len(pairs)
        b-=lr*gb/len(pairs)
    return ("lr",w,b,mu,sd)
def train_mlp(pairs, H=8, epochs=500, lr=0.15):
    m=len(pairs[0][0]); mu=[st.mean([p[0][j] for p in pairs]) for j in range(m)]; sd=[st.pstdev([p[0][j] for p in pairs]) or 1 for j in range(m)]
    pos=sum(1 for p in pairs if p[1]); neg=len(pairs)-pos; wp=neg/max(1,pos)
    W1=[[random.uniform(-0.3,0.3) for _ in range(m)] for _ in range(H)]; b1=[0.0]*H
    W2=[random.uniform(-0.3,0.3) for _ in range(H)]; b2=0.0
    X=[[(x[j]-mu[j])/sd[j] for j in range(m)] for x,_ in pairs]; Y=[y for _,y in pairs]
    for _ in range(epochs):
        for xi,yi in zip(X,Y):
            h=[math.tanh(sum(W1[k][j]*xi[j] for j in range(m))+b1[k]) for k in range(H)]
            p=sig(sum(W2[k]*h[k] for k in range(H))+b2); wt=wp if yi else 1.0; d=(p-yi)*wt
            for k in range(H):
                gh=d*W2[k]*(1-h[k]*h[k])
                for j in range(m): W1[k][j]-=lr*gh*xi[j]
                b1[k]-=lr*gh; W2[k]-=lr*d*h[k]
            b2-=lr*d
    return ("mlp",W1,b1,W2,b2,mu,sd,H,m)
def pred(M,x):
    if M[0]=="lr":
        _,w,b,mu,sd=M; xs=[(x[j]-mu[j])/sd[j] for j in range(len(w))]; return sig(sum(w[j]*xs[j] for j in range(len(w)))+b)
    _,W1,b1,W2,b2,mu,sd,H,m=M; xs=[(x[j]-mu[j])/sd[j] for j in range(m)]
    h=[math.tanh(sum(W1[k][j]*xs[j] for j in range(m))+b1[k]) for k in range(H)]
    return sig(sum(W2[k]*h[k] for k in range(H))+b2)

def within(M,X):
    if len(X)<2: return 1.0
    return sum(M[x][y] for i,x in enumerate(X) for y in X[i+1:])/comb(len(X),2)
def submean(M,X,Y): return sum(M[x][y] for x in X for y in Y)/(len(X)*len(Y))
def merge_feats(B,X,Y,best_other):
    A=B["A"]; n=B["n"]; cross=[A[x][y] for x in X for y in Y]; sim=sum(cross)/len(cross)
    cmin=min(cross); cstd=st.pstdev(cross) if len(cross)>1 else 0.0
    cg=min(abs(x-y) for x in X for y in Y); mg=sum(abs(x-y) for x in X for y in Y)/len(cross)
    wX=within(A,X); wY=within(A,Y)
    return [sim, sim-best_other, min(len(X),len(Y))/n, (len(X)+len(Y))/n, 1.0 if cg==1 else 0.0, mg/n, min(wX,wY), (n-len(X)-len(Y)+2)/n,
            cmin, cstd, submean(B["E"],X,Y), submean(B["F"],X,Y), sim-min(wX,wY)]
BASE8=[0,1,2,3,4,5,6,7]   # the original v7 feature subset

def all_pairsims(A,clusters):
    out=[]
    for ii in range(len(clusters)):
        for jj in range(ii+1,len(clusters)):
            out.append((submean(A,clusters[ii],clusters[jj]),ii,jj))
    out.sort(reverse=True); return out
def labels_of(clusters,n):
    out=[0]*n
    for li,c in enumerate(clusters):
        for m in c: out[m]=li
    return out
def greedy_record(B,ref,idxs):
    A=B["A"]; n=B["n"]; clusters=[[i] for i in range(n)]; recs=[]
    while len(clusters)>1:
        ps=all_pairsims(A,clusters); sim,ci,cj=ps[0]; bo=ps[1][0] if len(ps)>1 else 0.0
        f=[merge_feats(B,clusters[ci],clusters[cj],bo)[k] for k in idxs]
        XY=clusters[ci]+clusters[cj]; lab=1 if len(set(ref[i] for i in XY))==1 else 0
        recs.append((f,lab)); clusters=[c for k,c in enumerate(clusters) if k not in (ci,cj)]+[XY]
    return recs
def vetoed(B,SM,tau,idxs):
    A=B["A"]; n=B["n"]; clusters=[[i] for i in range(n)]
    while len(clusters)>1:
        ps=all_pairsims(A,clusters); merged=False
        for k,(sim,ci,cj) in enumerate(ps):
            bo=ps[0][0] if k>0 else (ps[1][0] if len(ps)>1 else 0.0)
            f=[merge_feats(B,clusters[ci],clusters[cj],bo)[t] for t in idxs]
            if pred(SM,f)>=tau:
                clusters=[c for kk,c in enumerate(clusters) if kk not in (ci,cj)]+[clusters[ci]+clusters[cj]]; merged=True; break
        if not merged: break
    return labels_of(clusters,n)
def greedy_dendro(A,n):
    clusters=[[i] for i in range(n)]; sims=[]; snaps=[list(range(n))]
    while len(clusters)>1:
        ps=all_pairsims(A,clusters); sim,ci,cj=ps[0]
        clusters=[c for k,c in enumerate(clusters) if k not in (ci,cj)]+[clusters[ci]+clusters[cj]]
        sims.append(sim); snaps.append(labels_of(clusters,n))
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
    bm=0; ba=-2
    for m in range(len(snap)):
        a=ari(snap[m],ref)
        if a>ba: ba=a; bm=m
    return snap[bm]

valset=set(l.strip() for l in open(VALFILE) if l.strip()); bills=[]
for sf in sorted(glob.glob(f"{SEC}/*.json")):
    d=json.load(open(sf)); vid=str(d["versionId"]); secs=sorted(d["sections"],key=lambda x:x["index"]); n=len(secs)
    if n<3 or not os.path.exists(f"{GOLD}/{vid}.json"): continue
    g=json.load(open(f"{GOLD}/{vid}.json")); ref=lab_g(g["groups"],n); k=len(g["groups"])
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
def tt(t):
    v=tfidf(t); return set(x for x,_ in sorted(v.items(),key=lambda z:-z[1])[:TOPN])
for B in bills:
    e=json.load(open(f"{EMB}/{B['vid']}.json")); em=[stdz(x) for x in e]; tv=[tfidf(t) for t in B["texts"]]; n=B["n"]
    B["tt"]=[tt(t) for t in B["texts"]]; B["ci"]=[cites(t) for t in B["texts"]]
    B["E"]=[[1.0 if i==j else cos(em[i],em[j]) for j in range(n)] for i in range(n)]
    B["F"]=[[1.0 if i==j else cos(tv[i],tv[j]) for j in range(n)] for i in range(n)]
AM=train_lr([(pf(B,i,j),1 if B["ref"][i]==B["ref"][j] else 0) for B in train for i in range(B["n"]) for j in range(i+1,B["n"])])
for B in bills:
    n=B["n"]; B["A"]=[[1.0 if i==j else pred(AM,pf(B,i,j)) for j in range(n)] for i in range(n)]
print("  affinity model trained", flush=True)
trs=[B for B in train if B["n"]<=40]
for B in val: B["sims"],B["snap"]=greedy_dendro(B["A"],B["n"])
for B in trs: B["sims"],B["snap"]=greedy_dendro(B["A"],B["n"])
grid=[round(0.30+0.025*i,3) for i in range(17)]; Tg=max(grid,key=lambda T:st.mean([ari(cutT(B["sims"],B["snap"],T),B["ref"]) for B in trs]))

ALL=list(range(13))
def fit_and_eval(idxs, trainer, tag):
    sp=[]
    for B in trs: sp+=greedy_record(B,B["ref"],idxs)
    SM=trainer(sp)
    taus=[round(0.30+0.05*i,3) for i in range(8)]
    tau=max(taus,key=lambda t:st.mean([ari(vetoed(B,SM,t,idxs),B["ref"]) for B in trs]))
    a=[ari(vetoed(B,SM,tau,idxs),B["ref"]) for B in val]; pg=st.mean([len(set(vetoed(B,SM,tau,idxs))) for B in val])
    print(f"  {tag:34s}: {st.mean(a):.3f} | {pg:.1f} | med {st.median(a):.3f} | >0.3 {sum(1 for x in a if x>0.3)} | <=0 {sum(1 for x in a if x<=0.001)}  [tau={tau}]", flush=True)
    return st.mean(a)

gg=st.mean([B["k"] for B in val])
oT=st.mean([ari(bestT(B["snap"],B["ref"]),B["ref"]) for B in val]); oK=st.mean([ari(cutK(B["snap"],B["k"]),B["ref"]) for B in val]); gT=st.mean([ari(cutT(B["sims"],B["snap"],Tg),B["ref"]) for B in val])
print(f"\n=== HELD-OUT VAL ({len(val)} bills, gold avg {gg:.1f})  [ARI | avg groups | median | >0.3 | <=0] ===")
print(f"  ceiling (oracle-T*)               : {oT:.3f}")
print(f"  oracle-K                          : {oK:.3f}")
print(f"  static global-T={Tg}                : {gT:.3f}")
fit_and_eval(BASE8, train_lr, "v7 baseline: 8feat LogReg")
fit_and_eval(ALL,   train_lr, "enhanced: 13feat LogReg")
fit_and_eval(ALL,   lambda sp: train_mlp(sp,H=8), "enhanced: 13feat MLP(8)")
