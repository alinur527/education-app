"""Real published starter-content journeys; answers are submitted only through UI.

Requires the locally populated release, Playwright and frontend/node_modules/axe-core.
The admin API resolves published fixture IDs and creates/archives a tiny rendering fixture.
Correct answers come exclusively from checked-in authoring JSON, never student APIs.
Run: .venv\\Scripts\\python.exe scripts/content_browser_tests.py
"""
from __future__ import annotations

import json
import argparse
import os
import re
import sys
import subprocess
import uuid
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright

ROOT = Path(__file__).resolve().parents[1]
BASE = os.environ.get("BROWSER_BASE_URL", "http://127.0.0.1:8081").rstrip("/")
OUT = ROOT / "test-results/expansion/content-browser"
SHOTS = ROOT / "docs/screenshots/expansion/content"
assert urlparse(BASE).hostname in ("localhost", "127.0.0.1"), "Local test accounts only"
OUT.mkdir(parents=True, exist_ok=True)
SHOTS.mkdir(parents=True, exist_ok=True)
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def load(relative):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def no_answer_keys(value, path="question"):
    if isinstance(value, dict):
        for key, child in value.items():
            assert key not in {"correctOptionId", "correctOptionIds", "correctPairs",
                               "answerKey", "explanationRu", "explanationKz", "isCorrect"}, f"Answer leaked: {path}.{key}"
            no_answer_keys(child, path + "." + key)
    elif isinstance(value, list):
        for child in value:
            no_answer_keys(child, path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--render-only", action="store_true", help="Only synthetic formula/table/code rendering, with a new UI-registered student")
    args = parser.parse_args()
    report_path = OUT / ("rendering-report.json" if args.render_only else "report.json")
    report = {"startedAt": datetime.now(timezone.utc).isoformat(), "base": BASE,
              "status": "RUNNING", "flows": [], "inspections": [], "errors": [],
              "answerSource": "checked-in authoring fixtures", "studentAnswerTransport": "browser UI only"}
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    errors = report["errors"]
    question_responses, result_responses, statistics, activity = {}, {}, {}, {}
    observed = Counter()
    subjects = []
    preferred = {"Математика": "pilot-math-right-triangle", "Физика": "pilot-physics-newton"}
    for filename in ("pilot", "core", "languages"):
        for subject in load(f"content/starter/{filename}.json")["subjects"]:
            selected = next((t for t in subject["topics"] if t["externalKey"] == preferred.get(subject["subjectRu"])), subject["topics"][0])
            subjects.append((subject, selected))
    assert len(subjects) == 18
    plan = load("content/generated/release-plan.json")
    subject_keys = {row["payload"]["titleRu"]: row["externalKey"] for batch in plan["batches"] for row in batch["rows"] if row["kind"] == "SUBJECT"}
    axe_source = (ROOT / "frontend/node_modules/axe-core/axe.min.js").read_text(encoding="utf-8")

    with sync_playwright() as pw:
        # Isolated admin request context: no privileged token is ever inserted in the student browser.
        api = pw.request.new_context(base_url=BASE)
        fixture = {"email":"content-operator-"+uuid.uuid4().hex+"@example.org","password":"Fixture-"+uuid.uuid4().hex}
        registered=api.post("/api/auth/register",data={**fixture,"firstName":"Content","lastName":"Test operator","language":"ru"})
        assert registered.ok
        user_id=uuid.UUID(registered.json()["user"]["id"])
        sql=f"UPDATE users SET role='ADMIN' WHERE id='{user_id}'::uuid"
        subprocess.run(['docker','compose','exec','-T','postgres','sh','-c','psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"','sh',sql],check=True,capture_output=True)
        login = api.post("/api/auth/login", data={"email": fixture["email"], "password": fixture["password"]})
        assert login.ok, f"Fixture login HTTP {login.status}"
        headers = {"Authorization": "Bearer " + login.json()["token"]}
        mappings = {}
        for index in range(100):
            response = api.get(f"/api/cms/content-packs/mappings?namespace={plan['namespace']}&page={index}", headers=headers)
            assert response.ok, f"Fixture mappings HTTP {response.status}"
            rows = response.json()
            mappings.update({r["externalKey"]: r["contentId"] for r in rows})
            if len(rows) < 100:
                break
        assert all("starter:" + t["externalKey"] in mappings for _, t in subjects), "Publish the release first"
        synthetic_ids = []

        def cms(method, path, data=None):
            response = api.fetch("/api/cms/content" + path, method=method, headers=headers, data=data)
            assert response.ok, f"Synthetic CMS fixture HTTP {response.status}: {response.text()}"
            return response.json()

        def archive_synthetic():
            for content_id in reversed(synthetic_ids):
                item = cms("GET", "/" + content_id)
                if item["status"] != "ARCHIVED":
                    item = cms("POST", "/" + content_id + "/transition", {"version": item["version"], "status": "ARCHIVED"})
                assert item["status"] == "ARCHIVED"
            if synthetic_ids:
                report["syntheticFixture"] = {"ids": synthetic_ids.copy(), "archived": True, "countsAsStarterContent": False}
        browser = pw.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 1000}, reduced_motion="reduce")
        page = context.new_page()
        page.set_default_timeout(20000)
        page.on("pageerror", lambda error: errors.append("pageerror: " + str(error)))
        page.on("console", lambda msg: errors.append("console: " + re.sub(r"data:[^\s']+", "data:[inline payload omitted]", msg.text)) if msg.type == "error" else None)
        page.on("requestfailed", lambda req: errors.append(f"requestfailed: {req.url}: {req.failure}") if req.failure != "net::ERR_ABORTED" else None)
        page.on("dialog", lambda dialog: dialog.accept())

        def response_received(response):
            if response.status >= 400:
                errors.append(f"HTTP {response.status}: {response.url}")
            path = urlparse(response.url).path
            if response.status != 200 or not path.startswith("/api/"):
                return
            try:
                if re.fullmatch(r"/api/tests/[^/]+/questions/\d+", path):
                    value = response.json()
                    no_answer_keys(value)
                    question_responses[(value["sessionId"], value["index"])] = value
                elif re.fullmatch(r"/api/tests/[^/]+/results", path):
                    result_responses[path.split("/")[3]] = response.json()
                elif path == "/api/statistics/me":
                    statistics.clear()
                    statistics.update(response.json())
                elif path == "/api/learning/dashboard":
                    activity.clear()
                    activity.update(response.json())
            except Exception as exc:
                errors.append(f"Response validation {path}: {exc}")

        page.on("response", response_received)

        def clean():
            assert not errors, "Browser errors: " + json.dumps(errors, ensure_ascii=False)

        def inspect(name, screenshot=True):
            page.wait_for_load_state("networkidle")
            if not page.evaluate("document.documentElement.scrollWidth <= innerWidth"):
                report["overflow"] = page.evaluate("""()=>({viewport:innerWidth,document:document.documentElement.scrollWidth,
                  elements:Array.from(document.querySelectorAll('main *')).map(el=>({tag:el.tagName,classes:el.className,
                    width:el.getBoundingClientRect().width,right:el.getBoundingClientRect().right,minWidth:getComputedStyle(el).minWidth}))
                    .filter(el=>el.right>innerWidth+1).slice(0,30)})""")
                raise AssertionError(f"Horizontal overflow: {name}")
            page.evaluate(axe_source)
            violations = page.evaluate("async()=> (await axe.run(document,{runOnly:['wcag2a','wcag2aa','wcag21aa']})).violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.map(n=>n.target)}))")
            assert not violations, f"Accessibility {name}: {violations}"
            if screenshot:
                page.screenshot(path=str(SHOTS / (name + ".png")), full_page=False)
            clean()
            report["inspections"].append({"name": name, "width": page.viewport_size["width"], "axeViolations": 0})
            print("PASS inspection " + name, flush=True)

        def language(lang):
            page.get_by_role("button", name="ҚАЗ" if lang == "kz" else "РУС", exact=True).first.click()

        def catalog():
            page.goto(BASE + "/subjects")
            expect(page.locator(".subject-card").first).to_be_visible()

        def rich_fixture():
            title = "Проверка отображения " + run_id
            title_kz = "Көрсетілімді тексеру " + run_id

            def create(kind, payload, parent=None):
                value = cms("POST", "", {"kind": kind, "parentId": parent, "payload": payload})
                synthetic_ids.append(value["id"])
                (OUT / "synthetic-fixture-ids.json").write_text(json.dumps(synthetic_ids), encoding="utf-8")
                for status in ("REVIEW", "PUBLISHED"):
                    value = cms("POST", "/" + value["id"] + "/transition", {"version": value["version"], "status": status})
                return value["id"]

            subject_id = create("SUBJECT", {"titleRu": title, "titleKz": title_kz, "durationMinutes": 10})
            topic_id = create("TOPIC", {"titleRu": title, "titleKz": title_kz}, subject_id)
            ru = r"""## Технический образец: формула, единицы, таблица

Этот временный образец проверяет отображение и не входит в учебный банк. Разность квадратов: $x^2-y^2=(x-y)(x+y)$. При $x=5$, $y=3$ обе части равны $16$.

$$S=ab=3\,\mathrm{cm}\cdot4\,\mathrm{cm}=12\,\mathrm{cm}^2$$

| Величина | Обозначение | Значение | Единица измерения |
|---|---|---|---|
| Длина первой стороны прямоугольника | $a$ | 3 | см |
| Длина второй стороны прямоугольника | $b$ | 4 | см |
| Площадь прямоугольника | $S=ab$ | 12 | $\mathrm{cm}^2$ |

Код ниже выводит число 12; единицы площади объясняются отдельно от вычисления."""
            kz = r"""## Техникалық үлгі: формула, өлшем бірлігі, кесте

Бұл уақытша үлгі көрсетілімді тексереді және оқу банкіне кірмейді. Квадраттар айырмасы: $x^2-y^2=(x-y)(x+y)$. $x=5$, $y=3$ болса, екі жақ та $16$-ға тең.

$$S=ab=3\,\mathrm{cm}\cdot4\,\mathrm{cm}=12\,\mathrm{cm}^2$$

| Шама | Белгіленуі | Мәні | Өлшем бірлігі |
|---|---|---|---|
| Тіктөртбұрыштың бірінші қабырғасының ұзындығы | $a$ | 3 | см |
| Тіктөртбұрыштың екінші қабырғасының ұзындығы | $b$ | 4 | см |
| Тіктөртбұрыштың ауданы | $S=ab$ | 12 | $\mathrm{cm}^2$ |

Төмендегі код 12 санын шығарады; ауданның өлшем бірлігі есептеуден бөлек түсіндіріледі."""
            create("THEORY", {"titleRu": title, "titleKz": title_kz, "contentRu": ru, "contentKz": kz,
                              "blocks": [{"type": "CODE", "textRu": "print(3 * 4)", "textKz": "print(3 * 4)"}]}, topic_id)
            try:
                catalog()
                page.locator(f'.subject-card[href="/subjects/{subject_id}"]').click()
                page.locator(f'.topic-row[href="/topics/{topic_id}"]').click()
                for lang in ("ru", "kz"):
                    language(lang)
                    expect(page.locator(".theory-content .math-display .katex")).to_be_visible()
                    expect(page.locator(".theory-content .math-inline .katex").first).to_be_visible()
                    expect(page.locator(".theory-content table tbody tr")).to_have_count(3)
                    expect(page.locator(".theory-content pre code")).to_have_text("print(3 * 4)")
                    assert page.locator(".theory-content .katex-error").count() == 0
                    for width in (320, 390):
                        page.set_viewport_size({"width": width, "height": 1000})
                        inspect(f"rich-rendering-{lang}-{width}")
                        table_region = page.get_by_role("region", name="Таблица / Кесте", exact=True)
                        table_region.scroll_into_view_if_needed()
                        assert table_region.evaluate("el=>el.scrollWidth>el.clientWidth"), "Four-column mobile table must retain readable columns with local scrolling"
                        table_region.focus()
                        page.keyboard.press("ArrowRight")
                        page.wait_for_function("()=>document.querySelector('.theory-content .table-scroll').scrollLeft>0")
                        page.screenshot(path=str(SHOTS / f"rich-table-{lang}-{width}.png"), full_page=False)
                report["richRendering"] = {"inlineKatex": True, "displayKatex": True, "tableRows": 3, "codeBlock": True,
                                           "languages": ["ru", "kz"], "widths": [320, 390], "keyboardTableScroll": True, "countsAsStarterContent": False}
            finally:
                archive_synthetic()
            language("ru")
            page.set_viewport_size({"width": 1440, "height": 1000})
            catalog()
            expect(page.locator(f'.subject-card[href="/subjects/{subject_id}"]')).to_have_count(0)

        try:
            run_id = uuid.uuid4().hex[:12]
            account = {"email": f"content-browser-{run_id}@example.org", "password": "Browser-" + uuid.uuid4().hex}
            page.goto(BASE + "/login")
            page.get_by_role("link", name="Создать аккаунт", exact=True).click()
            expect(page.get_by_label("Имя", exact=True)).to_be_visible()
            page.get_by_label("Имя", exact=True).fill("Проверка")
            page.get_by_label("Фамилия", exact=True).fill("Контента")
            page.get_by_label("Электронная почта", exact=True).fill(account["email"])
            page.get_by_label("Пароль", exact=True).fill(account["password"])
            page.get_by_role("button", name="Создать аккаунт", exact=True).click()
            expect(page.get_by_role("heading", name="Сегодня — ещё одна понятная тема.")).to_be_visible()
            page.get_by_role("button", name="Выйти", exact=True).first.click()
            expect(page.get_by_label("Пароль", exact=True)).to_be_visible()
            page.get_by_label("Электронная почта", exact=True).fill(account["email"])
            page.get_by_label("Пароль", exact=True).fill(account["password"])
            page.get_by_role("button", name="Войти", exact=True).click()
            expect(page.get_by_role("heading", name="Сегодня — ещё одна понятная тема.")).to_be_visible()
            inspect("registration-login")
            for subject, topic in ([] if args.render_only else subjects):
                sid = mappings[subject_keys[subject["subjectRu"]]]
                tid = mappings["starter:" + topic["externalKey"]]
                catalog()
                page.locator(f'.subject-card[href="/subjects/{sid}"]').click()
                expect(page.get_by_role("button", name="Уроки", exact=True)).to_be_visible()
                page.locator(f'.topic-row[href="/topics/{tid}"]').click()
                expect(page.get_by_role("heading", name=topic["titleRu"], exact=True).first).to_be_visible()
                expect(page.locator(".theory-content")).to_be_visible()
                assert len(page.locator(".theory-content").inner_text()) >= 1500, f"Short rendered theory {topic['externalKey']}"
                page.get_by_role("button", name="Отметить прочитанным", exact=True).click()
                expect(page.get_by_role("button", name="Теория отмечена прочитанной", exact=True)).to_be_visible()
                lang = "ru"
                if subject["subjectRu"] == "Математика":
                    # Both languages and all required widths on a real long mathematical lesson.
                    for lang in ("ru", "kz"):
                        language(lang)
                        for width in (320, 390, 768, 1024, 1440):
                            page.set_viewport_size({"width": width, "height": 1000})
                            inspect(f"math-theory-{lang}-{width}")
                    language("ru")
                    lang = "ru"
                elif subject["subjectRu"] == "Грамотность чтения":
                    language("kz")
                    lang = "kz"
                page.get_by_role("button", name="Тестті бастау" if lang == "kz" else "Начать тест", exact=True).click()
                page.wait_for_url("**/tests/**")
                session = page.url.rsplit("/", 1)[-1]
                qfixtures = {mappings["starter:" + topic["externalKey"] + ":q:" + q["externalKey"]]: q for q in topic["questions"]}
                seen = set()
                for index in range(10):
                    expect(page.locator(".question-card legend")).to_be_visible()
                    page.wait_for_function("i=>document.querySelector('.test-progress')?.textContent.includes(`${i+1} `)", arg=index)
                    page.wait_for_load_state("networkidle")
                    live = question_responses[(session, index)]
                    fixture_q = qfixtures[live["questionId"]]
                    assert live["questionId"] not in seen
                    seen.add(live["questionId"])
                    kind = fixture_q["questionType"]
                    observed[kind] += 1
                    if fixture_q.get("contextExternalKey"):
                        assert live.get("context"), "Shared passage missing"
                        expect(page.locator(".passage")).to_be_visible()
                        observed["CONTEXT_LINKED"] += 1
                    options = fixture_q["options"]
                    if kind == "SINGLE_CHOICE":
                        index_option = next(i for i, o in enumerate(options) if o["id"] == fixture_q["correctOptionId"])
                        page.get_by_role("radio").nth(index_option).check()
                    elif kind == "MULTIPLE_SELECT":
                        for i, option in enumerate(options):
                            if option["id"] in fixture_q["correctOptionIds"]:
                                page.get_by_role("checkbox").nth(i).check()
                        inspect("multiple-select")
                    else:
                        for i, left in enumerate(fixture_q["leftOptions"]):
                            right = next(p["rightId"] for p in fixture_q["correctPairs"] if p["leftId"] == left["id"])
                            page.get_by_role("combobox").nth(i).select_option(right)
                        inspect("matching")
                    if index == 0 and fixture_q.get("contextExternalKey"):
                        inspect("shared-reading-context-kz")
                    button = page.locator(".answer-submit")
                    expect(button).to_be_enabled()
                    with page.expect_response(lambda r: urlparse(r.url).path == f"/api/tests/{session}/answers" and r.request.method == "POST") as answer_response:
                        button.click()
                    assert answer_response.value.status == 200, f"Answer save HTTP {answer_response.value.status}"
                    if index < 9:
                        expect(page.locator(".test-progress")).to_contain_text(f"{index+2} ")
                page.wait_for_url("**/results/**")
                expect(page.locator(".result-score strong")).to_have_text("100%")
                expect(page.locator(".review-item")).to_have_count(10)
                result = result_responses[session]
                assert result["correctAnswers"] == result["totalQuestions"] == 10
                assert result["score"] == 100
                assert all(a.get("explanationRu") and a.get("explanationKz") for a in result["answers"]), "Explanation missing after finish"
                clean()
                if lang == "kz":
                    language("ru")
                report["flows"].append({"subject": subject["subjectRu"], "topic": topic["externalKey"], "sessionId": session,
                                        "questions": 10, "correct": 10, "score": 100, "language": lang})
                report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
                print("PASS student flow " + subject["subjectRu"], flush=True)
            if not args.render_only:
                page.get_by_role("link", name="Моя статистика", exact=True).click()
                expect(page.get_by_role("heading", name="Статистика", exact=True)).to_be_visible()
                page.wait_for_load_state("networkidle")
                assert statistics["testsTaken"] == 18 and statistics["averageScore"] == 100, statistics
                assert len(statistics["subjects"]) == 18
                assert sum(s["testsTaken"] for s in statistics["subjects"]) == 18
                inspect("statistics-18-real-attempts")
                assert observed["MATCHING"] > 0 and observed["MULTIPLE_SELECT"] > 0 and observed["CONTEXT_LINKED"] == 10
                assert len(question_responses) == 180
                report.update({"questionResponsesWithoutAnswerKeys": len(question_responses),
                           "observedTypes": dict(observed), "testsTaken": statistics["testsTaken"], "correctAnswers": 180,
                           "averageScore": statistics["averageScore"], "humanApprovalClaimed": False})
            rich_fixture()
            clean()
            report["status"] = "PASS"
        except Exception as exc:
            report.update({"status": "FAIL", "failure": str(exc), "lastUrl": page.url})
            page.screenshot(path=str(OUT / "failure.png"), full_page=True)
            (OUT / "failure.html").write_text(page.content(), encoding="utf-8")
            raise
        finally:
            cleanup_error = None
            try:
                archive_synthetic()
            except Exception as exc:
                cleanup_error = exc
                report.update({"status": "FAIL", "cleanupFailure": str(exc)})
            finally:
                api.dispose()
                report["finishedAt"] = datetime.now(timezone.utc).isoformat()
                report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
                browser.close()
            if cleanup_error:
                raise cleanup_error
        print(json.dumps({k: report[k] for k in ("status", "questionResponsesWithoutAnswerKeys", "observedTypes", "testsTaken", "correctAnswers", "averageScore", "richRendering") if k in report}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Also record failures before browser/fixture setup, so a prior PASS cannot look current.
        failure_path = OUT / ("rendering-report.json" if "--render-only" in sys.argv else "report.json")
        if failure_path.exists():
            failure = json.loads(failure_path.read_text(encoding="utf-8"))
            if failure.get("status") == "RUNNING":
                failure.update({"status": "FAIL", "failure": str(error), "finishedAt": datetime.now(timezone.utc).isoformat()})
                failure_path.write_text(json.dumps(failure, ensure_ascii=False, indent=2), encoding="utf-8")
        raise
