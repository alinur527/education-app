"""Generate the eight-subject original authoring pack (offline)."""
import build_pilot as b
from core_literacy import author as literacy
from core_science import author as science
from core_society import author as society

if __name__=='__main__':
    literacy(); science(); society()
    for subject in b.SUBJECTS:
        for topic in subject['topics']:
            context=topic.pop('questionContextExternalKey',None)
            if context:
                for question in topic['questions']:
                    question['contextExternalKey']=context
    b.finish('core.json')
