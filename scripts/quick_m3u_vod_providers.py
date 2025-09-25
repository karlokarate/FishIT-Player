#!/usr/bin/env python3
import re
import sys
from collections import Counter

extinf_re = re.compile(r'^#EXTINF:-1\s+(?P<attrs>[^,]*),(?P<title>.*)$')
attr_re = re.compile(r'(\w[\w-]*)="([^"]*)"')

def parse_m3u(path):
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        pending = None
        for line in f:
            line = line.rstrip('\n')
            if not line:
                continue
            if line.startswith('#EXTINF'):
                m = extinf_re.match(line)
                if not m:
                    pending = None
                    continue
                attrs = dict(attr_re.findall(m.group('attrs') or ''))
                title = m.group('title').strip()
                pending = {
                    'tvg_name': attrs.get('tvg-name', ''),
                    'group_title': attrs.get('group-title', ''),
                }
            elif pending is not None and not line.startswith('#'):
                pending['url'] = line.strip()
                yield pending
                pending = None

def classify_kind(group_title, tvg_name, url):
    s = ' '.join([group_title or '', tvg_name or '', url or '']).lower()
    if re.search(r'\b(series|serien|serie)\b', s):
        return 'series'
    if re.search(r'\b(vod|movie|kino|film|cinema|movies)\b', s):
        return 'vod'
    return 'live'

def normalize_bucket_vod(group_title, tvg_name, url):
    gt = (group_title or '').lower()
    name = (tvg_name or '').lower()
    u = (url or '').lower()
    def has(tok):
        return tok in gt or tok in name or tok in u
    def has_word(pattern):
        return re.search(pattern, gt) or re.search(pattern, name) or re.search(pattern, u)
    # Adults
    if re.search(r'\bfor adults\b', group_title or '', flags=re.IGNORECASE) or re.search(r'\bfor adults\b', tvg_name or '', flags=re.IGNORECASE):
        src = (group_title or tvg_name or '')
        m = re.search(r'for adults\s*(?:[➾:>\-]+\s*)?(.*)', src, flags=re.IGNORECASE)
        tail = (m.group(1) if m else '').strip()
        sub = tail if tail else 'other'
        slug = re.sub(r"[^\w]+", "_", sub.lower()).strip('_') or 'other'
        return 'adult_' + slug
    if 'netflix' in gt or 'netflix' in name: return 'netflix'
    if 'amazon' in gt or 'prime' in gt or 'amazon' in name or 'prime' in name: return 'amazon_prime'
    if 'disney+' in gt or 'disney plus' in gt or ('disney ' in gt and 'disney channel' not in gt) or 'disney+' in name or 'disney plus' in name: return 'disney_plus'
    if 'apple tv+' in gt or 'apple tv plus' in gt or 'apple tv' in gt or re.search(r'\bapple\b', name): return 'apple_tv_plus'
    if 'sky ' in gt or 'warner' in gt or 'hbo' in gt or re.search(r'\bmax\b', gt) or 'paramount' in gt: return 'sky_warner'
    if 'anime' in gt or 'anime' in name: return 'anime'
    if 'neu aktuell' in gt or 'neu ' in gt or 'new ' in gt: return 'new'
    if 'kids' in gt or 'kinder' in gt or 'cartoon' in gt or 'nick' in gt or 'kika' in gt or 'disney channel' in gt: return 'kids'
    if 'de ' in gt or 'deutschland' in gt or 'german' in gt: return 'german'
    return 'other'

def display_label(key):
    if key.startswith('adult_'):
        return 'For Adults – ' + (key[len('adult_'):].replace('_', ' ').title() or 'Other')
    return {
        'apple_tv_plus': 'Apple TV+',
        'netflix': 'Netflix',
        'disney_plus': 'Disney+',
        'amazon_prime': 'Amazon Prime',
        'sky_warner': 'Sky/Warner',
        'anime': 'Anime',
        'new': 'Neu',
        'kids': 'Kids',
        'german': 'Deutsch',
        'other': 'Other',
    }.get(key, key.replace('_', ' ').title())

def main():
    if len(sys.argv) < 2:
        print('Usage: quick_m3u_vod_providers.py <path-to-m3u>', file=sys.stderr)
        sys.exit(2)
    path = sys.argv[1]
    counts = Counter()
    for it in parse_m3u(path):
        kind = classify_kind(it.get('group_title'), it.get('tvg_name'), it.get('url'))
        if kind != 'vod':
            continue
        b = normalize_bucket_vod(it.get('group_title'), it.get('tvg_name'), it.get('url'))
        counts[b] += 1
    # Print alphabetically by display label
    items = sorted(((display_label(k), k, v) for k, v in counts.items()), key=lambda t: t[0].lower())
    for label, key, cnt in items:
        print(f"{label} ({key}) = {cnt}")

if __name__ == '__main__':
    main()

