import json, glob, os, math, statistics as st, itertools
GOLD="evaluation/src/main/resources/gold"; SUBJ="evaluation/src/main/resources/subjects"
rows=[]
for gf in sorted(glob.glob(f"{GOLD}/*.json")):
    vid=os.path.basename(gf)[:-5]; g=json.load(open(gf)); secs=g.get("sections",[])
    lens=[s.get("charLength",0) for s in secs]; n=len(secs)
    sf=f"{SUBJ}/{vid}.json"; kReal=0
    if os.path.exists(sf): kReal=len(json.load(open(sf)).get("subjects",[]))
    rows.append(dict(vid=vid,kRef=len(g.get("groups",[])),n=n,
        total=sum(lens), meanLen=(st.mean(lens) if lens else 0),
        maxLen=(max(lens) if lens else 0), kReal=kReal,
        conc=(max(lens)/sum(lens) if lens and sum(lens) else 0)))  # concentration
nt=[r for r in rows if r["n"]>3]
FLAT={"8966","189669","415327","323852"}

# candidate feature transforms (all cheap, no embeddings)
def feats(r):
    return {
      "n^0.7": r["n"]**0.7, "sqrt_n": math.sqrt(r["n"]), "log_n": math.log(r["n"]),
      "log_total": math.log(r["total"]+1), "log_maxLen": math.log(r["maxLen"]+1),
      "log_meanLen": math.log(r["meanLen"]+1), "conc": r["conc"], "log_kReal": math.log(r["kReal"]+1),
    }
FN=list(feats(rows[0]).keys())

def ols(X, y):  # X: list of rows (each list incl 1.0 bias), returns coef via normal equations
    import math
    m=len(X[0]); 
    # build XtX, Xty
    XtX=[[sum(X[r][i]*X[r][j] for r in range(len(X))) for j in range(m)] for i in range(m)]
    Xty=[sum(X[r][i]*y[r] for r in range(len(X))) for i in range(m)]
    # gaussian elimination
    A=[row[:]+[Xty[i]] for i,row in enumerate(XtX)]
    for c in range(m):
        p=max(range(c,m), key=lambda r:abs(A[r][c]))
        A[c],A[p]=A[p],A[c]
        if abs(A[c][c])<1e-12: A[c][c]=1e-12
        for r in range(m):
            if r!=c:
                f=A[r][c]/A[c][c]
                for k in range(c,m+1): A[r][k]-=f*A[c][k]
    return [A[i][m]/A[i][i] for i in range(m)]

def evalmodel(fnames):
    X=[[1.0]+[feats(r)[f] for f in fnames] for r in nt]; y=[r["kRef"] for r in nt]
    coef=ols(X,y)
    def pred(r):
        v=coef[0]+sum(coef[i+1]*feats(r)[f] for i,f in enumerate(fnames))
        return max(2, min(round(v), r["n"]-1))
    err=[abs(pred(r)-r["kRef"]) for r in nt]
    flaterr=[abs(pred(r)-r["kRef"]) for r in nt if r["vid"] in FLAT]
    return st.mean(err), st.mean(flaterr), coef, pred

# rank 1- and 2-feature models by non-trivial MAE
results=[]
for k in (1,2):
    for combo in itertools.combinations(FN,k):
        mae,fmae,coef,pred=evalmodel(list(combo))
        results.append((mae,fmae,combo,pred))
results.sort(key=lambda t:t[0])
print("TOP models by non-trivial MAE (then flat-MAE):")
print(f"{'features':32s} {'MAE':>5s} {'flatMAE':>7s}")
for mae,fmae,combo,pred in results[:8]:
    print(f"{','.join(combo):32s} {mae:5.2f} {fmae:7.2f}")

print("\nPredictions for the 4 FLAT bills (kRef vs best models):")
flat=[r for r in nt if r["vid"] in FLAT]
top=results[:5]
hdr=f"{'vid':9s}{'kRef':>5s}{'n':>4s}"+"".join(f"{','.join(c)[:14]:>15s}" for _,_,c,_ in top)
print(hdr)
for r in sorted(flat,key=lambda x:x['n']):
    line=f"{r['vid']:9s}{r['kRef']:>5d}{r['n']:>4d}"+"".join(f"{p(r):>15d}" for _,_,_,p in top)
    print(line)
print("\nbaseline round(n^0.7):", {r['vid']:round(r['n']**0.7) for r in sorted(flat,key=lambda x:x['n'])})
print("\n415327 vs 323852 (near-identical structure, kRef 10 vs 3):")
for r in rows:
    if r["vid"] in ("415327","323852"):
        print(f"  {r['vid']}: kRef={r['kRef']} n={r['n']} total={r['total']} meanLen={r['meanLen']:.0f} maxLen={r['maxLen']} conc={r['conc']:.2f} kReal={r['kReal']}")
