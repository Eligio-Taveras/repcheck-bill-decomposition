import json, glob, os, re, math, statistics as st, urllib.request
from math import comb, log, exp
from collections import Counter

SEC="C:/Temp/flat-sections"
GOLD="C:/Users/elita/source/repos2024/repcheck-bill-decomposition/evaluation/src/main/resources/gold"
STATS="C:/Users/elita/source/repos2024/repcheck-bill-decomposition/decomposition-ml/src/main/resources/standardization/standardization-stats-v1.json"
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
def hac(affinity, k):  # affinity[i][j] in [0,1]; distance=1-aff; average linkage; cut at k
    n=len(affinity)
    if k>=n: return list(range(n))
    D=[[1-affinity[i][j] for j in range(n)] for i in range(n)]
    members={i:[i] for i in range(n)}; active=set(range(n)); cid=n
    while len(active)>k:
        al=list(active); best=None
        for ii in range(len(al)):
            for jj in range(ii+1,len(al)):
                a,b=al[ii],al[jj]; d=sum(D[x][y] for x in members[a] for y in members[b])/(len(members[a])*len(members[b]))
                if best is None or d<best[0]: best=(d,a,b)
        _,a,b=best; members[cid]=members[a]+members[b]; active.discard(a);active.discard(b);active.add(cid); del members[a];del members[b]; cid+=1
    lab=[0]*n
    for li,c in enumerate(active):
        for m in members[c]: lab[m]=li
    return lab

# ---- load bills, embed, tfidf ----
files=sorted(glob.glob(f"{SEC}/*.json"))
bills=[]
allsec=[]
for sf in files:
    d=json.load(open(sf)); vid=d["versionId"]; secs=sorted(d["sections"],key=lambda x:x["index"]); n=len(secs)
    if n<3: continue
    g=json.load(open(f"{GOLD}/{vid}.json")); ref=labels_from_groups(g["groups"],n); k=len(g["groups"])
    bills.append({"vid":vid,"texts":[s["text"] for s in secs],"n":n,"ref":ref,"k":k})
    allsec+=[s["text"] for s in secs]
df=Counter()
for t in allsec:
    for term in set(toks(t)): df[term]+=1
N=len(allsec); IDF={t:log(N/(1+c)) for t,c in df.items()}
def tfidf(t):
    tf=Counter(toks(t)); return {x:(1+log(c))*IDF.get(x,0) for x,c in tf.items()}
def topterms(t):
    v=tfidf(t); return set(x for x,_ in sorted(v.items(),key=lambda z:-z[1])[:TOPN])

for B in bills:
    B["emb"]=[stdz(e) for e in embed([t[:4000] for t in B["texts"]])]
    B["tf"]=[tfidf(t) for t in B["texts"]]
    B["tt"]=[topterms(t) for t in B["texts"]]
    B["ci"]=[cites(t) for t in B["texts"]]

def pair_feats(B,i,j):
    return [cos(B["emb"][i],B["emb"][j]), cos(B["tf"][i],B["tf"][j]),
            jac(B["tt"][i],B["tt"][j]), jac(B["ci"][i],B["ci"][j]), 1.0-abs(i-j)/B["n"]]

# baselines (no training): embed-only, blend(emb+tfidf)
def baseline_ari(scorefn):
    out=[]
    for B in bills:
        n=B["n"]; aff=[[scorefn(B,i,j) if i!=j else 1.0 for j in range(n)] for i in range(n)]
        out.append(ari(hac(aff,B["k"]),B["ref"]))
    return st.mean(out)
emb_only=baseline_ari(lambda B,i,j: (cos(B["emb"][i],B["emb"][j])+1)/2)
blend=baseline_ari(lambda B,i,j: (cos(B["emb"][i],B["emb"][j])+cos(B["tf"][i],B["tf"][j])+1)/2 - 0.0)

# ---- supervised LogReg with 5-fold CV over BILLS ----
def sigmoid(z): return 1/(1+exp(-max(-30,min(30,z))))
def train(pairs):  # pairs: list of (feats, label)
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

idx=list(range(len(bills))); K=5
folds=[idx[i::K] for i in range(K)]
sup=[]; weights=[]
for f in range(K):
    te=set(folds[f]); tr=[i for i in idx if i not in te]
    pairs=[(pair_feats(bills[bi],i,j), 1 if bills[bi]["ref"][i]==bills[bi]["ref"][j] else 0)
           for bi in tr for i in range(bills[bi]["n"]) for j in range(i+1,bills[bi]["n"])]
    model=train(pairs); weights.append(model[0])
    for bi in folds[f]:
        B=bills[bi]; n=B["n"]
        aff=[[predict(model,pair_feats(B,i,j)) if i!=j else 1.0 for j in range(n)] for i in range(n)]
        sup.append(ari(hac(aff,B["k"]),B["ref"]))
print(f"=== {len(bills)} flat bills, cluster at oracle K ===")
print(f"  embed-only            : {emb_only:.3f}")
print(f"  unweighted blend       : {blend:.3f}")
print(f"  SUPERVISED (5-fold CV) : {st.mean(sup):.3f}")
avgw=[st.mean([weights[f][j] for f in range(K)]) for j in range(5)]
print(f"  learned weights [emb_cos, tfidf_cos, shared_terms, shared_cites, position]: {[round(x,2) for x in avgw]}")
