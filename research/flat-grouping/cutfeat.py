import json, glob, os, math, statistics as st

GOLD="evaluation/src/main/resources/gold"
SUBJ="evaluation/src/main/resources/subjects"

def congress_year(c): return 1789 + 2*(c-1)  # 1st congress = 1789-1791

rows=[]
for gf in sorted(glob.glob(f"{GOLD}/*.json")):
    vid=os.path.basename(gf)[:-5]
    g=json.load(open(gf))
    secs=g.get("sections",[])
    groups=g.get("groups",[])
    kRef=len(groups)
    n=len(secs)
    lens=[s.get("charLength",0) for s in secs]
    total=sum(lens)
    named=sum(1 for s in secs if s.get("heading"))
    withid=sum(1 for s in secs if s.get("identifier"))
    kinds=set(s.get("kind") for s in secs)
    # subjects/age
    sf=f"{SUBJ}/{vid}.json"
    kReal=0; congress=None; billType=g.get("billType"); policy=None
    if os.path.exists(sf):
        s=json.load(open(sf))
        kReal=len(s.get("subjects",[]))
        congress=s.get("congress"); policy=s.get("policyArea")
    age = (2026 - congress_year(congress)) if congress else None
    rows.append(dict(vid=vid,kRef=kRef,n=n,total=total,
        meanLen=round(st.mean(lens),0) if lens else 0,
        maxLen=max(lens) if lens else 0,
        stdLen=round(st.pstdev(lens),0) if len(lens)>1 else 0,
        named=named, withid=withid, nkinds=len(kinds),
        kReal=kReal, congress=congress, age=age, billType=billType))

# print table
cols=["vid","kRef","n","total","meanLen","maxLen","named","withid","nkinds","kReal","congress","age","billType"]
print("\t".join(cols))
for r in sorted(rows,key=lambda x:x["n"]):
    print("\t".join(str(r[c]) for c in cols))

# correlations with kRef (numeric feats), non-trivial subset (n>3) and all
def pearson(xs,ys):
    if len(xs)<3: return 0.0
    mx=st.mean(xs); my=st.mean(ys)
    num=sum((x-mx)*(y-my) for x,y in zip(xs,ys))
    dx=math.sqrt(sum((x-mx)**2 for x in xs)); dy=math.sqrt(sum((y-my)**2 for y in ys))
    return num/(dx*dy) if dx*dy else 0.0

feats=["n","total","meanLen","maxLen","named","withid","nkinds","kReal","age",
       "sqrt_n","log_n","log_total"]
def fval(r,f):
    if f=="sqrt_n": return math.sqrt(r["n"])
    if f=="log_n": return math.log(r["n"]+1)
    if f=="log_total": return math.log(r["total"]+1)
    return r[f] if r[f] is not None else 0
for subset,label in [(rows,"ALL 25"),([r for r in rows if r["n"]>3],"NON-TRIVIAL n>3")]:
    y=[r["kRef"] for r in subset]
    print(f"\n=== Pearson corr with kRef [{label}, n={len(subset)}] ===")
    cor=[(f,pearson([fval(r,f) for r in subset],y)) for f in feats]
    for f,c in sorted(cor,key=lambda t:-abs(t[1])):
        print(f"  {f:10s} {c:+.3f}")
