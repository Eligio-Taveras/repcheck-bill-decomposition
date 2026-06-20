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
def sig(z): return 1/(1+exp(-max(-30,min(30,z))))

# ---- base affinity model (validated 5-feature) ----
def pf(B,i,j):
    return [cos(B["emb"][i],B["emb"][j]),cos(B["tf"][i],B["tf"][j]),jac(B["tt"][i],B["tt"][j]),jac(B["ci"][i],B["ci"][j]),1.0-abs(i-j)/B["n"]]
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
    return (w,b,mu,sd)
def pred(M,x):
    w,b,mu,sd=M; xs=[(x[j]-mu[j])/sd[j] for j in range(len(w))]; return sig(sum(w[j]*xs[j] for j in range(len(w)))+b)

# ---- merge-level features for the STOP model ----
def avglink(A,X,Y): return sum(A[x][y] for x in X for y in Y)/(len(X)*len(Y))
def within(A,X):
    if len(X)<2: return 1.0
    return sum(A[x][y] for i,x in enumerate(X) for y in X[i+1:])/comb(len(X),2)
def crossgap(X,Y): return min(abs(x-y) for x in X for y in Y)
def meangap(X,Y): return sum(abs(x-y) for x in X for y in Y)/(len(X)*len(Y))
def merge_feats(A,clusters,ci,cj,n,best_other):
    X=clusters[ci]; Y=clusters[cj]; sim=avglink(A,X,Y); cg=crossgap(X,Y)
    return [sim, sim-best_other, min(len(X),len(Y))/n, (len(X)+len(Y))/n,
            1.0 if cg==1 else 0.0, meangap(X,Y)/n, min(within(A,X),within(A,Y)), len(clusters)/n]

def all_pairsims(A,clusters):
    out=[]
    for ii in range(len(clusters)):
        for jj in range(ii+1,len(clusters)):
            out.append((avglink(A,clusters[ii],clusters[jj]),ii,jj))
    out.sort(reverse=True)
    return out

# greedy agglomeration that RECORDS each chosen merge's feats + gold label (for training)
def greedy_record(A,n,ref):
    clusters=[[i] for i in range(n)]; recs=[]
    while len(clusters)>1:
        ps=all_pairsims(A,clusters); sim,ci,cj=ps[0]; best_other=ps[1][0] if len(ps)>1 else 0.0
        f=merge_feats(A,clusters,ci,cj,n,best_other)
        XY=clusters[ci]+clusters[cj]; label=1 if len(set(ref[i] for i in XY))==1 else 0
        recs.append((f,label))
        clusters=[c for k,c in enumerate(clusters) if k not in (ci,cj)]+[XY]
    return recs

def labels_of(clusters,n):
    out=[0]*n
    for li,c in enumerate(clusters):
        for m in c: out[m]=li
    return out

# eval method A: sequential stop on greedy order (stop at first merge with P<tau)
def seq_stop(A,n,SM,tau):
    clusters=[[i] for i in range(n)]
    while len(clusters)>1:
        ps=all_pairsims(A,clusters); sim,ci,cj=ps[0]; best_other=ps[1][0] if len(ps)>1 else 0.0
        f=merge_feats(A,clusters,ci,cj,n,best_other)
        if pred(SM,f)<tau: break
        clusters=[c for k,c in enumerate(clusters) if k not in (ci,cj)]+[clusters[ci]+clusters[cj]]
    return labels_of(clusters,n)

# eval method B: vetoed agglomeration (merge highest-sim pair with P>=tau; skip rejected; stop if none)
def vetoed(A,n,SM,tau):
    clusters=[[i] for i in range(n)]
    while len(clusters)>1:
        ps=all_pairsims(A,clusters); merged=False
        for k,(sim,ci,cj) in enumerate(ps):
            best_other=ps[0][0] if k>0 else (ps[1][0] if len(ps)>1 else 0.0)
            f=merge_feats(A,clusters,ci,cj,n,best_other)
            if pred(SM,f)>=tau:
                clusters=[c for kk,c in enumerate(clusters) if kk not in (ci,cj)]+[clusters[ci]+clusters[cj]]
                merged=True; break
        if not merged: break
    return labels_of(clusters,n)

# baselines on the greedy dendrogram
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

# ---- load ----
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
    e=json.load(open(f"{EMB}/{B['vid']}.json")); B["emb"]=[stdz(x) for x in e]
    B["tf"]=[tfidf(t) for t in B["texts"]]; B["tt"]=[tt(t) for t in B["texts"]]; B["ci"]=[cites(t) for t in B["texts"]]
# affinity model
AM=train_lr([(pf(B,i,j),1 if B["ref"][i]==B["ref"][j] else 0) for B in train for i in range(B["n"]) for j in range(i+1,B["n"])])
for B in bills:
    n=B["n"]; B["A"]=[[1.0 if i==j else pred(AM,pf(B,i,j)) for j in range(n)] for i in range(n)]
print("  affinity model trained", flush=True)

# cap train bills used for stop-model + tuning to n<=40 (vetoed is O(n^4); large flats belong to omnibus). val is <=16.
trs=[B for B in train if B["n"]<=40]
print(f"  stop-model train bills (n<=40): {len(trs)}/{len(train)}", flush=True)

# ---- STOP model: train on recorded greedy merges over train bills ----
stop_pairs=[]
for B in trs:
    stop_pairs += greedy_record(B["A"],B["n"],B["ref"])
print(f"  stop-model training merges: {len(stop_pairs)}  (pos {sum(1 for _,y in stop_pairs if y)})", flush=True)
SM=train_lr(stop_pairs, epochs=600, lr=0.3)

# baselines + cache dendrograms for val
for B in val:
    B["sims"],B["snap"]=greedy_dendro(B["A"],B["n"])
grid=[round(0.30+0.025*i,3) for i in range(17)]
for B in trs: B["sims"],B["snap"]=greedy_dendro(B["A"],B["n"])
Tg=max(grid,key=lambda T:st.mean([ari(cutT(B["sims"],B["snap"],T),B["ref"]) for B in trs]))

# tune tau for seq + vetoed on TRAIN
taus=[round(0.30+0.05*i,3) for i in range(11)]
tauA=max(taus,key=lambda t:st.mean([ari(seq_stop(B["A"],B["n"],SM,t),B["ref"]) for B in trs]))
tauB=max(taus,key=lambda t:st.mean([ari(vetoed(B["A"],B["n"],SM,t),B["ref"]) for B in trs]))

def ev(fn):
    a=[ari(fn(B),B["ref"]) for B in val]; pg=st.mean([len(set(fn(B))) for B in val]); return st.mean(a),pg,st.median(a),sum(1 for x in a if x>0.3),sum(1 for x in a if x<=0.001)
gg=st.mean([B["k"] for B in val])
oK=ev(lambda B:cutK(B["snap"],B["k"])); oT=ev(lambda B:bestT(B["snap"],B["ref"])); gT=ev(lambda B:cutT(B["sims"],B["snap"],Tg))
sA=ev(lambda B:seq_stop(B["A"],B["n"],SM,tauA)); sB=ev(lambda B:vetoed(B["A"],B["n"],SM,tauB))
NAMES=["sim","margin","min_sz","tot_sz","adjacent","mean_gap","cohesion","clust_frac"]
print(f"\n=== HELD-OUT VAL ({len(val)} bills, gold avg {gg:.1f} groups)  [ARI | avg groups | median | >0.3 | <=0] ===")
print(f"  oracle per-bill T* (ceiling) : {oT[0]:.3f} | {oT[1]:.1f} | {oT[2]:.3f} | {oT[3]} | {oT[4]}")
print(f"  oracle-K                     : {oK[0]:.3f} | {oK[1]:.1f} | {oK[2]:.3f} | {oK[3]} | {oK[4]}")
print(f"  static global-T={Tg:<5}        : {gT[0]:.3f} | {gT[1]:.1f} | {gT[2]:.3f} | {gT[3]} | {gT[4]}")
print(f"  SUPERVISED seq-stop (tau={tauA}) : {sA[0]:.3f} | {sA[1]:.1f} | {sA[2]:.3f} | {sA[3]} | {sA[4]}")
print(f"  SUPERVISED vetoed   (tau={tauB}) : {sB[0]:.3f} | {sB[1]:.1f} | {sB[2]:.3f} | {sB[3]} | {sB[4]}")
print(f"\n  vs static: seq {sA[0]-gT[0]:+.3f}   vetoed {sB[0]-gT[0]:+.3f}   (headroom to ceiling {oT[0]-gT[0]:+.3f})")
print(f"  stop-model weights: " + ", ".join(f"{NAMES[j]}:{SM[0][j]:+.2f}" for j in range(len(NAMES))))
