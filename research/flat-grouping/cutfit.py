import json, glob, os, math, statistics as st
GOLD="evaluation/src/main/resources/gold"
rows=[]
for gf in sorted(glob.glob(f"{GOLD}/*.json")):
    g=json.load(open(gf)); secs=g.get("sections",[])
    rows.append(dict(vid=os.path.basename(gf)[:-5], kRef=len(g.get("groups",[])),
        n=len(secs), total=sum(s.get("charLength",0) for s in secs)))

nt=[r for r in rows if r["n"]>3]  # 11 non-trivial (trivial bills → k=1 via minK guard anyway)

def fit_power(rows):  # kRef = a * n^b  via log-log least squares
    xs=[math.log(r["n"]) for r in rows]; ys=[math.log(r["kRef"]) for r in rows]
    mx=st.mean(xs); my=st.mean(ys)
    b=sum((x-mx)*(y-my) for x,y in zip(xs,ys))/sum((x-mx)**2 for x in xs)
    a=math.exp(my-b*mx); return a,b

def fit_lin(rows,fx):  # kRef = a*fx(n) (through-origin-ish least squares with intercept)
    xs=[fx(r) for r in rows]; ys=[r["kRef"] for r in rows]
    mx=st.mean(xs); my=st.mean(ys)
    b=sum((x-mx)*(y-my) for x,y in zip(xs,ys))/sum((x-mx)**2 for x in xs)
    a=my-b*mx; return a,b

a,b=fit_power(nt); print(f"power law (non-trivial):  k = {a:.3f} * n^{b:.3f}")
ia,ib=fit_lin(nt, lambda r: math.sqrt(r["n"])); print(f"sqrt linear:              k = {ia:.3f} + {ib:.3f}*sqrt(n)")

def clamp(k,n): return max(2, min(round(k), n-1))
def evalf(name, pred):
    errs=[]; within30=0
    print(f"\n{name}")
    print(f"  {'vid':8s} n    kRef  kPred  |err|  within±30%?")
    for r in nt:
        kp=clamp(pred(r), r["n"]); e=abs(kp-r["kRef"]); errs.append(e)
        w = abs(kp-r["kRef"])<=0.30*r["kRef"]; within30+=w
        print(f"  {r['vid']:8s} {r['n']:<4d} {r['kRef']:<5d} {kp:<5d}  {e:<5d}  {'yes' if w else 'no'}")
    print(f"  MAE={st.mean(errs):.2f}  median|err|={st.median(errs):.1f}  within±30%: {within30}/{len(nt)}")

evalf("k = 0.912*S (DP-0 old formula, S=kReal? no — use n here as sanity)", lambda r: 0.912*r["n"])
evalf(f"power: {a:.3f}*n^{b:.3f}", lambda r: a*r["n"]**b)
evalf(f"sqrt:  {ia:.3f}+{ib:.3f}*sqrt(n)", lambda r: ia+ib*math.sqrt(r["n"]))
evalf("simple: 1.6*sqrt(n)", lambda r: 1.6*math.sqrt(r["n"]))
evalf("simple: round(n^0.7)", lambda r: r["n"]**0.7)
