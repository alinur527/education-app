"""Bounded, public-only NTC research downloader. Never imports into the app.

Run with a Python environment containing pdfplumber and openpyxl. Source bytes
stay in ignored test-results; the reproducible manifest is committed separately.
"""
from __future__ import annotations

import argparse
import hashlib
import http.client
import ipaddress
import json
import socket
import ssl
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote, urljoin, urlsplit, urlunsplit

import pdfplumber

ROOT = Path(__file__).resolve().parents[1]
INPUT = ROOT / 'content/inputs'
OUTPUT = ROOT / 'test-results/source-research'
ARTIFACTS = ROOT / 'content/research'
MAX_BYTES = 20 * 1024 * 1024
ALLOWED = {'testcenter.kz', 'www.testcenter.kz', 'new.testcenter.kz'}


def public_target(url: str) -> tuple[str, str]:
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or parsed.hostname not in ALLOWED or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError('Only HTTPS on approved NTC hosts is allowed')
    addresses = sorted({entry[4][0] for entry in socket.getaddrinfo(parsed.hostname, 443, type=socket.SOCK_STREAM)})
    if not addresses or any(not ipaddress.ip_address(ip).is_global for ip in addresses):
        raise ValueError('Non-public destination rejected')
    return parsed.hostname, addresses[0]


class PinnedHTTPS(http.client.HTTPSConnection):
    def __init__(self, hostname: str, ip: str):
        super().__init__(hostname, timeout=30, context=ssl.create_default_context())
        self.checked_ip = ip

    def connect(self):
        raw = socket.create_connection((self.checked_ip, 443), self.timeout)
        self.sock = self._context.wrap_socket(raw, server_hostname=self.host)


def fetch(url: str) -> tuple[bytes, str, str]:
    current = url
    for _ in range(6):
        host, ip = public_target(current)
        parsed = urlsplit(current)
        path = quote(parsed.path, safe='/%:@') + ('?' + quote(parsed.query, safe='=&%') if parsed.query else '')
        connection = PinnedHTTPS(host, ip)
        try:
            connection.request('GET', path, headers={'Host': host, 'User-Agent': 'EducationApp-source-research/1.0', 'Accept-Encoding': 'identity'})
            response = connection.getresponse()
            if response.status in (301, 302, 303, 307, 308):
                current = urljoin(current, response.getheader('Location', ''))
                continue
            if response.status != 200:
                raise ValueError(f'HTTP_{response.status}')
            size = response.getheader('Content-Length')
            if size and int(size) > MAX_BYTES:
                raise ValueError('Download size limit exceeded')
            content = response.read(MAX_BYTES + 1)
            if len(content) > MAX_BYTES:
                raise ValueError('Download size limit exceeded')
            return content, current, response.getheader('Content-Type', '').split(';')[0].lower()
        finally:
            connection.close()
    raise ValueError('Redirect limit exceeded')


def download(record: dict, extension: str = 'pdf') -> dict:
    item = {
        'sourceId': record['id'], 'url': record['url'], 'subject': record.get('subject'),
        'language': record.get('language'), 'variant': record.get('variant'),
        'sourceKind': record.get('kind', 'OFFICIAL_WEB_PAGE'),
        'catalogueAppliesFrom': record.get('appliesFrom'), 'appliesFrom': None,
        'retrievedAt': datetime.now(timezone.utc).isoformat(),
        'reuseMode': 'EXTERNAL_LINK_UNLESS_REUSE_CONFIRMED',
        'reviewStatus': 'SOURCE_RESEARCH_NOT_CONTENT_APPROVAL',
    }
    target = OUTPUT / f"{record['id']}.{extension}"
    cached = OUTPUT / f"{record['id']}.manifest.json"
    if target.exists() and cached.exists():
        prior = json.loads(cached.read_text(encoding='utf-8'))
        if hashlib.sha256(target.read_bytes()).hexdigest() == prior.get('sha256') and prior['url'] == record['url']:
            return prior
    for attempt in range(3):
        try:
            content, resolved, content_type = fetch(record['url'])
            if extension == 'pdf' and (content_type != 'application/pdf' or not content.startswith(b'%PDF-')):
                raise ValueError('Expected real PDF bytes and application/pdf')
            if extension == 'html' and content_type != 'text/html':
                raise ValueError('Expected text/html')
            target.write_bytes(content)
            item.update(resolvedUrl=resolved, contentType=content_type, byteSize=len(content), sha256=hashlib.sha256(content).hexdigest(), localPath=target.relative_to(ROOT).as_posix(), accessStatus='DOWNLOADED')
            if extension == 'pdf':
                with pdfplumber.open(target) as pdf:
                    pages = [{'page': index + 1, 'text': page.extract_text() or '', 'tables': page.extract_tables()} for index, page in enumerate(pdf.pages)]
                (OUTPUT / f"{record['id']}.extracted.json").write_text(json.dumps(pages, ensure_ascii=False, indent=2), encoding='utf-8', newline='\n')
                item['pageCount'] = len(pages)
                first = pages[0]['text']
                if ('2026' in first and ('использования' in first or 'қолдану' in first)):
                    item['appliesFrom'] = 2026
                    item['appliesFromEvidence'] = {'page': 1, 'basis': 'Explicit document heading'}
            cached.write_text(json.dumps(item, ensure_ascii=False, indent=2), encoding='utf-8', newline='\n')
            return item
        except Exception as error:
            item.update(accessStatus='DOWNLOAD_FAILED', error=str(error))
            if attempt < 2:
                time.sleep(2 ** attempt)
    return item


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--groups', nargs='+', default=['specifications'])
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    source = json.loads((INPUT / 'ENT_sources_2026-10-04.json').read_text(encoding='utf-8-sig'))
    manifest = []
    for group in args.groups:
        if group not in ('specifications', 'officialSamples', 'officialReferences', 'recommendedBookLists'):
            raise ValueError('Unsupported finite catalogue group')
        for record in source[group]:
            result = download(record)
            manifest.append(result)
            print(record['id'], result['accessStatus'], flush=True)
    for record in [
        {'id': 'NTC-PREP-RU', 'url': 'https://testcenter.kz/?page_id=15094&lang=ru'},
        {'id': 'NTC-PREP-KK', 'url': 'https://testcenter.kz/?page_id=23376'},
        {'id': 'NTC-FORMAT', 'url': 'https://testcenter.kz/?page_id=15074&lang=ru'},
        {'id': 'NTC-SUBJECTS', 'url': 'https://testcenter.kz/?page_id=15562&lang=ru'},
    ]:
        manifest.append(download(record, 'html'))
    manifest.append(download({'id': 'NTC-PROFILE-PAIRS', 'url': 'https://testcenter.kz/wp-content/uploads/2026/05/Список-специальностей-полной-формы-обучения-с-профильными-предметами.pdf', 'kind': 'OFFICIAL_PROGRAMME_SUBJECT_PAIRS'}))
    (ARTIFACTS / 'official-source-manifest.json').write_text(json.dumps({'schema': 'education-app-official-source-manifest-v1', 'sources': manifest}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    main()
