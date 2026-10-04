#!/usr/bin/env python3
"""Reproducible local content release. Dry-run is the default; never edits the database directly."""
from __future__ import annotations

import argparse
import copy
import getpass
import hashlib
import json
import os
from pathlib import Path
import sys
import uuid
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = "education-kz-2026"
VERSION = "2026.10.04-v1"
SUBJECTS = [
    ("math-literacy", "Математическая грамотность", "Математикалық сауаттылық", "MANDATORY"),
    ("reading", "Грамотность чтения", "Оқу сауаттылығы", "MANDATORY"),
    ("history-kz", "История Казахстана", "Қазақстан тарихы", "MANDATORY"),
    ("math", "Математика", "Математика", "PROFILE"),
    ("physics", "Физика", "Физика", "PROFILE"),
    ("chemistry", "Химия", "Химия", "PROFILE"),
    ("biology", "Биология", "Биология", "PROFILE"),
    ("geography", "География", "География", "PROFILE"),
    ("informatics", "Информатика", "Информатика", "PROFILE"),
    ("world-history", "Всемирная история", "Дүниежүзі тарихы", "PROFILE"),
    ("law", "Основы права", "Құқық негіздері", "PROFILE"),
    ("russian", "Русский язык", "Орыс тілі", "PROFILE"),
    ("russian-literature", "Русская литература", "Орыс әдебиеті", "PROFILE"),
    ("kazakh", "Казахский язык", "Қазақ тілі", "PROFILE"),
    ("kazakh-literature", "Казахская литература", "Қазақ әдебиеті", "PROFILE"),
    ("english", "Английский язык", "Ағылшын тілі", "PROFILE"),
    ("german", "Немецкий язык", "Неміс тілі", "PROFILE"),
    ("french", "Французский язык", "Француз тілі", "PROFILE"),
]
BY_NAME = {s[1]: s for s in SUBJECTS}
COUNTERPARTS = [(0,15),(7,8),(9,10),(11,12),(13,14),(16,17),(18,19),(20,21),(22,23),(24,25),(26,27),(28,29),(32,33),(34,35)]

def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))

def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))

def digest(value):
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()

def write(path, value):
    path=Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+"\n", encoding="utf-8", newline="\n")

def row(key, kind, payload, parent=None):
    value={"externalKey":key,"kind":kind,"payload":payload,"sourceVersion":VERSION}
    if parent: value["parentExternalKey"]=parent
    return value

def source_ref(url):
    return "REF-"+hashlib.sha256(url.encode()).hexdigest()[:16]

def build():
    official=read(ROOT/"content/research/official-curriculum.json")
    programmes={p["sourceId"]:p for p in official["programmes"]}
    topic_index={}; paired={}
    for a,b in COUNTERPARTS:
        paired[f"SPEC-{a:02}"]=f"SPEC-{b:02}";paired[f"SPEC-{b:02}"]=f"SPEC-{a:02}"
    for p in programmes.values():
        for section in p["sections"]:
            for topic in section["topics"]:
                key=(p["sourceId"],topic["officialCode"])
                if key in topic_index: raise ValueError(f"Duplicate official code {key}")
                topic_index[key]=(section,topic)
    stages={k:[] for k in ("subjects","syllabus","starter-topics","contexts","theories","questions","courses","modules","lessons","quizzes","assignments")}
    refs={}; coverage=[]; warnings=[]
    for slug,ru,kk,category in SUBJECTS:
        stages["subjects"].append(row("subject:"+slug,"SUBJECT",{"titleRu":ru,"titleKz":kk,"category":category,"sourceType":"IMPORTED","sourceName":"НЦТ / ҰТО: спецификации с 2026 года","examVersion":"NTC_FROM_2026","durationMinutes":30}))
    for p in programmes.values():
        subject_key="subject:"+BY_NAME[p["subjectRu"]][0]
        for section in p["sections"]:
            for t in section["topics"]:
                other=topic_index.get((paired.get(p["sourceId"]),t["officialCode"]))
                ru=t["title"] if p["documentLanguage"]=="ru" else other[1]["title"] if other else "Түпнұсқа (KZ): "+t["title"]
                kk=t["title"] if p["documentLanguage"]=="kk" else other[1]["title"] if other else "Түпнұсқа (RU): "+t["title"]
                if not other and p["subjectRu"] not in {"Казахский язык","Казахская литература","Русский язык","Русская литература"}:warnings.append("Unpaired translation: "+p["sourceId"]+":"+str(t["officialCode"]))
                code=str(t["officialCode"]) if t["officialCode"] is not None else "platform-"+digest(t["title"])[:10]
                key=f"syllabus:{p['sourceId']}:{code}"
                meta={"sourceId":p["sourceId"],"page":",".join(map(str,t["pages"])),"sectionRu":section["title"],"sectionKz":section["title"],"officialCode":str(t["officialCode"] or ""),"variant":p["programmeVariant"],"examVersion":p["examVersion"],"platformCode":key,"extractionStatus":t.get("extractionStatus","UNKNOWN")}
                payload={"titleRu":ru if len(ru)<=300 else ru[:296]+"…","titleKz":kk if len(kk)<=300 else kk[:296]+"…","descriptionRu":ru,"descriptionKz":kk,"curriculum":meta,"curriculumVariant":p["programmeVariant"],"contentLanguage":p["documentLanguage"],"sourceIds":[p["sourceId"]],"sourceType":"IMPORTED","sourceUrl":p["url"],"sourceName":"НЦТ / ҰТО: официальный перечень тем","sortOrder":len(stages["syllabus"])}
                stages["syllabus"].append(row(key,"TOPIC",payload,subject_key))
    for filename in ("pilot.json","languages.json","core.json"):
        path=ROOT/"content/starter"/filename
        if not path.exists():warnings.append("Missing starter source: "+filename);continue
        authored=read(path)
        for subject in authored["subjects"]:
            stats={"subject":subject["subjectRu"],"topics":0,"questions":0,"humanApproved":0}
            for topic in subject["topics"]:
                source_id=topic["officialSourceId"];code=topic["officialCode"]
                if (source_id,code) not in topic_index:raise ValueError(f"Unknown curriculum mapping {source_id}:{code}")
                key="starter:"+topic["externalKey"];p=programmes[source_id];sec,official_topic=topic_index[(source_id,code)]
                urls=topic.get("sourceUrls",[])
                for url in urls:refs[source_ref(url)]={"sourceId":source_ref(url),"metadata":{"url":url,"sourceName":urllib.parse.urlsplit(url).hostname,"sourceKind":"AUTHORING_REFERENCE","accessStatus":"SEE_RESEARCH_EVIDENCE","reuseMode":"EXTERNAL_LINK_UNLESS_REUSE_CONFIRMED","reviewStatus":"AUTOMATED_CHECKS_ONLY"}}
                provenance={"sourceType":"AI_GENERATED","verified":False,"sourceIds":[source_id]+[source_ref(u) for u in urls][:19],"sourceUrl":urls[0] if urls else p["url"],"sourceName":"Авторский учебный материал; автоматические проверки, без одобрения специалиста","contentLanguage":"ru+kk","curriculum":{"sourceId":source_id,"officialCode":str(code),"page":",".join(map(str,official_topic["pages"])),"sectionRu":sec["title"],"sectionKz":sec["title"],"variant":p["programmeVariant"],"examVersion":p["examVersion"],"platformCode":key}}
                stages["starter-topics"].append(row(key,"TOPIC",{**provenance,"titleRu":topic["titleRu"],"titleKz":topic["titleKz"],"sortOrder":stats["topics"]},"subject:"+BY_NAME[subject["subjectRu"]][0]))
                for language in ("Ru","Kz"):
                    if len(topic["theory"+language])<1000:raise ValueError(f"Incomplete theory {key}/{language}")
                stages["theories"].append(row(key+":theory","THEORY",{**provenance,"titleRu":topic["titleRu"],"titleKz":topic["titleKz"],"contentRu":topic["theoryRu"],"contentKz":topic["theoryKz"],"offlineAllowed":True,"sortOrder":0},key))
                for context in topic.get("contexts",[]):
                    stages["contexts"].append(row(key+":context:"+context["externalKey"],"CONTEXT",{**provenance,**{k:v for k,v in context.items() if k in {"titleRu","titleKz","contentRu","contentKz"}}},key))
                if len(topic["questions"])<10:raise ValueError("Fewer than ten questions: "+key)
                question_titles=set()
                for index,q in enumerate(topic["questions"]):
                    if q["titleRu"] in question_titles:raise ValueError("Duplicate question: "+key)
                    question_titles.add(q["titleRu"])
                    allowed={"titleRu","titleKz","questionType","options","leftOptions","correctOptionId","correctOptionIds","correctPairs","explanationRu","explanationKz","difficulty","answerEvidence","reviewChecks","difficultyReason"}
                    payload={**provenance,**{k:v for k,v in q.items() if k in allowed}}
                    # Missing authoring keys use immutable original-prompt fingerprints, independent of row order.
                    qrow=row(key+":q:"+str(q.get("externalKey") or "original-"+digest({"ru":q["titleRu"],"kk":q["titleKz"]})[:16]),"QUESTION",payload,key)
                    if q.get("contextExternalKey"):qrow["contextExternalKey"]=key+":context:"+q["contextExternalKey"]
                    stages["questions"].append(qrow);stats["questions"]+=1
                stats["topics"]+=1
            coverage.append(stats)
    course_file=ROOT/"content/starter/python-course.json"
    if course_file.exists():
        course=read(course_file)["course"];ck="course:"+course["externalKey"]
        common={"sourceType":"AI_GENERATED","verified":False,"sourceUrl":"https://docs.python.org/3/tutorial/","sourceName":"Python tutorial; оригинальный учебный курс, автоматическая проверка примеров"}
        stages["courses"].append(row(ck,"COURSE",{**common,**{k:course[k] for k in ("titleRu","titleKz","descriptionRu","descriptionKz")},"visibility":"PUBLIC","selfEnroll":True,"icon":"⌘"}))
        for module in course["modules"]:
            mk=ck+":"+module["externalKey"];stages["modules"].append(row(mk,"MODULE",{"titleRu":module["titleRu"],"titleKz":module["titleKz"],"sortOrder":module["order"]},ck))
            for lesson in module["lessons"]:
                lk=mk+":"+lesson["externalKey"]
                blocks=[]
                for n,example in enumerate(lesson.get("codeExamples",[]),1):
                    blocks.append({"type":"HEADING","textRu":f"Пример {n}","textKz":f"{n}-мысал"})
                    if example.get("stdin"):blocks.append({"type":"TEXT","textRu":"Ввод: "+example["stdin"],"textKz":"Енгізу: "+example["stdin"]})
                    for name,value in example.get("inputFiles",{}).items():blocks.append({"type":"CODE","textRu":f"{name}\n{value}","textKz":f"{name}\n{value}"})
                    blocks.append({"type":"CODE","textRu":example["code"],"textKz":example["code"]})
                    blocks.append({"type":"TEXT","textRu":"Ожидаемый вывод:","textKz":"Күтілетін нәтиже:"})
                    blocks.append({"type":"CODE","textRu":example["expectedStdout"],"textKz":example["expectedStdout"]})
                    for name,value in example.get("expectedFiles",{}).items():blocks.append({"type":"CODE","textRu":f"{name}\n{value}","textKz":f"{name}\n{value}"})
                stages["lessons"].append(row(lk,"LESSON",{**common,"titleRu":lesson["titleRu"],"titleKz":lesson["titleKz"],"contentRu":lesson["theoryRu"],"contentKz":lesson["theoryKz"],"sortOrder":lesson["order"],"blocks":blocks},mk))
                if lesson.get("quiz"):
                    questions=[{k:v for k,v in q.items() if k in {"titleRu","titleKz","questionType","options","leftOptions","correctOptionId","correctOptionIds","correctPairs","explanationRu","explanationKz"}} for q in lesson["quiz"]["questions"]]
                    stages["quizzes"].append(row(lk+":quiz","QUIZ",{**common,"titleRu":"Проверка: "+lesson["titleRu"],"titleKz":"Тексеру: "+lesson["titleKz"],"questions":questions},lk))
                if lesson.get("assignment"):
                    a=lesson["assignment"];payload={**common,**{k:a[k] for k in ("titleRu","titleKz","maxScore")}}
                    for lang in ("Ru","Kz"):
                        text=a["description"+lang]+"\n\n"+"\n".join("• "+v for v in a.get("acceptanceCriteria"+lang,[]))
                        text+="\n\n"+("Оценивание:" if lang=="Ru" else "Бағалау:")+"\n"+"\n".join(str(v["points"])+" — "+v["criterion"+lang] for v in a.get("rubric",[]))
                        for sample in a.get("sampleRuns",[]):
                            text+="\n\n"+sample.get("inputDescription"+lang,("Ввод:" if lang=="Ru" else "Енгізу:")+"\n"+sample.get("stdin",""))
                            if "expectedStdout" in sample:text+="\n```\n"+sample["expectedStdout"]+"```"
                            for field in ("inputFiles","expectedFiles"):
                                for name,value in sample.get(field,{}).items():text+="\n"+name+"\n```\n"+value+"```"
                        payload["description"+lang]=text
                    stages["assignments"].append(row(lk+":assignment","ASSIGNMENT",payload,lk))
    else:warnings.append("Missing Python course")
    batches=[];seen=set()
    for stage,rows in stages.items():
        for start in range(0,len(rows),60):
            items=rows[start:start+60]
            for item in items:
                if len(item["externalKey"])>160 or item["externalKey"] in seen:raise ValueError("Invalid/duplicate key "+item["externalKey"])
                seen.add(item["externalKey"])
            batches.append({"schema":"education-content-pack/v1","namespace":NAMESPACE,"packVersion":VERSION,"batchKey":f"{stage}-{start//60:03}","rows":items})
    manifest=read(ROOT/"content/research/official-source-manifest.json")
    allowed={"url","resolvedUrl","retrievedAt","contentType","byteSize","sha256","subject","variant","sourceKind","appliesFrom","accessStatus","reuseMode","reviewStatus","sourceName","licenseUrl","attribution"}
    sources=[{"sourceId":s["sourceId"],"metadata":{k:v for k,v in s.items() if k in allowed}} for s in manifest["sources"]]+list(refs.values())
    return {"schema":"education-release-plan/v1","namespace":NAMESPACE,"version":VERSION,"inputSha256":{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/"content/inputs").glob('*')) if p.is_file()},"sources":sources,"batches":batches,"coverage":coverage,"officialTopics":len(stages["syllabus"]),"warnings":warnings}

class Api:
    def __init__(self,base,email):
        parsed=urllib.parse.urlsplit(base)
        if parsed.scheme not in {"http","https"} or not parsed.hostname or parsed.username:raise ValueError("Invalid API URL")
        if parsed.hostname not in {"localhost","127.0.0.1","::1"}:raise ValueError("This operator CLI is restricted to local development; production needs a separately reviewed release")
        self.base=base.rstrip('/')+"/api";self.token=None
        self.token=self.call('POST','/auth/login',{"email":email or os.getenv('EDU_EMAIL') or input('Email: '),"password":os.getenv('EDU_PASSWORD') or getpass.getpass('Password: ')})['token']
    def call(self,method,path,body=None):
        headers={'Content-Type':'application/json'}
        if self.token:headers['Authorization']='Bearer '+self.token
        req=urllib.request.Request(self.base+path,data=None if body is None else canonical(body).encode(),headers=headers,method=method)
        try:
            with urllib.request.urlopen(req,timeout=60) as response:return json.load(response)
        except urllib.error.HTTPError as e:
            error=e.read(2048).decode('utf-8','replace');raise RuntimeError(f"{method} {path}: HTTP {e.code} {error}") from None
    def multipart(self,path,fields,file_path,mime):
        boundary='edu-'+uuid.uuid4().hex;parts=[]
        for key,value in fields.items():parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n').encode('utf-8'))
        if any(c in file_path.name for c in '\r\n"'):raise ValueError('Unsafe upload filename')
        parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file_path.name}"\r\nContent-Type: {mime}\r\n\r\n').encode())
        parts.append(file_path.read_bytes());parts.append(f'\r\n--{boundary}--\r\n'.encode())
        req=urllib.request.Request(self.base+path,data=b''.join(parts),headers={'Content-Type':'multipart/form-data; boundary='+boundary,'Authorization':'Bearer '+self.token},method='POST')
        try:
            with urllib.request.urlopen(req,timeout=90) as response:return json.load(response)
        except urllib.error.HTTPError as e:raise RuntimeError(f'Upload HTTP {e.code}: '+e.read(2048).decode('utf-8','replace')) from None
    def mappings(self):
        result={};page=0
        while True:
            rows=self.call('GET',f'/cms/content-packs/mappings?namespace={NAMESPACE}&page={page}')
            result.update({r['externalKey']:r['contentId'] for r in rows})
            if len(rows)<100:return result
            page+=1

def run_apply(plan,args):
    api=Api(args.base_url,args.email);mapping=api.mappings();state={"namespace":NAMESPACE,"version":VERSION,"planChecksum":digest(plan),"batches":[],"published":[],"warnings":plan['warnings']}
    # Existing subjects are adopted by exact title only, with identical payload. No UUID changes.
    if not all('subject:'+s[0] in mapping for s in SUBJECTS):
        existing=api.call('GET','/cms/content?kind=SUBJECT&size=100')['items'];adopt=[]
        for slug,ru,_,_ in SUBJECTS:
            key='subject:'+slug
            if key in mapping:continue
            matches=[s for s in existing if s['titleRu']==ru]
            if len(matches)>1:raise ValueError('Ambiguous existing subject: '+ru)
            if len(matches)==1:
                c=api.call('GET','/cms/content/'+matches[0]['id']);r=row(key,'SUBJECT',c['payload']);r['existingId']=c['id'];adopt.append(r)
        if adopt:
            request={"schema":"education-content-pack/v1","namespace":NAMESPACE,"packVersion":VERSION,"batchKey":"adopt-baseline","rows":adopt}
            preview=api.call('POST','/cms/content-packs',request)
            if preview['status']=='INVALID':raise ValueError('Adoption validation failed: '+canonical(preview['preview']))
            api.call('POST',f"/cms/content-packs/{preview['id']}/confirm");mapping=api.mappings()
    for start in range(0,len(plan['sources']),75):api.call('POST','/cms/sources',plan['sources'][start:start+75])
    for original in plan['batches']:
        batch=copy.deepcopy(original)
        for r in batch['rows']:
            context=r.pop('contextExternalKey',None)
            if context:
                if context not in mapping:raise ValueError('Context must precede question: '+context)
                r['payload']['contextId']=mapping[context]
        preview=api.call('POST','/cms/content-packs',batch)
        if preview['status']=='INVALID':raise ValueError('Validation failed: '+canonical(preview['preview']))
        result=api.call('POST',f"/cms/content-packs/{preview['id']}/confirm")
        state['batches'].append({'batchKey':batch['batchKey'],'id':result['id'],'replayed':preview['status']=='APPLIED','result':result['result']})
        for item in result['result']['items']:mapping[item['externalKey']]=item['contentId']
        write(args.state,state)
        print(batch['batchKey'],{'replayed':len(result['result']['items']),'created':0,'updated':0} if preview['status']=='APPLIED' else {k:v for k,v in result['result'].items() if k!='items'})
        if args.publish:
            items=[]
            for item in result['result']['items']:
                if item['action']=='CONFLICT':continue
                c=api.call('GET','/cms/content/'+item['contentId'])
                # Never publish an unchanged mapped record with an unrelated manual draft.
                incoming=next(r['payload'] for r in batch['rows'] if r['externalKey']==item['externalKey'])
                if digest(c['payload'])!=digest(incoming):continue
                if c['status']!='PUBLISHED':items.append({'id':c['id'],'version':c['version']})
            if items:
                body={'items':items,'target':'PUBLISHED'};p=api.call('POST','/cms/content-batches/preview',body)
                if not p['valid']:raise ValueError('Publication validation failed: '+canonical(p['rows']))
                api.call('POST','/cms/content-batches/confirm',{**body,'confirmation':p['confirmation']});state['published'].extend(i['id'] for i in items);write(args.state,state)
    if args.materials:
        manifest=read(ROOT/'content/research/python-material.json');material=manifest['material']
        path=(ROOT/material['localIgnoredPath']).resolve();root=(ROOT/'test-results/source-research').resolve()
        if not path.is_relative_to(root):raise ValueError('Source file must be inside ignored source-research directory')
        if not path.exists():raise ValueError('Download the pinned permitted Python PDF using its research manifest before --materials')
        if hashlib.sha256(path.read_bytes()).hexdigest()!=material['sha256']:raise ValueError('Material SHA mismatch')
        parent_key='course:'+material['courseExternalKey'];parent=mapping[parent_key]
        uploaded=api.multipart('/cms/material-packs',{'namespace':NAMESPACE,'externalKey':material['externalKey'],'contentId':parent,'titleRu':material['titleRu'],'titleKz':material['titleKz']},path,material['mimeType'])
        state['materials']=[uploaded];write(args.state,state)
        if uploaded['action']=='CREATED' and args.publish:
            c=api.call('GET','/cms/content/'+parent)
            expected=next(r['payload'] for b in plan['batches'] for r in b['rows'] if r['externalKey']==parent_key)
            if digest(c['payload'])==digest(expected):
                c=api.call('PUT','/cms/content/'+parent,{'kind':c['kind'],'parentId':c['parentId'],'payload':c['payload'],'version':c['version']})
                body={'items':[{'id':c['id'],'version':c['version']}],'target':'PUBLISHED'}
                preview=api.call('POST','/cms/content-batches/preview',body)
                api.call('POST','/cms/content-batches/confirm',{**body,'confirmation':preview['confirmation']})
            else:state['warnings'].append('Material uploaded; publication deferred to preserve teacher draft')
        registry={'sourceId':'PYTHON-TUTORIAL-3.12.0','metadata':{'url':manifest['download']['archiveUrl'],'sourceName':'Python Tutorial 3.12.0 (EN archive)','sourceKind':'LICENSED_PDF','sha256':material['sha256'],'byteSize':material['size'],'contentType':material['mimeType'],'reuseMode':'REDISTRIBUTION_ALLOWED_WITH_NOTICES_RETAINED','reviewStatus':'SOURCE_RESEARCH_NOT_CONTENT_APPROVAL','licenseUrl':manifest['rights']['versionLicenseUrl'],'attribution':manifest['rights']['attribution'],'materialId':uploaded['id'],'accessStatus':'UPLOADED'}}
        api.call('POST','/cms/sources',[registry])
    state['mappingCount']=len(mapping);write(args.state,state)
    print('Complete. Server mappings:',len(mapping),'Report:',args.state)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['dry-run','build','apply','resume','report'],nargs='?',default='dry-run')
    parser.add_argument('--base-url',default='http://127.0.0.1:8081');parser.add_argument('--email')
    parser.add_argument('--materials',action='store_true',help='Upload the pinned permitted PDF, verified against its manifest')
    parser.add_argument('--publish',action='store_true',help='Explicitly publish exact imported payloads through preview + workflow')
    parser.add_argument('--output',type=Path,default=ROOT/'content/generated/release-plan.json')
    parser.add_argument('--state',type=Path,default=ROOT/'test-results/content-release-state.json')
    args=parser.parse_args()
    if args.command=='report':print(json.dumps(read(args.state),ensure_ascii=False,indent=2));return
    plan=build();write(args.output,plan)
    print(json.dumps({'officialTopics':plan['officialTopics'],'rows':sum(len(b['rows']) for b in plan['batches']),'batches':len(plan['batches']),'coverage':plan['coverage'],'warnings':plan['warnings'],'planChecksum':digest(plan)},ensure_ascii=False,indent=2))
    if args.command in {'apply','resume'}:run_apply(plan,args)
    else:print('Dry-run only: generated and validated locally; no network or database mutations. Apply always performs server preview before confirmation.')

if __name__=='__main__':
    try:main()
    except (ValueError,RuntimeError,KeyError) as error:print(str(error),file=sys.stderr);sys.exit(1)
