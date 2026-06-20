#!/usr/bin/env python3
"""Export the trained flat-grouping models + IDF + a parity fixture as JSON artifacts for the Scala port.

Reuses the metric_learn_v8 pipeline. Trains the SHIPPED models on ALL flat bills (the held-out 0.407
remains the validated generalization number). Writes:
  decomposition-ml/src/main/resources/flat-grouping/flat-affinity-model-v1.json
  decomposition-ml/src/main/resources/flat-grouping/flat-mergestop-model-v1.json
  decomposition-ml/src/main/resources/flat-grouping/flat-idf-v1.json
  decomposition-ml/src/test/resources/flat-grouping/parity-fixture-v1.json

Requires the GCS gold unzipped at C:/Temp/expansion (gold/, sections/, emb/) — no Ollama needed.
Run:  python research/flat-grouping/export_models.py
"""
import json, glob, os, re, math, statistics as st
from math import comb, log, exp
from collections import Counter

EXP   = "C:/Temp/expansion"
REPO  = "C:/Users/elita/source/repos2024/repcheck-bill-decomposition"
STATS = f"{REPO}/decomposition-ml/src/main/resources/standardization/standardization-stats-v1.json"
MAIN  = f"{REPO}/decomposition-ml/src/main/resources/flat-grouping"
TEST  = f"{REPO}/decomposition-ml/src/test/resources/flat-grouping"
TOPN  = 15
MAX_VETOED = 40  # bills above this are excluded from merge-stop training/tau (vetoed is O(n^4)); matches v8 cap
MIN_DF = 2       # drop corpus df<MIN_DF terms (mostly one-off bigrams) from the IDF — standard TF-IDF practice,
                 # cuts the artifact ~10x. Model is retrained self-consistently so parity holds.

STOP = set("""the a an and or of to in for on by with as at from is are be shall may not this that such any section sec
subsection paragraph subparagraph clause act code title united states public law amended amend following under
pursuant chapter part subtitle provided including include means term house senate congress bill resolution date
effective whoever person rule""".split())
USC  = re.compile(r"(\d{1,2})\s*U\.?\s?S\.?\s?C\.?\s*(?:§+\s?)?(\d+[A-Za-z0-9]*)")
PUBL = re.compile(r"[Pp]ub(?:lic)?\.?\s?L(?:aw)?\.?\s?(\d{1,3}[-–]\d{1,4})")
ACT  = re.compile(r"([A-Z][A-Za-z']+(?:\s+[A-Z][A-Za-z']+){0,5})\s+Act\s+of\s+(\d{4})")
def cites(t): return ({f"usc:{m.group(1)}:{m.group(2)}" for m in USC.finditer(t)} |
                      {f"pl:{m.group(1)}" for m in PUBL.finditer(t)} |
                      {f"act:{m.group(1).lower()}" for m in ACT.finditer(t)})

sg = json.load(open(STATS)); GM = sg["mean"]; GS = sg["std"]
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
def train_lr(pairs, epochs=500, lr=0.3):
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
    return {"w":w,"b":b,"mu":mu,"sd":sd}
def pred(M,x):
    w,b,mu,sd=M["w"],M["b"],M["mu"],M["sd"]; xs=[(x[j]-mu[j])/sd[j] for j in range(len(w))]; return sig(sum(w[j]*xs[j] for j in range(len(w)))+b)

AFF_NAMES = ["emb_cos","tfidf_cos","topterm_jaccard","cite_jaccard","position"]
MERGE_NAMES = ["sim","margin","min_size_frac","total_size_frac","adjacent","mean_index_gap_frac",
               "cohesion","clusters_remaining_frac","cross_min","cross_std","emb_avglink","tfidf_avglink","cohesion_contrast"]
def aff_feats(B,i,j):
    return [B["E"][i][j], B["F"][i][j], jac(B["tt"][i],B["tt"][j]), jac(B["ci"][i],B["ci"][j]), 1.0-abs(i-j)/B["n"]]
def within(A,X):
    if len(X)<2: return 1.0
    return sum(A[x][y] for i,x in enumerate(X) for y in X[i+1:])/comb(len(X),2)
def submean(M,X,Y): return sum(M[x][y] for x in X for y in Y)/(len(X)*len(Y))
def merge_feats(B,X,Y,best_other):
    A=B["A"]; n=B["n"]; cross=[A[x][y] for x in X for y in Y]; sim=sum(cross)/len(cross)
    cmin=min(cross); cstd=st.pstdev(cross) if len(cross)>1 else 0.0
    cg=min(abs(x-y) for x in X for y in Y); mg=sum(abs(x-y) for x in X for y in Y)/len(cross)
    wX=within(A,X); wY=within(A,Y)
    return [sim, sim-best_other, min(len(X),len(Y))/n, (len(X)+len(Y))/n, 1.0 if cg==1 else 0.0, mg/n, min(wX,wY),
            (n-len(X)-len(Y)+2)/n, cmin, cstd, submean(B["E"],X,Y), submean(B["F"],X,Y), sim-min(wX,wY)]
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
def greedy_record(B,ref):
    A=B["A"]; n=B["n"]; clusters=[[i] for i in range(n)]; recs=[]
    while len(clusters)>1:
        ps=all_pairsims(A,clusters); sim,ci,cj=ps[0]; bo=ps[1][0] if len(ps)>1 else 0.0
        f=merge_feats(B,clusters[ci],clusters[cj],bo)
        XY=clusters[ci]+clusters[cj]; lab=1 if len(set(ref[i] for i in XY))==1 else 0
        recs.append((f,lab)); clusters=[c for k,c in enumerate(clusters) if k not in (ci,cj)]+[XY]
    return recs
def vetoed(B,SM,tau):
    A=B["A"]; n=B["n"]; clusters=[[i] for i in range(n)]
    while len(clusters)>1:
        ps=all_pairsims(A,clusters); merged=False
        for k,(sim,ci,cj) in enumerate(ps):
            bo=ps[0][0] if k>0 else (ps[1][0] if len(ps)>1 else 0.0)
            f=merge_feats(B,clusters[ci],clusters[cj],bo)
            if pred(SM,f)>=tau:
                clusters=[c for kk,c in enumerate(clusters) if kk not in (ci,cj)]+[clusters[ci]+clusters[cj]]; merged=True; break
        if not merged: break
    return labels_of(clusters,n)

# ---- load all flat bills (gold + sections + RAW emb) ----
bills=[]; raw_emb={}
for sf in sorted(glob.glob(f"{EXP}/sections/*.json")):
    d=json.load(open(sf)); vid=str(d["versionId"]); secs=sorted(d["sections"],key=lambda x:x["index"]); n=len(secs)
    if n<3: continue
    gp=f"{EXP}/gold/{vid}.json"; ep=f"{EXP}/emb/{vid}.json"
    if not (os.path.exists(gp) and os.path.exists(ep)): continue
    g=json.load(open(gp)); ref=lab_g(g["groups"],n); k=len(g["groups"]); e=json.load(open(ep))
    raw_emb[vid]=e
    bills.append({"vid":vid,"texts":[s["text"] for s in secs],"n":n,"ref":ref,"k":k})
print(f"loaded {len(bills)} flat bills", flush=True)

# IDF over ALL flat bills
df=Counter()
for B in bills:
    for t in B["texts"]:
        for term in set(toks(t)): df[term]+=1
Nidf=sum(B["n"] for B in bills); IDF={t:log(Nidf/(1+c)) for t,c in df.items() if c>=MIN_DF}
def tfidf(t):
    tf=Counter(toks(t)); return {x:(1+log(c))*IDF.get(x,0.0) for x,c in tf.items()}
def tt(t):
    v=tfidf(t); return set(x for x,_ in sorted(v.items(),key=lambda z:-z[1])[:TOPN])

for B in bills:
    em=[stdz(x) for x in raw_emb[B["vid"]]]; tv=[tfidf(t) for t in B["texts"]]; n=B["n"]
    B["tt"]=[tt(t) for t in B["texts"]]; B["ci"]=[cites(t) for t in B["texts"]]
    B["E"]=[[1.0 if i==j else cos(em[i],em[j]) for j in range(n)] for i in range(n)]
    B["F"]=[[1.0 if i==j else cos(tv[i],tv[j]) for j in range(n)] for i in range(n)]

# affinity model on ALL pairs
AFF=train_lr([(aff_feats(B,i,j),1 if B["ref"][i]==B["ref"][j] else 0) for B in bills for i in range(B["n"]) for j in range(i+1,B["n"])])
for B in bills:
    n=B["n"]; B["A"]=[[1.0 if i==j else pred(AFF,aff_feats(B,i,j)) for j in range(n)] for i in range(n)]
print("affinity model trained", flush=True)

# merge-stop model on bills with n<=MAX_VETOED
trs=[B for B in bills if B["n"]<=MAX_VETOED]
stop_pairs=[]
for B in trs: stop_pairs+=greedy_record(B,B["ref"])
SM=train_lr(stop_pairs, epochs=600)
taus=[round(0.30+0.05*i,3) for i in range(8)]
TAU=max(taus,key=lambda t:st.mean([ari(vetoed(B,SM,t),B["ref"]) for B in trs]))
fit_ari=st.mean([ari(vetoed(B,SM,TAU),B["ref"]) for B in trs])
print(f"merge-stop trained on {len(trs)} bills, {len(stop_pairs)} merges; tau={TAU}; in-sample vetoed ARI={fit_ari:.3f}", flush=True)

# ---- write artifacts ----
os.makedirs(MAIN, exist_ok=True); os.makedirs(TEST, exist_ok=True)
def dump(path,obj):
    json.dump(obj, open(path,"w"), separators=(",",":")); print(f"wrote {path}  ({os.path.getsize(path)} bytes)", flush=True)
# topTermCount is a feature-extraction hyperparameter of the affinity model (top-N tf-idf terms used by
# the topterm-jaccard feature). It travels WITH the model so it can never drift from how it was trained.
dump(f"{MAIN}/flat-affinity-model-v1.json", {"featureNames":AFF_NAMES, "topTermCount":TOPN, **AFF})
dump(f"{MAIN}/flat-mergestop-model-v1.json", {"featureNames":MERGE_NAMES, "tau":TAU, **SM})
dump(f"{MAIN}/flat-idf-v1.json", {"n":Nidf, "idf":IDF})

# ---- parity fixture: deterministic ~15 bills, 3<=n<=20, run the shipped vetoed clusterer ----
fixt=[]
for B in sorted([b for b in bills if 3<=b["n"]<=20], key=lambda b:b["vid"])[:15]:
    labels=vetoed(B,SM,TAU)
    fixt.append({"versionId":B["vid"],
                 "sections":[{"text":B["texts"][i],"embedding":raw_emb[B["vid"]][i]} for i in range(B["n"])],
                 "expectedLabels":labels,   # Python vetoed output (parity target)
                 "goldLabels":B["ref"]})    # Sonnet gold grouping (behavior/quality target)
dump(f"{TEST}/parity-fixture-v1.json", fixt)
print(f"\nDONE. affinity w={[round(x,3) for x in AFF['w']]}  mergestop tau={TAU}", flush=True)
print(f"idf vocab size={len(IDF)}  fixture bills={len(fixt)}", flush=True)
