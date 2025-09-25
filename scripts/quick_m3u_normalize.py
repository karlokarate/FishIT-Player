#!/usr/bin/env python3
import re
import sys
from collections import defaultdict, Counter

extinf_re = re.compile(r'^#EXTINF:-1\s+(?P<attrs>[^,]*),(?P<title>.*)$')
attr_re = re.compile(r'(\w[\w-]*)="([^"]*)"')

def parse_m3u(path):
    """Yield dicts with fields: tvg_id, tvg_name, tvg_logo, group_title, title, url."""
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

def contains_any(s, toks):
    return any(t in s for t in toks)

# Port of CategoryNormalizer from app (keep in sync)
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

    # Adults special handling for VOD: preserve subcategories
    if kind.lower() == 'vod':
        if re.search(r'\bfor adults\b', (group_title or ''), flags=re.IGNORECASE) or re.search(r'\bfor adults\b', (tvg_name or ''), flags=re.IGNORECASE):
            src = (group_title or tvg_name or '')
            m = re.search(r'for adults\s*(?:[➾:>\-]+\s*)?(.*)', src, flags=re.IGNORECASE)
            tail = (m.group(1) if m else '').strip()
            sub = tail if tail else 'other'
            slug = re.sub(r"[^\w]+", "_", sub.lower()).strip('_') or 'other'
            return 'adult_' + slug

    isScreensaver = has('screensaver')
    isSport = has_any(['sport', 'dazn', 'sky sport', 'eurosport'])
    isNews = has_any(['news', 'nachricht', 'n-tv', 'welt', 'cnn', 'bbc news', 'al jazeera'])
    isDoc = has_any(['doku', 'docu', 'documentary', 'history', 'nat geo', 'discovery'])
    isKids = has_any(['kids', 'kinder', 'cartoon', 'nick', 'kika', 'disney channel'])
    isMusic = has_any(['musik', 'music', 'mtv', 'vh1'])
    isMoviesLive = has_any(['sky cinema', 'cinema'])
    isInternational = has_any(['thailand', 'arabic', 'turkish', 'fr ', ' france', 'italy', 'spanish', 'english', 'usa', 'uk '])

    isNetflix = has('netflix')
    isAmazon = has('amazon') or has('prime')
    isDisney = has('disney+') or has('disney plus') or (has('disney ') and not has('disney channel'))
    isApple = has('apple tv+') or has('apple tv plus') or has('apple tv') or has_word(r'\bapple\b')
    isSkyWarner = has('sky ') or has('warner') or has('hbo') or has_word(r'\bmax\b') or has('paramount')
    isAnime = has('anime')
    isNew = has('neu aktuell') or has('neu ') or has('new ')
    isGermanGroup = has('de ') or has('deutschland') or has('german')

    k = kind.lower()
    if k == 'live':
        if isScreensaver: return 'screensaver'
        if isSport: return 'sports'
        if isNews: return 'news'
        if isDoc: return 'documentary'
        if isKids: return 'kids'
        if isMusic: return 'music'
        if isMoviesLive: return 'movies'
        if isInternational: return 'international'
        return 'entertainment'
    elif k == 'vod':
        if isNetflix: return 'netflix'
        if isAmazon: return 'amazon_prime'
        if isDisney: return 'disney_plus'
        if isApple: return 'apple_tv_plus'
        if isSkyWarner: return 'sky_warner'
        if isAnime: return 'anime'
        if isNew: return 'new'
        if isKids: return 'kids'
        if isGermanGroup: return 'german'
        return 'other'
    elif k == 'series':
        if isNetflix: return 'netflix_series'
        if (isAmazon and isApple) or has('amazon & apple'): return 'amazon_apple_series'
        if isApple: return 'amazon_apple_series'
        if isDisney: return 'disney_plus_series'
        if isSkyWarner: return 'sky_warner_series'
        if isAnime: return 'anime_series'
        if isKids: return 'kids_series'
        if isGermanGroup: return 'german_series'
        return 'other'
    else:
        return 'other'

def display_label(key):
    # Adults dynamic labels
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
        'netflix_series': 'Netflix',
        'amazon_apple_series': 'Amazon & Apple',
        'disney_plus_series': 'Disney+',
        'sky_warner_series': 'Sky/Warner',
        'anime_series': 'Anime',
        'kids_series': 'Kids',
        'german_series': 'Deutsch',
        'unknown': 'Unbekannt',
        'movies': 'Movies',
        'entertainment': 'Entertainment',
        'other': 'Other',
    }.get(key, key.replace('_', ' ').title())

def classify_kind(group_title, tvg_name, url):
    s = ' '.join([group_title or '', tvg_name or '', url or '']).lower()
    if re.search(r'\b(series|serien|serie)\b', s):
        return 'series'
    if re.search(r'\b(vod|movie|kino|film|cinema|movies)\b', s):
        return 'vod'
    return 'live'

def main():
    if len(sys.argv) < 2:
        print('Usage: quick_m3u_normalize.py <path-to-m3u>', file=sys.stderr)
        sys.exit(2)
    path = sys.argv[1]
    totals = Counter()
    buckets = { 'live': Counter(), 'vod': Counter(), 'series': Counter() }

    for it in parse_m3u(path):
        kind = classify_kind(it.get('group_title'), it.get('tvg_name'), it.get('url'))
        totals[kind] += 1
        b = normalize_bucket(kind, it.get('group_title'), it.get('tvg_name'), it.get('url'))
        buckets[kind][b] += 1

    # Print in requested format
    def print_section(kind, title):
        total = totals.get(kind, 0)
        print(f"{title}: Sender in der m3u = {total}")
        # order buckets by count desc, then name
        for key, cnt in sorted(buckets[kind].items(), key=lambda kv: (-kv[1], kv[0])):
            print(f"  - {display_label(key)}: {cnt}")
        # summary
        unassigned = buckets[kind].get('other', 0) + buckets[kind].get('entertainment', 0) + buckets[kind].get('unknown', 0)
        print(f"  summary: zugewiesen={total - unassigned}, unklassifiziert={unassigned}")

    print_section('live',   'Live')
    print_section('vod',    'VOD')
    print_section('series', 'Serien')

if __name__ == '__main__':
    main()
