import json, os, statistics as st
from math import comb
from collections import Counter

NEW  = ["137916","230292","387711","403475","332586","335134"]          # 6 new omnibus (temp gold)
EXIST= ["148391","375702","244276","150025","357076","356661"]          # existing omnibus-ish (committed gold)
TMP  = "C:/Temp/expansion/gold"
COMM = "C:/Users/elita/source/repos2024/repcheck-bill-decomposition/evaluation/src/main/resources/gold"

def labels_from_groups(groups, n):
    lab=[-1]*n
    for gi,g in enumerate(groups):
        for si in g.get("sectionIndices",[]):
            if 0<=si<n and lab[si]==-1: lab[si]=gi
    nx=len(groups)
    for i in range(n):
        if lab[i]==-1: lab[i]=nx; nx+=1
    return lab

def ari(pred, gold):
    n=len(pred)
    if n<2: return 1.0
    cont=Counter(zip(pred,gold)); a=Counter(pred); b=Counter(gold)
    idx=sum(comb(v,2) for v in cont.values()); sa=sum(comb(v,2) for v in a.values()); sb=sum(comb(v,2) for v in b.values())
    exp=sa*sb/comb(n,2); mx=(sa+sb)/2
    return 1.0 if mx==exp else (idx-exp)/(mx-exp)

rows=[]
for vid in NEW+EXIST:
    path = f"{TMP}/{vid}.json" if os.path.exists(f"{TMP}/{vid}.json") else f"{COMM}/{vid}.json"
    if not os.path.exists(path):
        print(f"{vid}: NOT LABELED YET ({path})"); continue
    g=json.load(open(path)); n=len(g["sections"]); refK=len(g["groups"])
    ref=labels_from_groups(g["groups"], n)
    pred=list(range(n))                     # each section = its own concept
    a=ari(pred, ref)
    rows.append((vid, n, refK, round(n/refK,1), a))
    print(f"{vid:8s} n={n:4d} concepts={refK:3d}  sec/concept={n/refK:4.1f}  ARI[section=concept]={a:.3f}")
if rows:
    print(f"\n=== {len(rows)} omnibus bills ===")
    print(f"MEAN ARI[section=concept vs Sonnet] = {st.mean([r[4] for r in rows]):.3f}")
    print(f"MEAN sections/concept = {st.mean([r[3] for r in rows]):.1f}  (1.0 = no sprawl; higher = more sprawl)")
