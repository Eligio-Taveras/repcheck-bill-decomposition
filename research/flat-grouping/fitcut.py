import json, glob, os, math, statistics as st, sys, itertools

# ---- load gold (features + target kRef) from one or more dirs ----
def load(dirs):
    rows=[]
    seen=set()
    for d in dirs:
        for gf in sorted(glob.glob(f"{d}/*.json")):
            vid=os.path.basename(gf)[:-5]
            if vid in seen: continue
            try: g=json.load(open(gf))
            except: continue
            secs=g.get("sections",[]); groups=g.get("groups",[])
            if not secs or not groups: continue
            lens=[s.get("charLength",0) for s in secs]; n=len(secs)
            if n<4: continue   # trivial bills → k=1 by guard; formula domain is n>=4
            seen.add(vid)
            total=sum(lens); mx=max(lens); mn=st.mean(lens)
            rows.append(dict(vid=vid, kRef=len(groups), n=n, total=total,
                meanLen=mn, maxLen=mx, stdLen=(st.pstdev(lens) if n>1 else 0),
                conc=(mx/total if total else 0),
                nLong=sum(1 for L in lens if L>2*mn)))
    return rows

FEATS={
 "n": lambda r:r["n"], "sqrt_n": lambda r:math.sqrt(r["n"]), "log_n": lambda r:math.log(r["n"]),
 "np07": lambda r:r["n"]**0.7,
 "log_total": lambda r:math.log(r["total"]+1), "log_maxLen": lambda r:math.log(r["maxLen"]+1),
 "log_meanLen": lambda r:math.log(r["meanLen"]+1), "log_stdLen": lambda r:math.log(r["stdLen"]+1),
 "conc": lambda r:r["conc"], "nLong": lambda r:r["nLong"], "log_nLong": lambda r:math.log(r["nLong"]+1),
}

def matrix(rows, fnames):
    X=[[1.0]+[FEATS[f](r) for f in fnames] for r in rows]
    y=[float(r["kRef"]) for r in rows]
    return X,y

def standardize(X):  # standardize columns 1.. (keep bias col 0)
    m=len(X[0]); cols=list(zip(*X))
    mus=[0.0]+[st.mean(cols[j]) for j in range(1,m)]
    sds=[1.0]+[(st.pstdev(cols[j]) or 1.0) for j in range(1,m)]
    Xs=[[ (X[i][j]-mus[j])/sds[j] if j>0 else 1.0 for j in range(m)] for i in range(len(X))]
    return Xs, mus, sds

def ridge(X,y,lam):
    m=len(X[0])
    XtX=[[sum(X[r][i]*X[r][j] for r in range(len(X))) for j in range(m)] for i in range(m)]
    for i in range(1,m): XtX[i][i]+=lam
    Xty=[sum(X[r][i]*y[r] for r in range(len(X))) for i in range(m)]
    A=[XtX[i][:]+[Xty[i]] for i in range(m)]
    for c in range(m):
        p=max(range(c,m),key=lambda r:abs(A[r][c])); A[c],A[p]=A[p],A[c]
        if abs(A[c][c])<1e-12: A[c][c]=1e-12
        for r in range(m):
            if r!=c:
                f=A[r][c]/A[c][c]
                for k in range(c,m+1): A[r][k]-=f*A[c][k]
    return [A[i][m]/A[i][i] for i in range(m)]

def predict(w,xrow): return sum(w[i]*xrow[i] for i in range(len(w)))

def cv_mae(rows, fnames, lam, K=5):
    idx=sorted(range(len(rows)), key=lambda i:int(__import__('hashlib').md5(rows[i]['vid'].encode()).hexdigest(),16))
    folds=[idx[i::K] for i in range(K)]
    errs=[]
    for k in range(K):
        te=set(folds[k]); tr=[i for i in idx if i not in te]
        Xtr,ytr=matrix([rows[i] for i in tr],fnames); Xte,yte=matrix([rows[i] for i in te],fnames)
        Xtr,mus,sds=standardize(Xtr)
        Xte=[[ (Xte[i][j]-mus[j])/sds[j] if j>0 else 1.0 for j in range(len(mus))] for i in range(len(Xte))]
        w=ridge(Xtr,ytr,lam)
        for i,r in enumerate([rows[j] for j in folds[k]]):
            p=max(2,min(round(predict(w,Xte[i])), r["n"]-1))
            errs.append(abs(p-r["kRef"]))
    return st.mean(errs)

def best_lambda(rows,fnames):
    return min(((cv_mae(rows,fnames,l),l) for l in (0.0,0.1,0.3,1,3,10,30)), key=lambda t:t[0])

def main():
    dirs=sys.argv[1:] or ["/c/Temp/expansion/gold"]
    rows=load(dirs)
    print(f"loaded {len(rows)} bills (n>=4), kRef range {min(r['kRef'] for r in rows)}-{max(r['kRef'] for r in rows)}")
    # baselines (no fit)
    def base_mae(fn): return st.mean([abs(max(2,min(fn(r),r['n']-1))-r['kRef']) for r in rows])
    print(f"baseline round(n^0.7) MAE = {base_mae(lambda r:round(r['n']**0.7)):.2f}")
    # forward selection by CV MAE
    pool=list(FEATS.keys()); chosen=[]
    print("\nforward selection (CV MAE, 5-fold):")
    for step in range(6):
        cand=[]
        for f in pool:
            if f in chosen: continue
            mae,lam=best_lambda(rows,chosen+[f])
            cand.append((mae,lam,f))
        if not cand: break
        mae,lam,f=min(cand,key=lambda t:t[0])
        chosen.append(f)
        print(f"  +{f:12s} -> features={chosen}  CV-MAE={mae:.3f}  lambda={lam}")
    # final coefficients on full data (standardized) for the chosen set
    Xs,mus,sds=standardize(matrix(rows,chosen)[0]); _,y=matrix(rows,chosen)
    _,lam=best_lambda(rows,chosen); w=ridge(Xs,y,lam)
    print(f"\nfinal model features={chosen} lambda={lam}")
    print("standardized coefficients:", {("bias" if i==0 else chosen[i-1]):round(w[i],3) for i in range(len(w))})

if __name__=="__main__": main()
