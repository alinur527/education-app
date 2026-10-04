"""Original bilingual pilot authoring; never executes downloaded source content."""
import json
import re
from pathlib import Path

OUT = Path(__file__).parent
SUBJECTS = []
CURRENT = None
CHECKS = dict(sourceRead=True, extractionChecked=True, answerChecked=True, translationChecked=True, explanationChecked=True)


def subject(ru, kk):
    global CURRENT
    CURRENT = dict(subjectRu=ru, subjectKz=kk, topics=[])
    SUBJECTS.append(CURRENT)


def topic(key, ru, kk, source, code, urls, theory_ru, theory_kk):
    t = dict(externalKey=key, titleRu=ru, titleKz=kk, officialSourceId=source, officialCode=code, sourceUrls=urls, theoryRu=theory_ru.strip(), theoryKz=theory_kk.strip(), questions=[])
    CURRENT['topics'].append(t)
    return t


def option(text):
    parts = text.split('¦')
    return dict(textRu=parts[0], textKz=parts[-1])


def single(t, ru, kk, options, er, ek, difficulty='easy'):
    values = [option(x) for x in options]
    shift = len(t['questions']) % len(values)
    values = values[shift:] + values[:shift]
    correct = (-shift) % len(values)
    values = [dict(id=chr(65+i), **v) for i,v in enumerate(values)]
    t['questions'].append(dict(titleRu=ru, titleKz=kk, questionType='SINGLE_CHOICE', options=values, correctOptionId=chr(65+correct), explanationRu=er, explanationKz=ek, difficulty=difficulty, answerEvidence=er, reviewChecks=CHECKS.copy(), reviewMethod='MODEL_SOURCE_AND_REASONING_CHECK; NOT_HUMAN_REVIEW'))


def multiple(t, ru, kk, options, correct, er, ek):
    t['questions'].append(dict(titleRu=ru, titleKz=kk, questionType='MULTIPLE_SELECT', options=[dict(id=chr(65+i), **option(v)) for i,v in enumerate(options)], correctOptionIds=correct, explanationRu=er, explanationKz=ek, difficulty='medium', answerEvidence=er, reviewChecks=CHECKS.copy(), reviewMethod='MODEL_SOURCE_AND_REASONING_CHECK; NOT_HUMAN_REVIEW'))


def matching(t, ru, kk, left, right, pairs, er, ek):
    t['questions'].append(dict(titleRu=ru, titleKz=kk, questionType='MATCHING', leftOptions=[dict(id=f'L{i+1}', **option(v)) for i,v in enumerate(left)], options=[dict(id=f'R{i+1}', **option(v)) for i,v in enumerate(right)], correctPairs=[dict(leftId=f'L{i+1}',rightId=f'R{v}') for i,v in enumerate(pairs)], explanationRu=er, explanationKz=ek, difficulty='medium', answerEvidence=er, reviewChecks=CHECKS.copy(), reviewMethod='MODEL_SOURCE_AND_REASONING_CHECK; NOT_HUMAN_REVIEW'))


def finish(filename='pilot.json'):
    for s in SUBJECTS:
        for t in s['topics']:
            assert len(t['questions']) == 10, (t['externalKey'], len(t['questions']))
            for language in ('Ru','Kz'):
                assert len(t['theory'+language]) >= 1500, (t['externalKey'],language,len(t['theory'+language]))
            for index,q in enumerate(t['questions'],1):
                q['externalKey'] = t['externalKey'] + f'-q{index:02}'
                q['sourceType'] = 'AI_GENERATED'
                q['sourceUrls'] = t['sourceUrls']
    def typeset(value):
        if isinstance(value,dict):
            return {key: typeset(item) for key,item in value.items()}
        if isinstance(value,list):
            return [typeset(item) for item in value]
        if isinstance(value,str) and not value.startswith('https://'):
            value=re.sub(r'(?<=[0-9])(?=[А-Яа-яЁёӘәҒғҚқҢңӨөҰұҮүҺһІі])', ' ', value)
            value=re.sub(r'(?<=[А-Яа-яЁёӘәҒғҚқҢңӨөҰұҮүҺһІі])(?=[0-9])', ' ', value)
            value=re.sub(r'(?<=[А-Яа-яЁёӘәҒғҚқҢңӨөҰұҮүҺһІі])(?=[A-Za-zΣ])', ' ', value)
            value=re.sub(r'(?<=[A-Za-zΣ])(?=[А-Яа-яЁёӘәҒғҚқҢңӨөҰұҮүҺһІі])', ' ', value)
        return value
    payload=typeset(dict(schema='education-starter-authoring/v1', authoring='Original AI-assisted explanations and exercises; no copied official question bank; no human review claimed', subjects=SUBJECTS))
    (OUT/filename).write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')


if __name__ == '__main__':
    from pilot_math import author as math
    from pilot_physics import author as physics
    from pilot_history import author as history
    import sys
    # Modules import this shared instance instead of executing the builder twice.
    sys.modules['build_pilot'] = sys.modules['__main__']
    math(); physics(); history(); finish()
