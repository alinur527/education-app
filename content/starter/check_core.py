"""Offline checks for original core lessons; no downloaded code execution."""
import hashlib
import json
import statistics
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).parent
data=json.loads((ROOT/'core.json').read_text(encoding='utf-8'))
questions={q['externalKey']:q for s in data['subjects'] for t in s['topics'] for q in t['questions']}
proofs=[]


def check(topic,num,expected,reason='Independent computation from authored operands'):
    q=questions[f'core-{topic}-q{num:02}']
    actual=next(o['textRu'] for o in q['options'] if o['id']==q['correctOptionId'])
    assert actual==str(expected),(q['externalKey'],actual,expected)
    proofs.append(dict(questionExternalKey=q['externalKey'],expectedAnswer=str(expected),method=reason,passed=True))


def n(topic,num,value,unit=''):
    number=str(int(value)) if float(value).is_integer() else str(value).replace('.',',')
    check(topic,num,number+unit)


n('numeracy-percent',1,2500*.12,' тенге')
n('numeracy-percent',2,15000*.8)
n('numeracy-percent',3,9000/.75)
n('numeracy-percent',4,18/30*100,'%')
n('numeracy-percent',5,round(2000*1.1*1.1))
n('numeracy-percent',6,55-40)
assert 1400/500 < 900/300
check('numeracy-percent',7,'500 г за 1400','Independent unit-price comparison 2.8 < 3 tenge/g')
n('numeracy-percent',8,45/.15)
n('numeracy-percent',9,(80-60)/80*100,'%')
n('numeracy-percent',10,5000*.9*.8)
n('numeracy-data',1,statistics.mean([3,7,8]))
n('numeracy-data',2,statistics.median([9,2,5]))
n('numeracy-data',3,statistics.median([1,4,8,11]))
n('numeracy-data',4,statistics.mode([2,3,3,5,7]))
n('numeracy-data',5,max([-4,2,7])-min([-4,2,7]),' °C')
n('numeracy-data',6,statistics.mean([10,10,20,20,20]))
n('numeracy-data',7,28/4)
n('numeracy-data',10,12/(12+8)*100,'%')
for q,v in [(1,8),(2,27-13),(3,12),(4,12-2),(5,8+2),(7,7+8)]:n('chem-atoms',q,v)
for q,v,u in [(1,2*1+16,' г/моль'),(2,12+2*16,' г/моль'),(3,54/18,' моль'),(4,.25*44,' г'),(5,3*2,' моль'),(9,23+35.5,' г/моль')]:n('chem-mole',q,v,u)
check('chem-mole',8,'3,01·10²³','0.5 * 6.02 = 3.01 with unchanged 10^23 exponent')
assert .5*6.02==3.01
for q,v,u in [(1,200000/100000,' км'),(2,4*50000/100000,' км'),(3,6*100000/100000,' см'),(8,45+30/60,'°'),(9,60,'')]:n('geo-map',q,v,u)
n('geo-weather',5,statistics.mean([0,4,8]),' °C')
n('geo-weather',6,9-(-3),' °C')
n('geo-weather',8,.005*1000,' л')
n('info-binary',1,int('101',2))
check('info-binary',2,format(10,'b')+'₂','Python integer formatter verifies decimal-to-binary conversion')
n('info-binary',3,2**4)
n('info-binary',4,2**5-1)
n('info-binary',5,3*8)
n('info-binary',6,2*1024)
n('info-binary',7,128*2,' байт')
n('info-binary',8,2**3)
n('info-binary',9,(12-1).bit_length())
for q,v in [(1,5*2),(2,3+4*2),(3,19//4),(4,19%4),(7,sum(range(1,5))),(9,2**5),(10,2+4)]:n('info-python',q,v)
check('info-python',5,'==','Python comparison grammar, distinct from assignment =')
check('info-python',6,','.join(map(str,range(2,5))),'range endpoints verified by Python itself')
check('info-python',8,str(10>=10),'Direct Boolean boundary comparison')
n('reading-evidence',8,95-70,' литров')

expected_multi={'core-chem-atoms-q10':{'A','B','D'},'core-bio-cell-q10':{'A','B','D'},'core-bio-photosynthesis-q10':{'A','B'}}
for key,expected in expected_multi.items():
    assert set(questions[key]['correctOptionIds'])==expected
    proofs.append(dict(questionExternalKey=key,expectedAnswer=sorted(expected),method='Model independent re-reading of definition-based truth set',passed=True))
for key in ['core-chem-mole-q10','core-geo-map-q10']:
    assert questions[key]['correctPairs']==[dict(leftId='L1',rightId='R2'),dict(leftId='L2',rightId='R1')]
    proofs.append(dict(questionExternalKey=key,expectedAnswer=questions[key]['correctPairs'],method='Independent re-reading of both explicit relationships',passed=True))

assert len(data['subjects'])==8
assert len(questions)==160
titles=set()
contexts={}
for subject in data['subjects']:
    assert len(subject['topics'])==2
    for topic in subject['topics']:
        assert len(topic['questions'])==10
        assert min(len(topic['theoryRu']),len(topic['theoryKz']))>=1500
        assert topic['officialSourceId'] and topic['officialCode'] and topic['sourceUrls']
        for c in topic.get('contexts',[]):
            assert c['externalKey'] not in contexts
            assert len(c['contentRu'])>500 and len(c['contentKz'])>500 and c['original']
            contexts[c['externalKey']]=c
        for q in topic['questions']:
            assert q['titleRu'] not in titles,q['titleRu']
            titles.add(q['titleRu'])
            assert q['titleKz'] and q['explanationRu'] and q['explanationKz']
            assert q['sourceType']=='AI_GENERATED' and 'NOT_HUMAN' in q['reviewMethod']
            options=q['options'];ids={o['id'] for o in options}
            assert len(ids)==len(options)
            assert all(len({o['text'+lang] for o in options})==len(options) for lang in ('Ru','Kz'))
            if q['questionType']=='SINGLE_CHOICE':assert q['correctOptionId'] in ids
            elif q['questionType']=='MULTIPLE_SELECT':assert 1<=len(q['correctOptionIds'])<=3 and set(q['correctOptionIds'])<=ids
            elif q['questionType']=='MATCHING':
                assert len(q['correctPairs'])==len(q['leftOptions'])==2
                assert all(p['rightId'] in ids for p in q['correctPairs'])
            else:raise AssertionError(q['questionType'])
            if 'contextExternalKey' in q:assert q['contextExternalKey'] in contexts
            if subject['subjectRu']=='Основы права':
                assert topic['legalEffectiveFrom']=='2026-07-01'
                assert q['sourceEvidence']['article'] and q['sourceEvidence']['effectiveFrom']=='2026-07-01'

assert len(contexts)==2
assert sum('contextExternalKey' in q for q in questions.values())==20
summary=dict(schema='education-core-checks/v1',sourceSha256=hashlib.sha256((ROOT/'core.json').read_bytes()).hexdigest(),structuralQuestionCount=len(questions),distinctRussianPrompts=len(titles),contextCount=len(contexts),contextQuestionCount=20,questionTypes=dict(Counter(q['questionType'] for q in questions.values())),explicitAnswerChecks=len(proofs),proofs=proofs,limitations=['Conceptual, legal, historical and translation checks are model review, not human certification','Law content reads Constitution K2600000000 effective 2026-07-01; older article numbering must not be substituted','Reading passages and their scenarios are original fictional educational examples','This is starter coverage, not an official exam-ready complete bank'])
(ROOT/'core-proofs.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if k not in ('proofs','limitations')},ensure_ascii=False))
