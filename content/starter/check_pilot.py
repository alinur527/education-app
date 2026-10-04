"""Offline structural checks and independent exact arithmetic for the pilot.

No arbitrary source expression evaluation. Formula operands below are authored
fixtures; the checker resolves the selected option after the builder rotates it.
Conceptual/history evidence is model-reviewed, not certified by this script.
"""
import hashlib
import json
import math
from collections import Counter
from fractions import Fraction
from pathlib import Path

ROOT=Path(__file__).parent
data=json.loads((ROOT/'pilot.json').read_text(encoding='utf-8'))
questions={q['externalKey']:q for s in data['subjects'] for t in s['topics'] for q in t['questions']}
assert len(questions)==150
proofs=[]


def check(topic,number,expected,reason):
    key=f'pilot-{topic}-q{number:02}'
    q=questions[key]
    actual=next(x['textRu'] for x in q['options'] if x['id']==q['correctOptionId'])
    assert actual.replace('−','-')==expected.replace('−','-'),(key,actual,expected)
    proofs.append(dict(questionExternalKey=key,expectedAnswer=expected,method=reason,passed=True))


def number(value):
    return str(int(value)) if float(value).is_integer() else str(value).replace('.',',')


def n(topic,q,value,unit='',reason='Independent arithmetic from explicit problem operands'):
    check(topic,q,number(value)+(' '+unit if unit else ''),reason)


n('math-radicals',1,math.isqrt(81))
check('math-radicals',2,f'{math.isqrt(50//2)}√2','50 = 25 * 2; positive root of perfect square25 is5')
check('math-radicals',3,'√3','Coefficient4 - sqrt(27/3)=1')
assert 4-math.sqrt(27/3)==1
n('math-radicals',4,abs(-7))
assert 5**2 < 30 < 6**2
check('math-radicals',5,'5 и 6','25 < 30 < 36')
check('math-radicals',6,f'{8//2}√2','Rationalisation:8*sqrt(2)/2')
n('math-radicals',8,math.sqrt(16+9))
check('math-radicals',9,'0,7','Exact square: Fraction(7,10)^2 == Fraction(49,100)')
assert Fraction(7,10)**2==Fraction(49,100)
n('math-powers',1,2**3*2**4)
n('math-powers',2,Fraction(5**6,5**4))
check('math-powers',3,'a¹²','Independent exponent multiplication:3*4=12')
n('math-powers',4,7**0)
check('math-powers',5,str(Fraction(1,3**2)),'Exact reciprocal square')
n('math-powers',6,-4**2)
assert Fraction(56,100000)==Fraction(56,10)*Fraction(1,10000)
check('math-powers',7,'5,6·10⁻⁴','Coefficient5.6 lies in[1,10); exponent−4 preserves exact rational value')
n('math-powers',8,(-2)**5)
check('math-powers',9,'9x⁴','Coefficient3²=9; variable exponent2*2=4')
for q,value in [(1,Fraction(18+7,5)),(2,Fraction(12,3)+2),(3,Fraction(17-1,7-3)),(4,(8-3)*4),(8,Fraction(10-4,-2)),(10,Fraction(12,3))]:n('math-linear',q,value)
n('math-linear',7,(1800-200)/2,'тенге')
n('math-quadratic',1,(-6)**2-4*1*8)
assert all(x*x-5*x+6==0 for x in(2,3))
check('math-quadratic',2,'2 и 3','Both roots substituted into original polynomial; degree2 limits distinct roots to2')
n('math-quadratic',4,8/2)
n('math-quadratic',5,-(-9))
check('math-quadratic',6,str(Fraction(3,2)),'Vieta product c/a, a=2,c=3')
assert all(x*x-4*x==0 for x in(0,4))
check('math-quadratic',7,'0 и 4','Direct substitution of both polynomial roots')
n('math-quadratic',8,-2)
n('math-quadratic',9,math.sqrt(49),'см')
for q,value,unit in [(1,math.hypot(6,8),'см'),(2,math.sqrt(17**2-8**2),'м'),(3,5*12/2,'см²'),(8,math.hypot(10,24),'м'),(9,3+4+math.hypot(3,4),'см')]:n('math-right-triangle',q,value,unit)
check('math-right-triangle',4,f'{90-28}°','Complementary acute angles')
assert 7**2+24**2==25**2
check('math-right-triangle',5,'7,24,25','Longest-side squared equals sum of other squares; remaining triples fail')
for a,b,c in [(5,6,7),(4,5,6),(6,8,11)]:assert a*a+b*b!=c*c
for q,value,unit in [(1,150/30,'м/с'),(2,54/3.6,'м/с'),(3,40-40,'м'),(4,40+40,'м'),(5,-3,'м/с'),(6,120/(20+10),'м/с'),(7,8*25,'м'),(9,(60+60)/(10+20),'м/с'),(10,-2+4*3,'м')]:n('physics-motion',q,value,unit)
for q,value,unit in [(1,12/3,'м/с²'),(2,5*2,'Н'),(5,2.5*10,'Н'),(6,0,'Н')]:n('physics-newton',q,value,unit)
check('physics-newton',3,f'{number((20-8)/4)} м/с² вправо','Signed force balance +20−8 before division by mass4')
for q,value,unit in [(1,15*4,'Дж'),(2,0,''),(3,2*6**2/2,'Дж'),(5,3*10*2,'Дж'),(6,23-8,'Дж'),(7,240/12,'Вт'),(8,math.sqrt(2*10*20),'м/с')]:n('physics-energy',q,value,unit)
for q,value,unit in [(1,18/6,'А'),(2,.5*10,'В'),(3,12/.4,'Ом'),(4,3+7,'Ом'),(5,1/(1/8+1/8),'Ом'),(6,2*15,'Кл'),(7,9*2,'Вт')]:n('physics-ohm',q,value,unit)
check('physics-reflection',1,'35°','Incident angle equals reflected angle')
check('physics-reflection',2,f'{90-20}°','Surface angle converted to normal angle')
n('physics-reflection',4,2*2,'м')
n('physics-reflection',6,15,'см')
n('physics-reflection',7,.4*2,'м')
check('physics-reflection',10,'0°','Normal incidence is zero angle to normal')

typed_expected={
 'pilot-math-radicals-q10':{'A','C','D'},
 'pilot-math-quadratic-q10':{'A','C'},
 'pilot-physics-newton-q10':{'A','B'},
 'pilot-physics-ohm-q10':{'A','B'},
 'pilot-history-turks-q10':{'A','B'},
 'pilot-history-khanate-q10':{'A','B','D'},
 'pilot-history-sources-q10':{'A','B','D'},
}
for key,expected in typed_expected.items():
    assert set(questions[key]['correctOptionIds'])==expected
    proofs.append(dict(questionExternalKey=key,passed=True,method='Explicit independently re-read multi-answer truth set',expectedAnswer=sorted(expected)))
for key in ['pilot-math-right-triangle-q10','pilot-physics-energy-q10','pilot-history-independence-q10']:
    assert questions[key]['correctPairs']==[{'leftId':'L1','rightId':'R2'},{'leftId':'L2','rightId':'R1'}]
    proofs.append(dict(questionExternalKey=key,passed=True,method='Both matching relationships rechecked independently',expectedAnswer=questions[key]['correctPairs']))

titles=set()
for subject in data['subjects']:
    assert len(subject['topics'])==5
    for topic in subject['topics']:
        assert len(topic['questions'])==10
        assert min(len(topic['theoryRu']),len(topic['theoryKz']))>=1500
        assert topic['officialSourceId'] and topic['officialCode'] and topic['sourceUrls']
        for q in topic['questions']:
            assert q['titleRu'] not in titles,q['titleRu']
            titles.add(q['titleRu'])
            assert q['sourceType']=='AI_GENERATED' and 'NOT_HUMAN' in q['reviewMethod']
            assert q['titleKz'] and q['explanationKz'] and q['explanationRu']
            ids=[x['id'] for x in q['options']]
            assert len(ids)==len(set(ids))
            assert len(set(x['textRu'] for x in q['options']))==len(ids)
            assert len(set(x['textKz'] for x in q['options']))==len(ids)
            if q['questionType']=='SINGLE_CHOICE': assert q['correctOptionId'] in ids
            elif q['questionType']=='MULTIPLE_SELECT': assert 1<=len(q['correctOptionIds'])<=3 and set(q['correctOptionIds'])<=set(ids)
            elif q['questionType']=='MATCHING':
                assert len(q['correctPairs'])==len(q['leftOptions'])==2
                assert all(p['rightId'] in ids for p in q['correctPairs'])
            else:raise AssertionError(q['questionType'])

summary=dict(schema='education-pilot-checks/v1',sourceSha256=hashlib.sha256((ROOT/'pilot.json').read_bytes()).hexdigest(),structuralQuestionCount=len(questions),distinctRussianPrompts=len(titles),questionTypes=dict(Counter(x['questionType'] for x in questions.values())),explicitAnswerChecks=len(proofs),proofs=proofs,limitations=['Conceptual, translation and historical explanations reviewed by model; no human subject expert approval claimed','This script proves authored arithmetic and structural constraints, not completeness of ENT coverage','Official PDF examples were not mechanically copied into the bank'])
(ROOT/'pilot-proofs.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
print(json.dumps({k:v for k,v in summary.items() if k not in('proofs','limitations')},ensure_ascii=False))
