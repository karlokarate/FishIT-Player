#!/usr/bin/env python3
import re
import sys
from collections import defaultdict, Counter, OrderedDict

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
                    'tvg_id': attrs.get('tvg-id', ''),
                    'tvg_name': attrs.get('tvg-name', ''),
                    'tvg_logo': attrs.get('tvg-logo', ''),
                    'group_title': attrs.get('group-title', ''),
                    'title': title,
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

def normalize_bucket(kind, group_title, tvg_name, url):
    gt = (group_title or '').lower()
    name = (tvg_name or '').lower()
    u = (url or '').lower()
    def has(tok):
        return tok in gt or tok in name or tok in u
    def has_any(tokens):
        return any(has(t) for t in tokens)
    def has_word(pattern):
        return re.search(pattern, gt) or re.search(pattern, name) or re.search(pattern, u)
    if kind == 'vod':
        # Preserve Adult subcategories
        if re.search(r'\bfor adults\b', group_title or '', flags=re.IGNORECASE) or re.search(r'\bfor adults\b', tvg_name or '', flags=re.IGNORECASE):
            src = (group_title or tvg_name or '')
            m = re.search(r'for adults\s*(?:[➾:>\-]+\s*)?(.*)', src, flags=re.IGNORECASE)
            tail = (m.group(1) if m else '').strip()
            sub = tail if tail else 'other'
            slug = re.sub(r"[^\w]+", "_", sub.lower()).strip('_') or 'other'
            return 'adult_' + slug
        if has('netflix'): return 'netflix'
        if has('amazon') or has('prime'): return 'amazon_prime'
        if has('disney+') or has('disney plus') or (has('disney ') and not has('disney channel')): return 'disney_plus'
        if has('apple tv+') or has('apple tv plus') or has('apple tv') or has_word(r'\bapple\b'): return 'apple_tv_plus'
        if has('sky ') or has('warner') or has('hbo') or has_word(r'\bmax\b') or has('paramount'): return 'sky_warner'
        if has('anime'): return 'anime'
        if has('neu aktuell') or has('neu ') or has('new '): return 'new'
        if has('kids') or has('kinder') or has('cartoon') or has('nick') or has('kika') or has('disney channel'): return 'kids'
        if has('de ') or has('deutschland') or has('german'): return 'german'
        return 'other'
    elif kind == 'series':
        if has('netflix'): return 'netflix_series'
        if (has('amazon') or has('prime')) and (has('apple tv') or has_word(r'\bapple\b')): return 'amazon_apple_series'
        if has('apple tv') or has_word(r'\bapple\b'): return 'amazon_apple_series'
        if has('disney+') or has('disney plus') or (has('disney ') and not has('disney channel')): return 'disney_plus_series'
        if has('sky ') or has('warner') or has('hbo') or has_word(r'\bmax\b') or has('paramount'): return 'sky_warner_series'
        if has('anime'): return 'anime_series'
        if has('kids') or has('kinder') or has('cartoon') or has('nick') or has('kika') or has('disney channel'): return 'kids_series'
        if has('de ') or has('deutschland') or has('german'): return 'german_series'
        return 'other'
    else:
        # live reduced
        if has('screensaver'): return 'screensaver'
        if has_any(['sport', 'dazn', 'sky sport', 'eurosport']): return 'sports'
        if has_any(['news', 'nachricht', 'n-tv', 'welt', 'cnn', 'bbc news', 'al jazeera']): return 'news'
        if has_any(['doku', 'docu', 'documentary', 'history', 'nat geo', 'discovery']): return 'documentary'
        if has_any(['kids', 'kinder', 'cartoon', 'nick', 'kika', 'disney channel']): return 'kids'
        if has_any(['musik', 'music', 'mtv', 'vh1']): return 'music'
        if has_any(['sky cinema', 'cinema']): return 'movies'
        if has_any(['thailand', 'arabic', 'turkish', 'fr ', ' france', 'italy', 'spanish', 'english', 'usa', 'uk ']): return 'international'
        return 'entertainment'

def display_label(key):
    if key.startswith('adult_'):
        pretty = key[len('adult_'):].replace('_', ' ').title() or 'Other'
        return f'For Adults – {pretty}'
    return {
        'apple_tv_plus': 'Apple TV+',
        'netflix': 'Netflix',
        'disney_plus': 'Disney+',
        'amazon_prime': 'Amazon Prime',
        'sky_warner': 'Sky/Warner',
        'paramount_plus': 'Paramount+',
        'max': 'Max',
        'sky_wow': 'Sky / WOW',
        'discovery_plus': 'discovery+',
        'mubi': 'MUBI',
        'new': 'Neu',
        'german': 'Deutsch',
        'kids': 'Kids',
        'sports': 'Sport',
        'news': 'News',
        'music': 'Musik',
        'documentary': 'Dokumentationen',
        'international': 'International',
        'screensaver': 'Screensaver',
        'movies': 'Movies',
        'entertainment': 'Entertainment',
        'other': 'Other',
        'netflix_series': 'Netflix',
        'amazon_apple_series': 'Amazon & Apple',
        'disney_plus_series': 'Disney+',
        'sky_warner_series': 'Sky/Warner',
        'anime_series': 'Anime',
        'kids_series': 'Kids',
        'german_series': 'Deutsch',
        'unknown': 'Unbekannt',
    }.get(key, key.replace('_', ' ').title())

def main():
    if len(sys.argv) < 2:
        print('Usage: quick_m3u_vod_category_mapping.py <path-to-m3u>', file=sys.stderr)
        sys.exit(2)
    path = sys.argv[1]

    per_cat_total = Counter()
    per_cat_bucket = defaultdict(Counter)

    for it in parse_m3u(path):
        kind = classify_kind(it.get('group_title'), it.get('tvg_name'), it.get('url'))
        if kind != 'vod':
            continue
        cat = it.get('group_title') or ''
        per_cat_total[cat] += 1
        b = normalize_bucket('vod', it.get('group_title'), it.get('tvg_name'), it.get('url'))
        per_cat_bucket[cat][b] += 1

    # Sort categories by total desc
    for cat, total in sorted(per_cat_total.items(), key=lambda kv: (-kv[1], kv[0])):
        # sort buckets per category
        bucket_counts = per_cat_bucket[cat]
        parts = []
        for bkey, cnt in sorted(bucket_counts.items(), key=lambda kv: (-kv[1], kv[0])):
            parts.append(f"{display_label(bkey)}={cnt}")
        # print one line per category
        print(f"{cat}  =>  {', '.join(parts)}  (total={total})")

if __name__ == '__main__':
    main()
